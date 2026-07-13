package com.openelements.content;

import static com.openelements.spring.base.mcp.McpTools.integer;
import static com.openelements.spring.base.mcp.McpTools.paginationProps;
import static com.openelements.spring.base.mcp.McpTools.prop;
import static com.openelements.spring.base.mcp.McpTools.requiredString;
import static com.openelements.spring.base.mcp.McpTools.string;
import static com.openelements.spring.base.mcp.McpTools.tool;

import com.openelements.spring.base.mcp.McpPaging;
import com.openelements.spring.base.mcp.McpToolProvider;
import com.openelements.spring.base.mcp.McpToolSupport;
import com.openelements.spring.base.mcp.McpUnavailableException;
import com.openelements.spring.base.services.search.SearchReadinessState;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Exposes the four content tools on the {@code /mcp} endpoint by implementing the library
 * {@link McpToolProvider} (which {@code McpServerConfig} auto-aggregates). The tools are thin
 * adapters over {@link ContentSearchService}; schemas, argument parsing, paging, access logging, and
 * JSON-RPC error mapping are handled by the house helpers.
 *
 * <p>Error mapping (via {@link McpToolSupport#spec}): {@link IllegalArgumentException} →
 * invalid-argument, {@link NoSuchElementException} → not-found, {@link McpUnavailableException} →
 * temporary-unavailable. Search and list report unavailable while the index is bootstrapping.
 *
 * <p>Only created when the MCP server is enabled.
 */
@Component
@ConditionalOnProperty(prefix = "openelements.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ContentMcpToolProvider implements McpToolProvider {

    private final ContentSearchService searchService;
    private final McpToolSupport support;
    private final McpPaging paging;
    private final SearchReadinessState readinessState;

    public ContentMcpToolProvider(ContentSearchService searchService, McpToolSupport support,
                                  McpPaging paging, SearchReadinessState readinessState) {
        this.searchService = searchService;
        this.support = support;
        this.paging = paging;
        this.readinessState = readinessState;
    }

    @Override
    public List<SyncToolSpecification> toolSpecifications() {
        return List.of(searchContent(), listPosts(), getPost(), listCategories());
    }

    // ---- tool definitions ----

    private SyncToolSpecification searchContent() {
        Map<String, Object> props = new LinkedHashMap<>(paginationProps());
        props.put("query", prop("string", "Search query (required)."));
        props.put("locale", prop("string", "Filter by locale, e.g. en or de."));
        props.put("source", prop("string", "Filter by source id."));
        props.put("category", prop("string", "Filter by category."));
        Tool tool = tool("search_content",
            "Full-text search across the indexed content; returns highlighted snippets.",
            props, List.of("query"));
        return support.spec(tool, this::searchContentLogic);
    }

    private SyncToolSpecification listPosts() {
        Map<String, Object> props = new LinkedHashMap<>(paginationProps());
        props.put("locale", prop("string", "Filter by locale, e.g. en or de."));
        props.put("source", prop("string", "Filter by source id."));
        props.put("category", prop("string", "Filter by category."));
        props.put("since", prop("string", "Only posts published on/after this ISO date (YYYY-MM-DD)."));
        Tool tool = tool("list_posts",
            "List indexed posts, newest first.", props, List.of());
        return support.spec(tool, this::listPostsLogic);
    }

    private SyncToolSpecification getPost() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("url", prop("string", "The post URL (provide url or id)."));
        props.put("id", prop("string", "The post id (provide url or id)."));
        Tool tool = tool("get_post",
            "Get the full content and metadata of a single post by url or id.", props, List.of());
        return support.spec(tool, this::getPostLogic);
    }

    private SyncToolSpecification listCategories() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("source", prop("string", "Filter by source id."));
        props.put("locale", prop("string", "Filter by locale, e.g. en or de."));
        Tool tool = tool("list_categories",
            "List content categories with their document counts.", props, List.of());
        return support.spec(tool, this::listCategoriesLogic);
    }

    // ---- tool logic (package-private for direct unit testing) ----

    Object searchContentLogic(Map<String, Object> args) {
        requireReady();
        ContentFilters filters = new ContentFilters(
            string(args, "source"), string(args, "locale"), string(args, "category"), null);
        return searchService.search(
            requiredString(args, "query"), filters,
            paging.resolvePage(integer(args, "page")), paging.resolveSize(integer(args, "size")));
    }

    Object listPostsLogic(Map<String, Object> args) {
        requireReady();
        ContentFilters filters = new ContentFilters(
            string(args, "source"), string(args, "locale"), string(args, "category"), string(args, "since"));
        return searchService.listPosts(
            filters, paging.resolvePage(integer(args, "page")), paging.resolveSize(integer(args, "size")));
    }

    Object getPostLogic(Map<String, Object> args) {
        String url = string(args, "url");
        String id = string(args, "id");
        if (isBlank(url) && isBlank(id)) {
            throw new IllegalArgumentException("Either 'url' or 'id' is required");
        }
        return searchService.getByUrlOrId(url, id)
            .orElseThrow(() -> new NoSuchElementException("No post found for the given url/id"));
    }

    Object listCategoriesLogic(Map<String, Object> args) {
        return searchService.categoryFacets(string(args, "source"), string(args, "locale"));
    }

    private void requireReady() {
        if (readinessState.isBootstrapping()) {
            throw new McpUnavailableException("Content index is still bootstrapping; try again shortly");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
