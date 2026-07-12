package com.openelements.content;

import com.openelements.spring.base.services.search.MeilisearchProperties;
import com.openelements.spring.base.services.search.SearchIndexBootstrapStep;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Populates the content index at application startup by implementing the library's
 * {@link SearchIndexBootstrapStep}.
 *
 * <p>The step is intentionally thin: it declares the target index and provides a lazy document
 * stream over all enabled sources (via {@link ContentIndexer#streamAllDocuments(ContentSource)}). The
 * library's {@code MeilisearchBootstrapRunner} discovers the step, consumes the stream in batches,
 * and toggles {@code SearchReadinessState} around the run. Reusing {@link ContentIndexer} keeps
 * bootstrap and the refresh scheduler (spec 010) from diverging.
 *
 * <p>Only created when the Meilisearch stack is enabled.
 */
@Component
@ConditionalOnProperty(prefix = "openelements.meilisearch", name = "enabled", havingValue = "true")
public class ContentBootstrapStep implements SearchIndexBootstrapStep {

    private static final Logger log = LoggerFactory.getLogger(ContentBootstrapStep.class);

    private final ContentSourceProperties properties;
    private final ContentIndexer indexer;
    private final MeilisearchProperties meilisearchProperties;

    public ContentBootstrapStep(ContentSourceProperties properties, ContentIndexer indexer,
                                MeilisearchProperties meilisearchProperties) {
        this.properties = properties;
        this.indexer = indexer;
        this.meilisearchProperties = meilisearchProperties;
    }

    @Override
    public String indexUid() {
        return meilisearchProperties.resolveIndex("content");
    }

    @Override
    public Stream<Map<String, Object>> documents() {
        return properties.sources().stream()
            .filter(ContentSource::enabled)
            .flatMap(this::streamSourceSafely);
    }

    /**
     * Streams one source's documents, isolating a source-level failure so the remaining sources still
     * contribute (per-item fetch/extract errors are already contained as {@code SKIP} by the strategy).
     */
    private Stream<Map<String, Object>> streamSourceSafely(ContentSource source) {
        try {
            return indexer.streamAllDocuments(source);
        } catch (Exception e) {
            log.warn("Skipping source {} during bootstrap: {}", source.id(), e.toString());
            return Stream.empty();
        }
    }
}
