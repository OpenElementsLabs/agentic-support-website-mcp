# Implementation Steps: Content Search Service

## Step 1: Result/filter records

- [x] `ContentFilters(source, locale, category, since)` (+ `none()`)
- [x] `SearchHit(title, url, publishedDate, snippet, score)`, `SearchHits(hits, estimatedTotal)`, `CategoryCount(category, count)`

**Related behaviors:** all (return/argument shapes)

---

## Step 2: `ContentSearchService`

- [x] `@Component` (gated on `meilisearch.enabled`) over `MeilisearchClient` + `MeilisearchProperties`
- [x] `search`: builds a `multi-search` body — filter, `publishedDate:desc` sort, highlight tags = `Highlighter.PRE_MARK`/`POST_MARK`, crop `body`, `showRankingScore`, paging — and parses hits with `Highlighter.safeHighlight`
- [x] `listPosts`: empty query, `publishedDate:desc`, paged
- [x] `getByUrlOrId`: filter by `id` (preferred) or `url`; empty when both blank / not found
- [x] `categoryFacets`: `facets:["categories"]` with source/locale filter, `limit 0`
- [x] `buildFilter` AND-combines non-null filters (quote-escaped)

**Related behaviors:** all search/filter/list/get/facet scenarios

---

## Step 3: Tests

- [x] `ContentSearchServiceTest`: pure `buildFilter`/`parseHits`/`parseFacets` + public methods via a capturing `MeilisearchClient` subclass (request bodies + parsing + get + empty)

**Acceptance criteria:**
- [x] All tests pass (`mvn test`); build green

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| Query returns relevant hits with snippet | Backend | Step 3 (`ResponseParsing.parsesHits` + `RequestBuilding.searchBuildsRequest`) |
| Title matches rank above body matches | Backend | Governed by `IndexSettings` searchable order (spec 003) + Meilisearch; the service adds only the date tie-breaker (verified in `searchBuildsRequest`). Live ranking needs Meilisearch. |
| Highlight markers become safe em tags | Backend | Step 3 (`highlightMarkersBecomeSafeEmTags`) |
| Locale/Source/Category filter restricts results | Backend | Step 3 (`FilterBuilding.*` + `searchBuildsRequest`) |
| since filters by published date | Backend | Step 3 (`FilterBuilding.since`) |
| Combined filters are AND-combined | Backend | Step 3 (`FilterBuilding.combined`) |
| listPosts sorts by published date descending | Backend | Step 3 (`listPostsBuildsRequest`) |
| Empty query returns all (filtered) posts | Backend | Step 3 (`listPostsBuildsRequest`) |
| Get by url / id returns full document | Backend | Step 3 (`GetByUrlOrId.returnsFullDocument`, `getById/UrlBuildsFilter`) |
| Get for unknown url/id is empty | Backend | Step 3 (`emptyWhenNotFound`, `emptyWhenNoArguments`) |
| Category facets return counts / respect filter | Backend | Step 3 (`parsesFacets` + `facetsBuildRequest`) |
| No results / paging beyond the end | Backend | Step 3 (`parsesEmpty`; paging via `offset` in `searchBuildsRequest`) |

All scenarios are backend; there is no frontend in this spec. Live ranking/filtering is Meilisearch's
responsibility (the service builds the correct request); tests verify the request and the parsing.
