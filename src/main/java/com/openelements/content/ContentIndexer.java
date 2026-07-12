package com.openelements.content;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Orchestrates the ingestion pipeline for one source: Discover → Diff → Fetch → Extract → Index.
 *
 * <p>Discovery yields URLs with change markers; the indexer diffs these against the state stored in
 * the index (via {@link ContentIndexStore}) and only fetches new or changed items. Fetched documents
 * are upserted; items reported gone (404) or no longer present in discovery are deleted. Upserts are
 * keyed by the stable document id, so re-running is idempotent, and a single failing page is skipped
 * without aborting the pass.
 *
 * <p>This is the reusable engine invoked by the bootstrap step (spec 009) and the refresh scheduler
 * (spec 010). It is only created when the Meilisearch stack is enabled.
 */
@Component
@ConditionalOnProperty(prefix = "openelements.meilisearch", name = "enabled", havingValue = "true")
public class ContentIndexer {

    private static final Logger log = LoggerFactory.getLogger(ContentIndexer.class);

    private final SourceStrategyRegistry strategyRegistry;
    private final ContentIndexStore indexStore;

    public ContentIndexer(SourceStrategyRegistry strategyRegistry, ContentIndexStore indexStore) {
        this.strategyRegistry = strategyRegistry;
        this.indexStore = indexStore;
    }

    /**
     * Runs a full incremental pass over one source.
     *
     * @param source the source to index
     * @return a report of what happened
     */
    public IndexReport indexSource(ContentSource source) {
        ContentSourceStrategy strategy = strategyRegistry.forSource(source);
        List<DiscoveredItem> discovered = strategy.discover(source);
        Map<String, ContentIndexStore.StoredDocument> stored = indexStore.loadState(source.id());

        List<Map<String, Object>> toUpsert = new ArrayList<>();
        Set<String> discoveredIds = new HashSet<>();
        int upserted = 0;
        int unchanged = 0;
        int deleted = 0;
        int skipped = 0;

        for (DiscoveredItem item : discovered) {
            String id = ContentDocument.id(source.id(), item.url());
            discoveredIds.add(id);
            ContentIndexStore.StoredDocument existing = stored.get(id);

            if (existing != null && isUnchanged(existing.lastmod(), item.lastmod())) {
                unchanged++;
                continue;
            }

            FetchOutcome outcome = strategy.fetch(source, item);
            switch (outcome.result()) {
                case INDEX -> {
                    toUpsert.add(outcome.document().toMap());
                    upserted++;
                }
                case UNCHANGED -> unchanged++;
                case DELETE -> {
                    if (existing != null) {
                        indexStore.delete(id);
                        deleted++;
                    }
                }
                case SKIP -> skipped++;
            }
        }

        if (!toUpsert.isEmpty()) {
            indexStore.upsert(toUpsert);
        }

        // Documents still in the index but no longer discovered have been removed at the source.
        for (String storedId : stored.keySet()) {
            if (!discoveredIds.contains(storedId)) {
                indexStore.delete(storedId);
                deleted++;
            }
        }

        IndexReport report = new IndexReport(discovered.size(), upserted, unchanged, deleted, skipped);
        log.info("Indexed source {}: {}", source.id(), report);
        return report;
    }

    /**
     * Lazily fetches and yields every in-scope document of a source, without diffing against the
     * index — used for a full (re)build by the bootstrap step (spec 009).
     *
     * @param source the source
     * @return a lazy stream of document attribute maps ready for the index
     */
    public Stream<Map<String, Object>> streamAllDocuments(ContentSource source) {
        ContentSourceStrategy strategy = strategyRegistry.forSource(source);
        return strategy.discover(source).stream()
            .map(item -> strategy.fetch(source, item))
            .filter(outcome -> outcome.result() == FetchOutcome.Result.INDEX)
            .map(outcome -> outcome.document().toMap());
    }

    /** A discovered item is unchanged only when it carries a change marker equal to the stored one. */
    private static boolean isUnchanged(String storedLastmod, String discoveredLastmod) {
        return discoveredLastmod != null && discoveredLastmod.equals(storedLastmod);
    }
}
