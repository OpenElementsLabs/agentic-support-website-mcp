# Behaviors: Content Refresh Scheduler

## Scheduled execution

### Refresh runs each enabled source on tick
- **Given** two enabled sources and a cron tick
- **When** `refresh()` fires
- **Then** `ContentIndexer.indexSource` is called once per enabled source

### Disabled source is skipped
- **Given** one source with `enabled: false`
- **When** `refresh()` fires
- **Then** that source is not indexed

### Cron is externally configurable
- **Given** a custom `open-elements.content.refresh-cron`
- **When** the app runs
- **Then** the scheduler fires on that cron expression (default hourly when unset)

## Guards

### Skips while bootstrapping
- **Given** `SearchReadinessState.isBootstrapping()` is true
- **When** a cron tick fires
- **Then** `refresh()` returns immediately without indexing

### Skips when globally disabled
- **Given** `open-elements.content.enabled: false`
- **When** a cron tick fires
- **Then** `refresh()` does nothing

### Non-overlapping runs
- **Given** a refresh still running when the next tick fires
- **When** the next tick occurs
- **Then** it is skipped (no overlapping concurrent refresh of the same sources)

## Change propagation

### New page appears after refresh
- **Given** a new post published since the last run
- **When** the next refresh fires
- **Then** the post is discovered, fetched, and added to the index

### Deleted page removed after refresh
- **Given** a post removed from the site (gone from sitemap / now 404)
- **When** the next refresh fires
- **Then** its document is deleted from the index

## Error handling

### One source failing does not stop the others
- **Given** two sources where the first throws during indexing
- **When** `refresh()` fires
- **Then** the failure is logged and the second source is still refreshed
