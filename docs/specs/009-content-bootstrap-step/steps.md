# Implementation Steps: Content Bootstrap Step

## Step 1: `ContentBootstrapStep`

- [x] `@Component` (gated on `meilisearch.enabled`) implementing `SearchIndexBootstrapStep`
- [x] `indexUid()` → `resolveIndex("content")`
- [x] `documents()` → lazy `flatMap` over enabled sources via `ContentIndexer.streamAllDocuments`
- [x] Per-source fault isolation: a source that fails to stream is logged and skipped, others still contribute

**Acceptance criteria:**
- [x] Project builds; behaviors verified by tests
- [x] Discovered by the library `MeilisearchBootstrapRunner` (auto-injected `List<SearchIndexBootstrapStep>`)

**Related behaviors:** indexUid targets content index; all enabled sources contribute; stream is lazy; failing source does not block others

---

## Step 2: Tests

- [x] `ContentBootstrapStepTest` (real `ContentIndexer` + stub strategy): indexUid, enabled-only sources, laziness, failing-source isolation

**Acceptance criteria:**
- [x] All tests pass (`mvn test`); build green

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| Step is discovered by the runner | Backend (integration) | Delegated to the library `MeilisearchBootstrapRunner` (auto-injects `SearchIndexBootstrapStep` beans); `@Component` registration verified by the full app-context build. Live end-to-end run needs a running Meilisearch. |
| indexUid targets the content index | Backend | Step 2 (`indexUidTargetsContentIndex`) |
| All enabled sources contribute documents | Backend | Step 2 (`onlyEnabledSourcesContribute`) |
| Stream is lazy | Backend | Step 2 (`streamIsLazy`) |
| Readiness flips after bootstrap | Backend (integration) | Owned by the library runner (`markBootstrappingStarted/Finished` on `SearchReadinessState`); needs a live Meilisearch. |
| Search short-circuits during bootstrap | Backend | Deferred to spec 011 (search layer observes `SearchReadinessState.isBootstrapping()`). |
| A failing source does not block others | Backend | Step 2 (`failingSourceDoesNotBlockOthers`) |

All scenarios are backend; there is no frontend in this spec. The readiness/runner scenarios are the library's responsibility and require a live Meilisearch, consistent with the project's established live-search test boundary.
