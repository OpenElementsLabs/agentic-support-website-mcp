# Design: Source Strategy

## Summary

Introduce the `ContentSourceStrategy` interface that decouples the indexer from *how* a source
type is discovered and fetched, and provide the `WebsiteSourceStrategy` that wires together
`SitemapCrawler` (spec 004), `PageFetcher` (spec 005), and `ContentExtractor` (spec 006) for
`type: website`. This is the extension point that lets the later `type: git` strategy (spec 016)
plug in without touching the indexer or the Meilisearch flow.

## GitHub Issue

— (roadmap Phase 1 step 7; design doc §3a)

## Goals

- `ContentSourceStrategy` interface: `discover(src)` and `fetch(item)` (conceptual API from design §3a).
- `WebsiteSourceStrategy` implementing it for `SourceType.WEBSITE`.
- A way for `ContentIndexer` (spec 008) to select the correct strategy per `ContentSource.type()`.

## Non-goals

- No orchestration/diff/indexing (spec 008).
- No `GitSourceStrategy` (spec 016) — only the seam is created here.

## Technical approach

### Interface (design §3a)

```java
public interface ContentSourceStrategy {
    SourceType type();                                  // which ContentSource.type this handles
    List<DiscoveredItem> discover(ContentSource src);   // URLs/files + change marker
    FetchOutcome fetch(ContentSource src, DiscoveredItem item);
}
```

`FetchOutcome` carries enough to build a `ContentDocument` **or** to signal skip/delete:

```java
public record FetchOutcome(Result result, ContentDocument document) {}
public enum Result { INDEX, UNCHANGED, DELETE, SKIP }
```

- `INDEX` — a document to upsert.
- `UNCHANGED` — 304 / same change-marker; nothing to do.
- `DELETE` — the item no longer exists (404/410).
- `SKIP` — fetch/extract failed for this one item; log and continue.

> Note: the design §3a sketch returns `ContentDocument fetch(item)`. We widen it to a
> `FetchOutcome` so the 304/deletion/skip cases (needed by specs 005/008) are first-class
> rather than encoded as null/exceptions. The canonical `ContentDocument` flow into Meilisearch
> stays identical across strategies, per design §3a.

### `WebsiteSourceStrategy`

```java
@Component
public class WebsiteSourceStrategy implements ContentSourceStrategy {
    // constructor-injected: SitemapCrawler, PageFetcher, ContentExtractor
    public SourceType type() { return SourceType.WEBSITE; }
    public List<DiscoveredItem> discover(ContentSource src) { return sitemapCrawler.discover(src); }
    public FetchOutcome fetch(ContentSource src, DiscoveredItem item) {
        // PageFetcher.fetch(...) -> map NOT_MODIFIED/NOT_FOUND/ERROR/OK
        // OK -> ContentExtractor.extract(...) -> ContentDocument -> FetchOutcome.INDEX
    }
}
```

Prior `ETag`/`lastmod` for the conditional GET comes from the indexer's diff (spec 008); this
step defines the seam and the website wiring, and may pass nulls until spec 008 supplies them.

### Strategy selection

A small registry maps `SourceType` → strategy. Spring injects `List<ContentSourceStrategy>`;
the registry indexes them by `strategy.type()`:

```java
@Component
public class SourceStrategyRegistry {
    private final Map<SourceType, ContentSourceStrategy> byType;
    ContentSourceStrategy forSource(ContentSource src);   // throws if no strategy for type
}
```

### Rationale

- **Strategy per `type`** is exactly design §3a: the indexer knows only the interface; adding `git` is a new bean, no core rewrite.
- **`FetchOutcome` enum** keeps the non-happy paths explicit and testable, and centralizes the "what should the indexer do with this item" decision in the strategy.
- **List-injection + registry** is idiomatic Spring and auto-discovers future strategies.

## Dependencies

- `SitemapCrawler` (004), `PageFetcher` (005), `ContentExtractor` (006), `ContentSource`/`SourceType` (002), `ContentDocument` (003).

## Open questions

- Whether `discover` should also return the stored change-marker so the strategy can short-circuit, or whether the indexer owns all diffing (spec 008). Leaning: indexer owns the diff; strategy is stateless.
