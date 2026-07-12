package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ContentIndexer} using an in-memory {@link ContentIndexStore} and a
 * test-double {@link ContentSourceStrategy}, so the diff/upsert/delete orchestration is verified
 * without a running Meilisearch or any mocks.
 */
@DisplayName("Content indexer")
class ContentIndexerTest {

    private static final ContentSource SOURCE = new ContentSource(
        "oe", SourceType.WEBSITE, "https://ex.com", List.of(), List.of("/**"), List.of(), "article", List.of(), true);

    private final InMemoryContentIndexStore store = new InMemoryContentIndexStore();
    private final StubStrategy strategy = new StubStrategy();
    private final ContentIndexer indexer =
        new ContentIndexer(new SourceStrategyRegistry(List.of(strategy)), store);

    private static DiscoveredItem item(String path, String lastmod) {
        return new DiscoveredItem("https://ex.com" + path, lastmod);
    }

    private static ContentDocument document(String path, String lastmod) {
        String url = "https://ex.com" + path;
        return new ContentDocument(
            ContentDocument.id("oe", url), "oe", "en", url, "T", "E", "body text", "a",
            List.of("x"), "2026-01-01", lastmod, null);
    }

    @Test
    @DisplayName("new pages are fetched and upserted")
    void newPagesAreUpserted() {
        strategy.discovered = List.of(item("/a", "L"), item("/b", "L"), item("/c", "L"));
        strategy.fetchFn = it -> FetchOutcome.index(document(pathOf(it), "L"));

        IndexReport report = indexer.indexSource(SOURCE);

        assertThat(report.discovered()).isEqualTo(3);
        assertThat(report.upserted()).isEqualTo(3);
        assertThat(store.loadState("oe")).hasSize(3);
    }

    @Test
    @DisplayName("unchanged pages are skipped without fetching")
    void unchangedPagesAreSkipped() {
        store.upsert(List.of(document("/a", "L").toMap(), document("/b", "L").toMap(), document("/c", "L").toMap()));
        strategy.discovered = List.of(item("/a", "L"), item("/b", "L"), item("/c", "L"));
        strategy.fetchFn = it -> {
            throw new AssertionError("fetch must not be called for unchanged items");
        };

        IndexReport report = indexer.indexSource(SOURCE);

        assertThat(report.unchanged()).isEqualTo(3);
        assertThat(report.upserted()).isZero();
        assertThat(strategy.fetched).isEmpty();
    }

    @Test
    @DisplayName("a page whose lastmod changed is re-fetched and upserted")
    void changedPageIsReFetched() {
        store.upsert(List.of(document("/a", "old").toMap()));
        strategy.discovered = List.of(item("/a", "new"));
        strategy.fetchFn = it -> FetchOutcome.index(document("/a", "new"));

        IndexReport report = indexer.indexSource(SOURCE);

        assertThat(report.upserted()).isEqualTo(1);
        assertThat(strategy.fetched).containsExactly("https://ex.com/a");
        assertThat(store.loadState("oe").get(ContentDocument.id("oe", "https://ex.com/a")).lastmod())
            .isEqualTo("new");
    }

    @Test
    @DisplayName("a 304 (UNCHANGED) after a lastmod change causes no upsert")
    void conditionalGetShortCircuits() {
        store.upsert(List.of(document("/a", "old").toMap()));
        strategy.discovered = List.of(item("/a", "new"));
        strategy.fetchFn = it -> FetchOutcome.unchanged();

        IndexReport report = indexer.indexSource(SOURCE);

        assertThat(report.unchanged()).isEqualTo(1);
        assertThat(report.upserted()).isZero();
        assertThat(strategy.fetched).containsExactly("https://ex.com/a"); // fetch was attempted
    }

    @Test
    @DisplayName("a document no longer present in discovery is deleted")
    void vanishedUrlIsDeleted() {
        store.upsert(List.of(document("/a", "L").toMap(), document("/b", "L").toMap()));
        strategy.discovered = List.of(item("/a", "L"));

        IndexReport report = indexer.indexSource(SOURCE);

        assertThat(report.unchanged()).isEqualTo(1);
        assertThat(report.deleted()).isEqualTo(1);
        assertThat(store.loadState("oe")).containsOnlyKeys(ContentDocument.id("oe", "https://ex.com/a"));
    }

    @Test
    @DisplayName("a 404 (DELETE) removes the document by stable id")
    void notFoundDeletesDocument() {
        store.upsert(List.of(document("/a", "old").toMap()));
        strategy.discovered = List.of(item("/a", "new"));
        strategy.fetchFn = it -> FetchOutcome.delete();

        IndexReport report = indexer.indexSource(SOURCE);

        assertThat(report.deleted()).isEqualTo(1);
        assertThat(store.loadState("oe")).isEmpty();
    }

    @Test
    @DisplayName("re-running with no changes is idempotent")
    void reRunIsIdempotent() {
        strategy.discovered = List.of(item("/a", "L"), item("/b", "L"));
        strategy.fetchFn = it -> FetchOutcome.index(document(pathOf(it), "L"));
        indexer.indexSource(SOURCE);
        strategy.fetched.clear();

        IndexReport second = indexer.indexSource(SOURCE);

        assertThat(second.unchanged()).isEqualTo(2);
        assertThat(second.upserted()).isZero();
        assertThat(second.deleted()).isZero();
        assertThat(store.loadState("oe")).hasSize(2);
    }

    @Test
    @DisplayName("one failing page does not abort the batch")
    void oneFailingPageIsSkipped() {
        strategy.discovered = List.of(
            item("/a", "L"), item("/b", "L"), item("/c", "L"), item("/d", "L"), item("/e", "L"));
        strategy.fetchFn = it -> pathOf(it).equals("/e")
            ? FetchOutcome.skip()
            : FetchOutcome.index(document(pathOf(it), "L"));

        IndexReport report = indexer.indexSource(SOURCE);

        assertThat(report.upserted()).isEqualTo(4);
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(store.loadState("oe")).hasSize(4);
    }

    @Test
    @DisplayName("the stable id prevents duplicates across runs")
    void stableIdPreventsDuplicates() {
        strategy.discovered = List.of(item("/a", "L1"));
        strategy.fetchFn = it -> FetchOutcome.index(document("/a", "L1"));
        indexer.indexSource(SOURCE);

        strategy.discovered = List.of(item("/a", "L2"));
        strategy.fetchFn = it -> FetchOutcome.index(document("/a", "L2"));
        indexer.indexSource(SOURCE);

        assertThat(store.loadState("oe")).hasSize(1);
    }

    @Test
    @DisplayName("streamAllDocuments lazily yields every in-scope document")
    void streamAllDocumentsYieldsAll() {
        strategy.discovered = List.of(item("/a", "L"), item("/b", "L"), item("/c", "L"));
        strategy.fetchFn = it -> FetchOutcome.index(document(pathOf(it), "L"));

        List<Map<String, Object>> documents = indexer.streamAllDocuments(SOURCE).toList();

        assertThat(documents).hasSize(3);
        assertThat(documents).allSatisfy(doc -> assertThat(doc).containsKey("id"));
    }

    private static String pathOf(DiscoveredItem item) {
        return item.url().substring("https://ex.com".length());
    }

    /** In-memory {@link ContentIndexStore}: documents keyed by id. */
    private static final class InMemoryContentIndexStore implements ContentIndexStore {
        private final Map<String, Map<String, Object>> documents = new LinkedHashMap<>();

        @Override
        public Map<String, StoredDocument> loadState(String source) {
            Map<String, StoredDocument> state = new LinkedHashMap<>();
            documents.forEach((id, doc) -> {
                if (source.equals(doc.get("source"))) {
                    state.put(id, new StoredDocument((String) doc.get("url"), (String) doc.get("lastmod")));
                }
            });
            return state;
        }

        @Override
        public int upsert(List<Map<String, Object>> docs) {
            docs.forEach(doc -> documents.put((String) doc.get("id"), doc));
            return docs.size();
        }

        @Override
        public void delete(String id) {
            documents.remove(id);
        }
    }

    /** Test-double strategy with configurable discovery and fetch behavior, recording fetches. */
    private static final class StubStrategy implements ContentSourceStrategy {
        private List<DiscoveredItem> discovered = List.of();
        private Function<DiscoveredItem, FetchOutcome> fetchFn = it -> FetchOutcome.skip();
        private final List<String> fetched = new ArrayList<>();

        @Override
        public SourceType type() {
            return SourceType.WEBSITE;
        }

        @Override
        public List<DiscoveredItem> discover(ContentSource source) {
            return discovered;
        }

        @Override
        public FetchOutcome fetch(ContentSource source, DiscoveredItem item) {
            fetched.add(item.url());
            return fetchFn.apply(item);
        }
    }
}
