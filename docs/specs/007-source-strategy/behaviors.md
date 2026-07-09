# Behaviors: Source Strategy

## Strategy selection

### Website source resolves to website strategy
- **Given** a `ContentSource` with `type = WEBSITE`
- **When** `SourceStrategyRegistry.forSource(src)` is called
- **Then** it returns the `WebsiteSourceStrategy`

### Unknown type fails clearly
- **Given** a `ContentSource` whose `type` has no registered strategy
- **When** `forSource(src)` is called
- **Then** it throws a clear error naming the unsupported type

## Discovery delegation

### discover delegates to the sitemap crawler
- **Given** a website source
- **When** `WebsiteSourceStrategy.discover(src)` runs
- **Then** it returns the `DiscoveredItem`s produced by `SitemapCrawler.discover(src)`

## Fetch outcomes

### Successful fetch produces an INDEX outcome
- **Given** a discovered item whose page returns 200 with extractable content
- **When** `fetch(src, item)` runs
- **Then** the outcome is `INDEX` carrying a fully populated `ContentDocument`

### 304 produces UNCHANGED
- **Given** a discovered item the fetcher reports as `NOT_MODIFIED`
- **When** `fetch(src, item)` runs
- **Then** the outcome is `UNCHANGED` with no document

### 404 produces DELETE
- **Given** a discovered item the fetcher reports as `NOT_FOUND`
- **When** `fetch(src, item)` runs
- **Then** the outcome is `DELETE`

### Extraction failure produces SKIP
- **Given** a page that fetches but yields empty/unusable content
- **When** `fetch(src, item)` runs
- **Then** the outcome is `SKIP` and the failure is logged (the batch continues)

## Extensibility

### New strategy is auto-discovered
- **Given** an additional `ContentSourceStrategy` bean is added to the context (e.g. a future git strategy)
- **When** the registry is built
- **Then** it is indexed by its `type()` with no change to the indexer
