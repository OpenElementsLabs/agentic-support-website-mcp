package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.search.MeilisearchProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Unit tests for {@link ContentBootstrapStep} using a real {@link ContentIndexer} driven by a
 * test-double strategy, so document streaming is exercised without a running Meilisearch.
 *
 * <p>Readiness toggling and batching are the library {@code MeilisearchBootstrapRunner}'s
 * responsibility and require a live instance; they are not reproduced here.
 */
@DisplayName("Content bootstrap step")
class ContentBootstrapStepTest {

    private final MeilisearchProperties meilisearch =
        new MeilisearchProperties("http://localhost:7700", "", "content_", Duration.ofSeconds(10));
    private final StubStrategy strategy = new StubStrategy();
    private final ContentIndexer indexer =
        new ContentIndexer(new SourceStrategyRegistry(List.of(strategy)), new NoOpStore());

    private static ContentSource source(String id, boolean enabled) {
        return new ContentSource(
            id, SourceType.WEBSITE, "https://ex.com", List.of(), List.of("/**"), List.of(), "article", List.of(), enabled);
    }

    private static ContentSourceProperties properties(ContentSource... sources) {
        return new ContentSourceProperties(
            true, null, "UA", 2.0, Duration.ofSeconds(10), DataSize.ofMegabytes(5), List.of(sources));
    }

    private ContentBootstrapStep step(ContentSourceProperties properties) {
        return new ContentBootstrapStep(properties, indexer, meilisearch);
    }

    @Test
    @DisplayName("indexUid targets the prefixed content index")
    void indexUidTargetsContentIndex() {
        assertThat(step(properties()).indexUid()).isEqualTo("content_content");
    }

    @Test
    @DisplayName("only enabled sources contribute documents")
    void onlyEnabledSourcesContribute() {
        ContentBootstrapStep step = step(properties(source("a", true), source("b", true), source("c", false)));

        List<String> sources = step.documents().map(doc -> (String) doc.get("source")).toList();

        assertThat(sources).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("the document stream is lazy — nothing is fetched until it is consumed")
    void streamIsLazy() {
        strategy.itemsPerSource = 3;
        ContentBootstrapStep step = step(properties(source("a", true)));

        Stream<Map<String, Object>> documents = step.documents();
        assertThat(strategy.fetchCount.get()).isZero(); // building the stream fetches nothing

        List<Map<String, Object>> materialized = documents.toList();
        assertThat(materialized).hasSize(3);
        assertThat(strategy.fetchCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("a source that fails while streaming does not block the others")
    void failingSourceDoesNotBlockOthers() {
        strategy.throwingSourceIds.add("bad");
        ContentBootstrapStep step = step(properties(source("a", true), source("bad", true)));

        List<String> sources = step.documents().map(doc -> (String) doc.get("source")).toList();

        assertThat(sources).containsExactly("a");
    }

    /** Test-double strategy: one INDEX document per discovered item; can throw for chosen sources. */
    private static final class StubStrategy implements ContentSourceStrategy {
        private final List<String> throwingSourceIds = new ArrayList<>();
        private final AtomicInteger fetchCount = new AtomicInteger();
        private int itemsPerSource = 1;

        @Override
        public SourceType type() {
            return SourceType.WEBSITE;
        }

        @Override
        public List<DiscoveredItem> discover(ContentSource source) {
            if (throwingSourceIds.contains(source.id())) {
                throw new IllegalStateException("boom " + source.id());
            }
            return IntStream.range(0, itemsPerSource)
                .mapToObj(i -> new DiscoveredItem("https://ex.com/" + source.id() + "/" + i, "L"))
                .toList();
        }

        @Override
        public FetchOutcome fetch(ContentSource source, DiscoveredItem item) {
            fetchCount.incrementAndGet();
            ContentDocument document = new ContentDocument(
                ContentDocument.id(source.id(), item.url()), source.id(), "en", item.url(),
                "T", "E", "body", "a", List.of(), "2026-01-01", item.lastmod(), null);
            return FetchOutcome.index(document);
        }
    }

    /** streamAllDocuments never touches the store, so a no-op suffices. */
    private static final class NoOpStore implements ContentIndexStore {
        @Override
        public Map<String, StoredDocument> loadState(String source) {
            return Map.of();
        }

        @Override
        public int upsert(List<Map<String, Object>> documents) {
            return documents.size();
        }

        @Override
        public void delete(String id) {
        }
    }
}
