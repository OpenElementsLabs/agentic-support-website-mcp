package com.openelements.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.openelements.spring.base.services.search.Highlighter;
import com.openelements.spring.base.services.search.MeilisearchClient;
import com.openelements.spring.base.services.search.MeilisearchProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Read-only facade over the Meilisearch content index, backing the MCP tools (spec 012).
 *
 * <p>It builds {@code multi-search} request bodies with AND-combined filters, a
 * {@code publishedDate:desc} tie-breaker, and the library {@link Highlighter}'s boundary markers as
 * highlight tags, then turns the raw hits into {@link SearchHit}s with HTML-safe snippets. Searchable
 * ranking ({@code title > excerpt > body}) is governed by the {@code IndexSettings} (spec 003); this
 * service only adds the date tie-breaker and never writes.
 *
 * <p>Only created when the Meilisearch stack is enabled.
 */
@Component
@ConditionalOnProperty(prefix = "openelements.meilisearch", name = "enabled", havingValue = "true")
public class ContentSearchService {

    /** Words of {@code body} kept around a match for the snippet preview. */
    static final int SNIPPET_CROP_LENGTH = 50;

    private final MeilisearchClient client;
    private final String indexUid;

    public ContentSearchService(MeilisearchClient client, MeilisearchProperties meilisearchProperties) {
        this.client = client;
        this.indexUid = meilisearchProperties.resolveIndex("content");
    }

    /**
     * Full-text search with filters, highlighting, and a date tie-breaker.
     *
     * @param query   the search terms (blank matches everything)
     * @param filters optional filters
     * @param page    zero-based page index
     * @param size    page size
     * @return the matching hits
     */
    public SearchHits search(String query, ContentFilters filters, int page, int size) {
        Map<String, Object> query0 = baseQuery(filters, page, size);
        query0.put("q", query == null ? "" : query);
        query0.put("sort", List.of("publishedDate:desc"));
        query0.put("attributesToHighlight", List.of("title", "excerpt", "body"));
        query0.put("attributesToCrop", List.of("body"));
        query0.put("cropLength", SNIPPET_CROP_LENGTH);
        query0.put("highlightPreTag", Highlighter.PRE_MARK);
        query0.put("highlightPostTag", Highlighter.POST_MARK);
        query0.put("showRankingScore", true);
        return parseHits(client.multiSearch(wrap(query0)));
    }

    /**
     * Lists documents (no query) sorted newest-first, with optional filters.
     *
     * @param filters optional filters
     * @param page    zero-based page index
     * @param size    page size
     * @return the matching documents as hits
     */
    public SearchHits listPosts(ContentFilters filters, int page, int size) {
        Map<String, Object> query0 = baseQuery(filters, page, size);
        query0.put("q", "");
        query0.put("sort", List.of("publishedDate:desc"));
        return parseHits(client.multiSearch(wrap(query0)));
    }

    /**
     * Fetches a full document by id (preferred) or url.
     *
     * @param url the document URL (used when {@code id} is blank)
     * @param id  the document id
     * @return the document, or empty if none matches (or both arguments are blank)
     */
    public Optional<ContentDocument> getByUrlOrId(String url, String id) {
        String filter;
        if (present(id)) {
            filter = "id = \"" + escape(id) + "\"";
        } else if (present(url)) {
            filter = "url = \"" + escape(url) + "\"";
        } else {
            return Optional.empty();
        }
        Map<String, Object> query0 = new LinkedHashMap<>();
        query0.put("indexUid", indexUid);
        query0.put("q", "");
        query0.put("filter", filter);
        query0.put("limit", 1);
        return firstDocument(client.multiSearch(wrap(query0)));
    }

    /**
     * Category facet counts, optionally restricted by source/locale.
     *
     * @param source optional source filter
     * @param locale optional locale filter
     * @return the categories with their document counts
     */
    public List<CategoryCount> categoryFacets(String source, String locale) {
        Map<String, Object> query0 = new LinkedHashMap<>();
        query0.put("indexUid", indexUid);
        query0.put("q", "");
        String filter = buildFilter(new ContentFilters(source, locale, null, null));
        if (filter != null) {
            query0.put("filter", filter);
        }
        query0.put("facets", List.of("categories"));
        query0.put("limit", 0);
        return parseFacets(client.multiSearch(wrap(query0)));
    }

    // ---- request building ----

    private Map<String, Object> baseQuery(ContentFilters filters, int page, int size) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("indexUid", indexUid);
        String filter = buildFilter(filters);
        if (filter != null) {
            query.put("filter", filter);
        }
        int safeSize = Math.max(0, size);
        query.put("limit", safeSize);
        query.put("offset", Math.max(0, page) * safeSize);
        return query;
    }

    private static Map<String, Object> wrap(Map<String, Object> query) {
        return Map.of("queries", List.of(query));
    }

    /** Builds the AND-combined Meilisearch filter expression, or {@code null} when no filter applies. */
    static String buildFilter(ContentFilters filters) {
        if (filters == null) {
            return null;
        }
        List<String> clauses = new ArrayList<>();
        if (present(filters.source())) {
            clauses.add("source = \"" + escape(filters.source()) + "\"");
        }
        if (present(filters.locale())) {
            clauses.add("locale = \"" + escape(filters.locale()) + "\"");
        }
        if (present(filters.category())) {
            clauses.add("categories = \"" + escape(filters.category()) + "\"");
        }
        if (present(filters.since())) {
            clauses.add("publishedDate >= \"" + escape(filters.since()) + "\"");
        }
        return clauses.isEmpty() ? null : String.join(" AND ", clauses);
    }

    // ---- response parsing ----

    static SearchHits parseHits(JsonNode response) {
        JsonNode result = firstResult(response);
        if (result == null) {
            return SearchHits.empty();
        }
        List<SearchHit> hits = new ArrayList<>();
        for (JsonNode hit : result.path("hits")) {
            hits.add(toHit(hit));
        }
        return new SearchHits(hits, result.path("estimatedTotalHits").asLong(0));
    }

    private static SearchHit toHit(JsonNode hit) {
        JsonNode formatted = hit.path("_formatted");
        String rawSnippet = firstNonBlank(text(formatted, "body"), text(formatted, "excerpt"), text(hit, "excerpt"));
        String snippet = rawSnippet == null ? "" : Highlighter.safeHighlight(rawSnippet);
        return new SearchHit(
            text(hit, "title"), text(hit, "url"), text(hit, "publishedDate"),
            snippet, hit.path("_rankingScore").asDouble(0.0));
    }

    static List<CategoryCount> parseFacets(JsonNode response) {
        JsonNode result = firstResult(response);
        if (result == null) {
            return List.of();
        }
        List<CategoryCount> counts = new ArrayList<>();
        result.path("facetDistribution").path("categories").fields()
            .forEachRemaining(entry -> counts.add(new CategoryCount(entry.getKey(), entry.getValue().asLong(0))));
        return counts;
    }

    private static Optional<ContentDocument> firstDocument(JsonNode response) {
        JsonNode result = firstResult(response);
        if (result == null) {
            return Optional.empty();
        }
        JsonNode hits = result.path("hits");
        if (!hits.isArray() || hits.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toDocument(hits.get(0)));
    }

    private static ContentDocument toDocument(JsonNode hit) {
        List<String> categories = new ArrayList<>();
        hit.path("categories").forEach(category -> categories.add(category.asText()));
        return new ContentDocument(
            text(hit, "id"), text(hit, "source"), text(hit, "locale"), text(hit, "url"),
            text(hit, "title"), text(hit, "excerpt"), text(hit, "body"), text(hit, "author"),
            categories, text(hit, "publishedDate"), text(hit, "lastmod"), text(hit, "previewImage"));
    }

    // ---- small helpers ----

    private static JsonNode firstResult(JsonNode response) {
        JsonNode results = response.path("results");
        return results.isArray() && !results.isEmpty() ? results.get(0) : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String escape(String value) {
        return value.replace("\"", "\\\"");
    }
}
