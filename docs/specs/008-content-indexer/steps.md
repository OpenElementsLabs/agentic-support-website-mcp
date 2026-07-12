# Implementation Steps: Content Indexer

## Step 1: `IndexReport`

- [x] Record `IndexReport(discovered, upserted, unchanged, deleted, skipped)`

**Related behaviors:** all (report counters)

---

## Step 2: `ContentIndexStore` seam

- [x] Interface `loadState(source) → id→StoredDocument`, `upsert(docs) → count`, `delete(id)`; nested `StoredDocument(url, lastmod)`
- [x] `MeilisearchContentIndexStore` production impl: paged filtered `multiSearch` (testable `parseHits`), batched `addDocuments` + `waitForTask` (BatchWriter is package-private), `deleteDocument`; gated on `meilisearch.enabled`

**Related behaviors:** underpins all (diff/upsert/delete)

---

## Step 3: `ContentIndexer`

- [x] `@Component` (gated on `meilisearch.enabled`) taking `SourceStrategyRegistry` + `ContentIndexStore`
- [x] `indexSource`: discover → load stored state → per item: unchanged (lastmod equal) skip; else strategy.fetch → INDEX(collect)/UNCHANGED/DELETE/SKIP; batch upsert; delete stored-but-not-discovered
- [x] `streamAllDocuments`: lazy discover→fetch→INDEX map stream for bootstrap
- [x] Idempotent (upsert by stable id); fault-tolerant (SKIP one, continue)

**Related behaviors:** new/unchanged/changed/304/vanished/404/idempotent/skip/dedupe/stream

---

## Step 4: Tests

- [x] `ContentIndexerTest` (10) — in-memory store + stub strategy: every behavior
- [x] `MeilisearchContentIndexStoreTest` (3) — `parseHits` parsing (hits, empty, missing id)

**Acceptance criteria:**
- [x] All tests pass (`mvn test`); build green

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| New pages are upserted | Backend | Step 4 (`newPagesAreUpserted`) |
| Unchanged pages are skipped | Backend | Step 4 (`unchangedPagesAreSkipped`) |
| Changed page is re-fetched and upserted | Backend | Step 4 (`changedPageIsReFetched`) |
| Conditional GET short-circuits a lastmod false-positive | Backend | Step 4 (`conditionalGetShortCircuits`) |
| Vanished URL is deleted | Backend | Step 4 (`vanishedUrlIsDeleted`) |
| 404 on fetch deletes the document | Backend | Step 4 (`notFoundDeletesDocument`) |
| Re-running is idempotent | Backend | Step 4 (`reRunIsIdempotent`) |
| One failing page does not abort the batch | Backend | Step 4 (`oneFailingPageIsSkipped`) |
| Stable id prevents duplicates | Backend | Step 4 (`stableIdPreventsDuplicates`) |
| streamAllDocuments yields every in-scope document | Backend | Step 4 (`streamAllDocumentsYieldsAll`) |

All scenarios are backend; there is no frontend in this spec.

## Notes / deviations

- The design suggested `BatchWriter.write(...)`, but `BatchWriter` is package-private in `spring-services` and not reusable from this package. Upsert uses `MeilisearchClient.addDocuments` in batches with `waitForTask`.
- A `ContentIndexStore` seam was introduced (not in the original design) to encapsulate the Meilisearch read/write and make the orchestration unit-testable without a live instance. The production impl's `parseHits` is unit-tested; the `multiSearch` paging / `addDocuments` batching is thin delegation exercised end-to-end by specs 009/010 against real Meilisearch.
