# Design: Search Enhancements (Optional)

## Summary

Optional Phase 4 add-ons that improve search quality once the core is proven: per-language
synonyms and stop words, surfacing category facets more richly in the tools, and (optionally)
semantic/embeddings-based search. These are independent, individually shippable enhancements —
none is required for the MCP to be useful, so this spec is a menu of opt-in improvements.

## GitHub Issue

— (roadmap Phase 4; design doc §8, §12 P4)

## Goals (each independent, pick per need)

- **Synonyms & stop words** per language (DE/EN) to improve recall/precision.
- **Facet surfacing:** richer category (and possibly source/locale) facet output in `list_categories` / search responses.
- **Semantic search (optional):** embeddings-based vector search as an alternative/complement to keyword search.

## Non-goals

- No change to the ingestion pipeline or source model.
- Not a prerequisite for any earlier spec.

## Technical approach

### Synonyms & stop words

Meilisearch supports `synonyms` and `stopWords` index settings. Since `IndexSettings`
(spring-services) does not carry these, push them via `MeilisearchClient.updateSettings(indexUid, settings)`
in an application runner (ordered after `MeilisearchIndexSettingsInitializer`), or extend the
settings-write path. Provide per-language lists via new `open-elements.content.search.*` config.

- With one index + `locale` filter (spec 003), synonyms/stop words are index-global; if DE/EN
  need conflicting rules, this is the point to reconsider **two indexes** (`content_en`/`content_de`),
  as flagged in design §8. Decision gate lives here.

### Facet surfacing

Extend `ContentSearchService` (spec 011) to optionally return `facetDistribution` for `source`,
`locale`, and `categories` alongside search hits, and enrich `list_categories`. Purely additive
to the tool responses.

### Semantic search (optional)

Meilisearch vector search / hybrid search: configure an embedder, generate embeddings for
`title`+`body` at index time, and expose a hybrid keyword+semantic mode in `search_content`.
This adds an embeddings dependency/provider and indexing cost — gate behind config and only
pursue if keyword search proves insufficient.

### Rationale

- **Deferred by design** (§12 P4): ship keyword search first, tune later with evidence.
- **`updateSettings` for synonyms/stop words** because `IndexSettings` intentionally omits them; keeps the base schema simple.
- **Two-index decision deferred to here** so it is made with real query data, not upfront.

## Open questions

- Do DE/EN actually need diverging synonyms/stop words (→ two-index split)? Decide with usage data.
- Which embedder/provider for semantic search, and is the added cost/latency justified? Likely a separate spec if pursued.
- Whether facet surfacing needs new MCP tool params or is always-on in responses.
