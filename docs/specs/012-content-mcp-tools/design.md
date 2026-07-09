# Design: Content MCP Tools

## Summary

`ContentMcpToolProvider` implements the library `McpToolProvider` interface and exposes the four
content tools on the existing `/mcp` endpoint: `search_content`, `list_posts`, `get_post`, and
`list_categories`. Because `McpServerConfig` aggregates every `McpToolProvider` bean in the
context, declaring this one bean makes the tools appear automatically. Tool schemas and argument
parsing use the house helpers (`McpTools`, `McpToolSupport`, `McpPaging`), and the tools delegate
to `ContentSearchService` (spec 011).

## GitHub Issue

— (roadmap Phase 1 step 12; design doc §7)

## Goals

- Implement `List<SyncToolSpecification> toolSpecifications()` returning the 4 tools.
- Build each tool via `McpTools.tool(name, description, props, required)` + `McpToolSupport.spec(tool, logic)`.
- Parse arguments with `McpTools` accessors (`requiredString`, `string`, `integer`) and page with `McpPaging`.
- Return results wrapped for pagination where applicable (`McpPage` / `support.paginate`).

## Non-goals

- No new search logic (all delegated to `ContentSearchService`, spec 011).
- No auth changes (spec 013 / already provided by `McpSecurityConfig`).

## Technical approach

Mirror the house pattern (from `open-crm`'s `McpToolFactory`): a `@Component` implementing
`McpToolProvider`, constructor-injecting `ContentSearchService`, `McpToolSupport`, and `McpPaging`,
with one private builder method per tool and static imports from `McpTools`.

```java
@Component
public class ContentMcpToolProvider implements McpToolProvider {
    // ctor: ContentSearchService search, McpToolSupport support, McpPaging paging

    @Override public List<SyncToolSpecification> toolSpecifications() {
        return List.of(searchContent(), listPosts(), getPost(), listCategories());
    }
}
```

### Tools (design §7)

| Tool | Params | Behavior |
|---|---|---|
| `search_content` | `query` (req), `locale?`, `source?`, `category?`, `page?`, `size?` | `ContentSearchService.search(...)` → title, url, publishedDate, highlighted snippet, score |
| `list_posts` | `locale?`, `source?`, `category?`, `since?`, `page?`, `size?` | `ContentSearchService.listPosts(...)` sorted `publishedDate:desc` |
| `get_post` | `url` **or** `id` (one required) | `ContentSearchService.getByUrlOrId(...)` → full body + metadata; `NoSuchElementException` → not-found if absent |
| `list_categories` | `source?`, `locale?` | `ContentSearchService.categoryFacets(...)` → categories + counts |

Example (search):

```java
private SyncToolSpecification searchContent() {
    var props = paginationProps();                 // page, size
    props.put("query", prop("string", "Search query."));
    props.put("locale", prop("string", "Filter by locale (en|de)."));
    props.put("source", prop("string", "Filter by source id."));
    props.put("category", prop("string", "Filter by category."));
    var tool = tool("search_content", "Full-text search across indexed content …",
                    props, List.of("query"));
    return support.spec(tool, args -> {
        var f = new ContentFilters(string(args,"source"), string(args,"locale"), string(args,"category"), null);
        int page = paging.resolvePage(integer(args,"page"));
        int size = paging.resolveSize(integer(args,"size"));
        return search.search(requiredString(args,"query"), f, page, size);
    });
}
```

- `get_post` validates that exactly one of `url`/`id` is present (`IllegalArgumentException` → invalid-argument if neither).
- Paging uses `McpPaging.resolvePage/resolveSize` (honors `openelements.mcp` default/max page size).
- `McpToolSupport.spec` wraps each tool with structured access logging and JSON-RPC error mapping (`IllegalArgumentException`→invalid-argument, `NoSuchElementException`→not-found, `McpUnavailableException`→unavailable).

### Availability during bootstrap

If searches should fail cleanly while the index is still bootstrapping (spec 009), the tool
logic can throw `McpUnavailableException` when `SearchReadinessState.isBootstrapping()` — mapped
by the library to a temporary-unavailable JSON-RPC error. Decision: apply this guard to
search/list tools.

### Rationale

- **`McpToolProvider` bean auto-aggregation** — zero endpoint wiring; matches design §2/§7.
- **House helpers** — consistent schema/argument/paging/error behavior with the rest of the platform.
- **Thin tools over `ContentSearchService`** — tools are adapters; all query logic stays in spec 011.

## Dependencies

- `McpToolProvider`, `McpToolSupport`, `McpTools`, `McpPaging`, `McpPage`, `McpUnavailableException` (spring-services MCP).
- `ContentSearchService` (011), `SearchReadinessState` (spring-services).

## Open questions

- Whether search results should be paginated via `support.paginate` (materialized list) or return `SearchHits` with Meilisearch's estimated total directly. Prefer returning the service's `SearchHits` (already paged by Meilisearch) to avoid double paging.
- Snippet length / how much `body` to include in `get_post` (full body vs. capped). Design §7 says full text for `get_post`; return full `body`.
