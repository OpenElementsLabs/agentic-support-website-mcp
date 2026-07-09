# Design: Content Document & Index Settings

## Summary

Define the canonical `ContentDocument` record (the shape of one indexed page) and register
the Meilisearch `IndexSettings` bean for the content index. This gives the search stack a
concrete index to initialize: searchable attributes weighted `title > excerpt > body`,
filterable attributes for `source`/`locale`/`author`/`categories`/`publishedDate`, and
`publishedDate` sortable. No crawling yet — this defines the data contract and index schema.

## GitHub Issue

— (roadmap Phase 1 step 3; design doc §4, §8)

## Goals

- `ContentDocument` record matching the canonical JSON of design §4.
- A stable primary key derived from `source + url`.
- A `ContentIndexSettings` `@Bean IndexSettings` registered so `MeilisearchIndexSettingsInitializer` configures the index at startup.
- A `toMap()` mapping from `ContentDocument` to `Map<String,Object>` for `MeilisearchClient.addDocuments` / `BatchWriter`.

## Non-goals

- No document production (crawling/extraction) — specs 004–008.
- No `SearchIndexBootstrapStep` yet (spec 009).
- No search/query logic (spec 011).

## Technical approach

### `ContentDocument` (record)

```java
public record ContentDocument(
    String id,             // stable hash of source + url
    String source,         // e.g. "open-elements"
    String locale,         // "en" | "de"
    String url,
    String title,
    String excerpt,
    String body,           // full cleaned text / Markdown
    String author,
    List<String> categories,
    String publishedDate,  // ISO date "2026-03-12"
    String lastmod,        // ISO datetime — change marker
    String previewImage
) {
    public static String id(String source, String url) { /* stable hash */ }
    public Map<String, Object> toMap() { /* for addDocuments */ }
}
```

Primary key = stable hash of `source + url` (design §4). Use a deterministic hash (e.g.
SHA-256 hex, or a readable `source:path` form). Meilisearch primary keys allow
`[A-Za-z0-9_-]`, so a hex digest or sanitized slug is required — a raw URL is **not** a valid
primary key. Decision: `id = source + ":" + sha256Hex(url)` (readable prefix + safe digest).

### `ContentIndexSettings` (`@Bean IndexSettings`)

`IndexSettings` is a record `(indexUid, primaryKey, searchableAttributes, filterableAttributes, sortableAttributes)`.

```java
@Bean
IndexSettings contentIndexSettings(MeilisearchProperties props) {
    return new IndexSettings(
        props.resolveIndex("content"),                 // e.g. "content_content" with prefix, or "content"
        "id",
        List.of("title", "excerpt", "body"),            // order = ranking weight
        List.of("source", "locale", "author", "categories", "publishedDate"),
        List.of("publishedDate")
    );
}
```

`MeilisearchIndexSettingsInitializer` (library, `@Order(20)`) writes these settings to the
index at startup, so no manual `updateSettings` call is needed for the base schema.

### Ranking / tie-breaker note

`IndexSettings` intentionally carries only searchable/filterable/sortable attributes — it does
**not** carry Meilisearch *ranking rules*. The design's `publishedDate:desc` **tie-breaker**
is therefore applied one of two ways:
- **Query-time sort** (`sort: ["publishedDate:desc"]`) in `ContentSearchService` (spec 011) — simplest, no extra settings.
- Or a custom ranking rule pushed via `MeilisearchClient.updateSettings` if a global default is wanted.

Decision: apply `publishedDate:desc` at query time (spec 011); do not add custom ranking rules here.

### Rationale

- **One index + `locale` filter** (design §8) rather than per-language indexes — start simple; revisit only if ranking-tuning diverges (spec 017).
- **`resolveIndex("content")`** so the configured index prefix is honored consistently.
- **Hash-based id** because Meilisearch primary keys cannot contain `/`, `:`-in-path, etc. from raw URLs.

## Data model

The `ContentDocument` fields are exactly the design §4 JSON. Dates are stored as ISO strings;
`publishedDate` is a plain date, `lastmod` a datetime. `categories` is a string array
(filterable + facetable).

## Open questions

- Whether `publishedDate` should be a numeric timestamp for reliable Meilisearch sorting vs. an ISO string. ISO date strings sort lexicographically correctly for `YYYY-MM-DD`; keep string. Confirm during spec 011.
- Exact `resolveIndex` result given the `index-prefix` config (`content_` prefix → `content_content`?). Align the prefix/suffix so the final UID reads well.
