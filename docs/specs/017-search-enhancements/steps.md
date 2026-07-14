# Implementation Steps: Search Enhancements

Scope for this spec (chosen from the design's optional menu): **synonyms & stop words** and **facet
surfacing**. **Semantic/hybrid search is deferred** to a separate spec (heavy: embedder provider +
indexing cost), per the design's open question.

## Step 1: Synonyms & stop words

- [x] `ContentSearchProperties` (`@ConfigurationProperties("open-elements.content.search")`): `synonyms` (map) + `stopWords` (list); registered on `ContentConfig`
- [x] `SearchSettingsInitializer` (`ApplicationRunner`, `@Order(40)`, gated on `meilisearch.enabled`): pushes `synonyms`/`stopWords` via `MeilisearchClient.updateSettings` (no data reindex); no-op when unset; failure logged, not fatal
- [x] Commented example in `application.yaml`

**Related behaviors:** synonym expands a query; stop word ignored; settings applied without reindex disruption

---

## Step 2: Facet surfacing

- [x] `SearchHits` gains a `facets` (`List<CategoryCount>`) field
- [x] `ContentSearchService.search` requests `facets: ["categories"]` and populates `SearchHits.facets` from `facetDistribution`; `list_categories` unchanged (already faceted)

**Related behaviors:** search response includes facet distribution; list_categories reflects enriched facets

---

## Step 3: Semantic search (deferred)

- [x] Not implemented — with it absent/off, no embeddings are generated and keyword search is unchanged (the "semantic is opt-in / off = unchanged" behavior holds by default). A future spec would add an embedder + hybrid mode.

**Related behaviors:** semantic opt-in (off by default → unchanged); hybrid search — deferred

---

## Step 4: Tests

- [x] `SearchSettingsInitializerTest` — settings pushed via `updateSettings`, no-op when empty, `buildSettings` keys
- [x] `ContentSearchServiceTest.searchSurfacesFacets` — search requests facets and returns counts
- [x] Non-regression: the full existing suite (155 → 159) still passes with enhancements present but unconfigured

**Acceptance criteria:**
- [x] All tests pass (`mvn test`); build green

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| Synonym expands a query | Backend | Step 4 (settings pushed); live expansion is Meilisearch's, verified against a live instance |
| Stop word is ignored in ranking | Backend | Step 4 (settings pushed); live effect is Meilisearch's |
| Settings applied without reindex disruption | Backend | Step 4 (`SearchSettingsInitializerTest` — `updateSettings`, not addDocuments) |
| Search response includes facet distribution | Backend | Step 4 (`searchSurfacesFacets`) |
| list_categories reflects enriched facets | Backend | Existing `ContentSearchService.categoryFacets` (spec 011) |
| Hybrid search returns semantically related results | Backend | Deferred (Step 3) — separate spec |
| Semantic search is opt-in | Backend | Step 3 — off by default → keyword search unchanged |
| Enhancements do not break core search | Backend | Step 4 (full suite green; enhancements additive/gated) |

All scenarios are backend; there is no frontend in this spec.
