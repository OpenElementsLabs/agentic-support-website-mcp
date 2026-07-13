# Implementation Steps: Content MCP Tools

## Step 1: `ContentMcpToolProvider`

- [x] `@Component implements McpToolProvider` (gated on `openelements.mcp.enabled`), auto-aggregated by `McpServerConfig`
- [x] `toolSpecifications()` returns the four tools built with `McpTools.tool` + `McpToolSupport.spec`
- [x] `search_content` (query required; locale/source/category filters; paging) → `ContentSearchService.search`
- [x] `list_posts` (locale/source/category/since; paging) → `ContentSearchService.listPosts`
- [x] `get_post` (url or id; neither → `IllegalArgumentException`; absent → `NoSuchElementException`) → `getByUrlOrId`
- [x] `list_categories` (source/locale) → `categoryFacets`
- [x] Paging via `McpPaging.resolvePage/resolveSize`; bootstrap guard throws `McpUnavailableException` for search/list
- [x] Tool logic extracted to package-private methods for direct unit testing

**Related behaviors:** all

---

## Step 2: Tests

- [x] `ContentMcpToolProviderTest` (capturing `ContentSearchService` subclass + real `McpToolSupport`/`McpPaging`): four tools registered; per-tool delegation, filter pass-through, size clamp, missing-arg/not-found/unavailable errors

**Acceptance criteria:**
- [x] All tests pass (`mvn test`); build green

---

## Step 3: Drift + dependency fixes

- [x] Update spec 001's `ContentMcpApplicationTests` — the skeleton asserted an empty `McpToolProvider` catalog; a provider now exists. Drift recorded in `docs/specs/001-project-skeleton/behaviors.md`.
- [x] Pin `spring-services` to `1.3.0-20260712.175350-13` (the floating `1.3.0-SNAPSHOT` published a broken, dependency-less `-14` POM that broke the build).

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| Four tools appear on /mcp | Backend | Step 2 (`fourToolsAreRegistered`) |
| search_content returns highlighted hits | Backend | Step 2 (`searchPassesFiltersAndClampsSize`) + `ContentSearchServiceTest` (spec 011 highlighting) |
| Missing required query is rejected | Backend | Step 2 (`searchWithoutQueryThrows`) |
| Filters are passed through | Backend | Step 2 (`searchPassesFiltersAndClampsSize`) |
| Paging is honored and clamped | Backend | Step 2 (`searchPassesFiltersAndClampsSize`) |
| list_posts lists newest first / since filter | Backend | Step 2 (`listPostsFilterAndAvailability`) + spec 011 sort |
| get_post by url / id returns full post | Backend | Step 2 (`getPostByUrl`) |
| Neither url nor id is an error | Backend | Step 2 (`getPostWithoutArgsThrows`) |
| Unknown post is not-found | Backend | Step 2 (`getPostUnknownIsNotFound`) |
| list_categories returns counts / scoped | Backend | Step 2 (`listCategoriesDelegates`) |
| Tool reports unavailable during bootstrap | Backend | Step 2 (`searchUnavailableWhileBootstrapping`, `listPostsFilterAndAvailability`) |
| Each tool call is access-logged | Backend | Delegated to `McpToolSupport.spec` (library); tools are built via it. |

All scenarios are backend; there is no frontend in this spec.
