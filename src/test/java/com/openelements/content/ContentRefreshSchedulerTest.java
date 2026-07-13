package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.search.SearchReadinessState;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.unit.DataSize;

/**
 * Unit tests for {@link ContentRefreshScheduler} using a real {@link ContentIndexer} driven by a
 * test-double strategy and an in-memory store, plus a real {@link SearchReadinessState}. No mocks or
 * live Meilisearch; non-overlap is verified deterministically via re-entrancy rather than threads.
 */
@DisplayName("Content refresh scheduler")
class ContentRefreshSchedulerTest {

    private final StubStrategy strategy = new StubStrategy();
    private final InMemoryContentIndexStore store = new InMemoryContentIndexStore();
    private final ContentIndexer indexer =
        new ContentIndexer(new SourceStrategyRegistry(List.of(strategy)), store);
    private final SearchReadinessState readiness = new SearchReadinessState();

    @BeforeEach
    void markBootstrapComplete() {
        // A fresh SearchReadinessState reports isBootstrapping()==true until bootstrap finishes;
        // simulate a completed startup bootstrap so scheduled refreshes are allowed to run.
        readiness.markBootstrappingFinished();
    }

    private static ContentSource source(String id, boolean enabled) {
        return new ContentSource(
            id, SourceType.WEBSITE, "https://ex.com", List.of(), List.of("/**"), List.of(), "article", List.of(), enabled, null);
    }

    private static ContentSourceProperties properties(boolean enabled, ContentSource... sources) {
        return new ContentSourceProperties(
            enabled, null, "UA", 2.0, Duration.ofSeconds(10), DataSize.ofMegabytes(5), List.of(sources));
    }

    private ContentRefreshScheduler scheduler(ContentSourceProperties properties) {
        return new ContentRefreshScheduler(properties, indexer, readiness);
    }

    private static ContentDocument document(String source, String url, String lastmod) {
        return new ContentDocument(
            ContentDocument.id(source, url), source, "en", url, "T", "E", "body", "a",
            List.of(), "2026-01-01", lastmod, null);
    }

    @Test
    @DisplayName("each enabled source is indexed on a tick")
    void runsEachEnabledSource() {
        scheduler(properties(true, source("a", true), source("b", true))).refresh();

        assertThat(strategy.discoveredSources).containsExactly("a", "b");
    }

    @Test
    @DisplayName("a disabled source is skipped")
    void disabledSourceIsSkipped() {
        scheduler(properties(true, source("a", true), source("b", false))).refresh();

        assertThat(strategy.discoveredSources).containsExactly("a");
    }

    @Test
    @DisplayName("refresh is skipped while bootstrapping")
    void skipsWhileBootstrapping() {
        readiness.markBootstrappingStarted();

        scheduler(properties(true, source("a", true))).refresh();

        assertThat(strategy.discoveredSources).isEmpty();
    }

    @Test
    @DisplayName("refresh does nothing when the pipeline is globally disabled")
    void skipsWhenGloballyDisabled() {
        scheduler(properties(false, source("a", true))).refresh();

        assertThat(strategy.discoveredSources).isEmpty();
    }

    @Test
    @DisplayName("a re-entrant tick does not overlap a running refresh")
    void nonOverlappingRuns() {
        ContentRefreshScheduler scheduler = scheduler(properties(true, source("a", true), source("b", true)));
        strategy.reentrantOnce = scheduler::refresh; // fired during the first discover of the outer run

        scheduler.refresh();

        // The re-entrant call is skipped, so each source is discovered exactly once.
        assertThat(strategy.discoveredSources).containsExactly("a", "b");
    }

    @Test
    @DisplayName("one source failing does not stop the others")
    void oneSourceFailingDoesNotStopOthers() {
        strategy.throwOnDiscover.add("a");

        scheduler(properties(true, source("a", true), source("b", true))).refresh();

        assertThat(strategy.discoveredSources).containsExactly("a", "b");
    }

    @Test
    @DisplayName("the cron expression is externalized with an hourly default")
    void cronIsExternallyConfigurable() throws Exception {
        Method refresh = ContentRefreshScheduler.class.getMethod("refresh");
        Scheduled scheduled = refresh.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${open-elements.content.refresh-cron:0 0 * * * *}");
    }

    @Test
    @DisplayName("a new page is added to the index on refresh")
    void newPageIsAdded() {
        strategy.itemsBySource.put("a", List.of(new DiscoveredItem("https://ex.com/a", "L")));

        scheduler(properties(true, source("a", true))).refresh();

        assertThat(store.loadState("a")).containsKey(ContentDocument.id("a", "https://ex.com/a"));
    }

    @Test
    @DisplayName("a vanished page is deleted from the index on refresh")
    void deletedPageIsRemoved() {
        store.upsert(List.of(document("a", "https://ex.com/gone", "L").toMap()));
        strategy.itemsBySource.put("a", List.of()); // no longer discovered

        scheduler(properties(true, source("a", true))).refresh();

        assertThat(store.loadState("a")).isEmpty();
    }

    /** Test-double strategy: records discovered sources, can throw or fire a one-shot re-entrant action. */
    private static final class StubStrategy implements ContentSourceStrategy {
        private final List<String> discoveredSources = new ArrayList<>();
        private final Set<String> throwOnDiscover = new HashSet<>();
        private final Map<String, List<DiscoveredItem>> itemsBySource = new HashMap<>();
        private BiFunction<ContentSource, DiscoveredItem, FetchOutcome> fetchFn =
            (source, item) -> FetchOutcome.index(document(source.id(), item.url(), item.lastmod()));
        private Runnable reentrantOnce;
        private boolean reentrantFired;

        @Override
        public SourceType type() {
            return SourceType.WEBSITE;
        }

        @Override
        public List<DiscoveredItem> discover(ContentSource source) {
            discoveredSources.add(source.id());
            if (reentrantOnce != null && !reentrantFired) {
                reentrantFired = true;
                reentrantOnce.run();
            }
            if (throwOnDiscover.contains(source.id())) {
                throw new IllegalStateException("boom " + source.id());
            }
            return itemsBySource.getOrDefault(source.id(), List.of());
        }

        @Override
        public FetchOutcome fetch(ContentSource source, DiscoveredItem item) {
            return fetchFn.apply(source, item);
        }
    }

    /** In-memory {@link ContentIndexStore}: documents keyed by id. */
    private static final class InMemoryContentIndexStore implements ContentIndexStore {
        private final Map<String, Map<String, Object>> documents = new HashMap<>();

        @Override
        public Map<String, StoredDocument> loadState(String source) {
            Map<String, StoredDocument> state = new HashMap<>();
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
}
