package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openelements.spring.base.mcp.McpPaging;
import com.openelements.spring.base.mcp.McpProperties;
import com.openelements.spring.base.mcp.McpToolSupport;
import com.openelements.spring.base.mcp.McpUnavailableException;
import com.openelements.spring.base.services.search.MeilisearchClient;
import com.openelements.spring.base.services.search.MeilisearchProperties;
import com.openelements.spring.base.services.search.SearchReadinessState;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ContentMcpToolProvider}: tool registration and each tool's logic (argument
 * parsing, delegation, paging clamp, and error mapping) via a capturing {@link ContentSearchService}
 * subclass and the real MCP helper beans. No mocks or live Meilisearch.
 */
@DisplayName("Content MCP tool provider")
class ContentMcpToolProviderTest {

    private static final int MAX_PAGE_SIZE = 50;

    private final CapturingSearchService searchService = new CapturingSearchService();
    private final McpPaging paging = new McpPaging(new McpProperties(true, "n", "v", MAX_PAGE_SIZE, 10, null));
    private final McpToolSupport support = new McpToolSupport(paging, new ObjectMapper());
    private final SearchReadinessState readiness = new SearchReadinessState();
    private final ContentMcpToolProvider provider =
        new ContentMcpToolProvider(searchService, support, paging, readiness);

    @BeforeEach
    void markBootstrapComplete() {
        readiness.markBootstrappingFinished();
    }

    private static Map<String, Object> args(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("all four tools are registered with names")
    void fourToolsAreRegistered() {
        List<String> names = provider.toolSpecifications().stream().map(spec -> spec.tool().name()).toList();

        assertThat(names).containsExactlyInAnyOrder(
            "search_content", "list_posts", "get_post", "list_categories");
    }

    @Test
    @DisplayName("search_content passes filters through and clamps the page size")
    void searchPassesFiltersAndClampsSize() {
        searchContentResult();
        provider.searchContentLogic(args("query", "wallet", "locale", "de", "source", "open-elements", "size", 999));

        assertThat(searchService.lastQuery).isEqualTo("wallet");
        assertThat(searchService.lastFilters.locale()).isEqualTo("de");
        assertThat(searchService.lastFilters.source()).isEqualTo("open-elements");
        assertThat(searchService.lastSize).isEqualTo(MAX_PAGE_SIZE); // clamped from 999
    }

    @Test
    @DisplayName("search_content without a query is an invalid-argument error")
    void searchWithoutQueryThrows() {
        assertThatThrownBy(() -> provider.searchContentLogic(args()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("search_content reports unavailable while bootstrapping")
    void searchUnavailableWhileBootstrapping() {
        readiness.markBootstrappingStarted();

        assertThatThrownBy(() -> provider.searchContentLogic(args("query", "wallet")))
            .isInstanceOf(McpUnavailableException.class);
    }

    @Test
    @DisplayName("list_posts passes the since filter and reports unavailable while bootstrapping")
    void listPostsFilterAndAvailability() {
        provider.listPostsLogic(args("since", "2026-01-01", "source", "hiero"));
        assertThat(searchService.lastFilters.since()).isEqualTo("2026-01-01");
        assertThat(searchService.lastFilters.source()).isEqualTo("hiero");

        readiness.markBootstrappingStarted();
        assertThatThrownBy(() -> provider.listPostsLogic(args()))
            .isInstanceOf(McpUnavailableException.class);
    }

    @Test
    @DisplayName("get_post returns the full document by url")
    void getPostByUrl() {
        ContentDocument document = document();
        searchService.document = Optional.of(document);

        Object result = provider.getPostLogic(args("url", "https://ex.com/a"));

        assertThat(result).isEqualTo(document);
        assertThat(searchService.lastUrl).isEqualTo("https://ex.com/a");
    }

    @Test
    @DisplayName("get_post with neither url nor id is an invalid-argument error")
    void getPostWithoutArgsThrows() {
        assertThatThrownBy(() -> provider.getPostLogic(args()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("get_post for an unknown post is a not-found error")
    void getPostUnknownIsNotFound() {
        searchService.document = Optional.empty();

        assertThatThrownBy(() -> provider.getPostLogic(args("id", "oe_missing")))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("list_categories delegates with the source/locale filter")
    void listCategoriesDelegates() {
        searchService.categories = List.of(new CategoryCount("ai", 3));

        Object result = provider.listCategoriesLogic(args("source", "hiero"));

        assertThat(result).isEqualTo(List.of(new CategoryCount("ai", 3)));
        assertThat(searchService.lastSource).isEqualTo("hiero");
    }

    private void searchContentResult() {
        searchService.hits = new SearchHits(List.of(new SearchHit("T", "u", "2026-01-01", "snip", 0.9)), 1);
    }

    private static ContentDocument document() {
        return new ContentDocument("oe_a", "open-elements", "en", "https://ex.com/a",
            "T", "E", "body", "a", List.of("ai"), "2026-01-01", "2026-01-01", null);
    }

    /** Capturing {@link ContentSearchService} test double recording arguments and returning canned data. */
    private static final class CapturingSearchService extends ContentSearchService {
        private String lastQuery;
        private ContentFilters lastFilters;
        private int lastPage;
        private int lastSize;
        private String lastUrl;
        private String lastId;
        private String lastSource;
        private String lastLocale;
        private SearchHits hits = SearchHits.empty();
        private List<CategoryCount> categories = List.of();
        private Optional<ContentDocument> document = Optional.empty();

        CapturingSearchService() {
            super(new MeilisearchClient(props(), new ObjectMapper()), props());
        }

        private static MeilisearchProperties props() {
            return new MeilisearchProperties("http://localhost:7700", "", "content_", Duration.ofSeconds(10));
        }

        @Override
        public SearchHits search(String query, ContentFilters filters, int page, int size) {
            this.lastQuery = query;
            this.lastFilters = filters;
            this.lastPage = page;
            this.lastSize = size;
            return hits;
        }

        @Override
        public SearchHits listPosts(ContentFilters filters, int page, int size) {
            this.lastFilters = filters;
            this.lastPage = page;
            this.lastSize = size;
            return hits;
        }

        @Override
        public Optional<ContentDocument> getByUrlOrId(String url, String id) {
            this.lastUrl = url;
            this.lastId = id;
            return document;
        }

        @Override
        public List<CategoryCount> categoryFacets(String source, String locale) {
            this.lastSource = source;
            this.lastLocale = locale;
            return categories;
        }
    }
}
