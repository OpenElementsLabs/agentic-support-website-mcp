# Implementation Steps: Source Strategy

## Step 1: `FetchOutcome`

- [x] Record `FetchOutcome(Result, ContentDocument)` with nested `Result {INDEX, UNCHANGED, DELETE, SKIP}` and factory helpers

**Related behaviors:** all fetch-outcome scenarios (return shape)

---

## Step 2: `ContentSourceStrategy` interface

- [x] `type()`, `discover(source)`, `fetch(source, item)` — the seam the indexer depends on

**Related behaviors:** all (contract)

---

## Step 3: `WebsiteSourceStrategy`

- [x] `@Component` implementing the interface for `SourceType.WEBSITE`
- [x] `discover` delegates to `SitemapCrawler`
- [x] `fetch` maps `FetchResult` → `FetchOutcome`: 304 → UNCHANGED, 404/410 → DELETE, error → SKIP, OK → extract → INDEX (empty extraction → SKIP)
- [x] Builds the `ContentDocument` from `ExtractedContent` + url + source (lastmod = the discovery change marker)

**Related behaviors:** discover delegation; INDEX; UNCHANGED; DELETE; SKIP (error and empty)

---

## Step 4: `SourceStrategyRegistry`

- [x] `@Component` indexing injected `List<ContentSourceStrategy>` by `type()`; `forSource` throws a clear error for unknown types; duplicate types fail fast

**Related behaviors:** website resolves; unknown type fails clearly; new strategy auto-discovered

---

## Step 5: Tests

- [x] `SourceStrategyRegistryTest` — resolve, unknown-type error, auto-discovery, duplicate guard (test-double strategies)
- [x] `WebsiteSourceStrategyTest` — real `SitemapCrawler`/`PageFetcher`/`ContentExtractor` + `MockRestServiceServer`: type, discover delegation, INDEX/UNCHANGED/DELETE/SKIP(error)/SKIP(empty)

**Acceptance criteria:**
- [x] All tests pass (`mvn test`); build green

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| Website source resolves to website strategy | Backend | Step 5 (`SourceStrategyRegistryTest.websiteSourceResolvesToWebsiteStrategy`) |
| Unknown type fails clearly | Backend | Step 5 (`SourceStrategyRegistryTest.unknownTypeFailsClearly`) |
| discover delegates to the sitemap crawler | Backend | Step 5 (`WebsiteSourceStrategyTest.discoverDelegatesToCrawler`) |
| Successful fetch produces an INDEX outcome | Backend | Step 5 (`WebsiteSourceStrategyTest.successfulFetchProducesIndex`) |
| 304 produces UNCHANGED | Backend | Step 5 (`WebsiteSourceStrategyTest.notModifiedProducesUnchanged`) |
| 404 produces DELETE | Backend | Step 5 (`WebsiteSourceStrategyTest.notFoundProducesDelete`) |
| Extraction failure produces SKIP | Backend | Step 5 (`WebsiteSourceStrategyTest.emptyContentProducesSkip`, `fetchErrorProducesSkip`) |
| New strategy is auto-discovered | Backend | Step 5 (`SourceStrategyRegistryTest.newStrategyIsAutoDiscovered`) |

All scenarios are backend; there is no frontend in this spec.
