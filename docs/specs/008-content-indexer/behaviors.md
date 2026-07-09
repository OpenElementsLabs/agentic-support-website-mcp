# Behaviors: Content Indexer

## Full pass

### New pages are upserted
- **Given** a source whose index is empty and discovery returns 3 in-scope URLs
- **When** `indexSource(src)` runs
- **Then** all 3 pages are fetched, extracted, and upserted; `IndexReport.upserted == 3`

### Unchanged pages are skipped
- **Given** 3 indexed documents whose discovered `lastmod` equals the stored `lastmod`
- **When** `indexSource(src)` runs
- **Then** no fetch/upsert happens for them and `IndexReport.unchanged == 3`

### Changed page is re-fetched and upserted
- **Given** an indexed document whose discovered `lastmod` is newer than the stored value
- **When** `indexSource(src)` runs
- **Then** the page is fetched and upserted (same `id`, updated content)

### Conditional GET short-circuits a lastmod false-positive
- **Given** a page whose `lastmod` changed but whose content is byte-identical (server returns 304)
- **When** `indexSource(src)` runs
- **Then** the outcome is `UNCHANGED` and no upsert occurs

## Deletions

### Vanished URL is deleted
- **Given** an indexed document whose URL is no longer present in discovery
- **When** `indexSource(src)` runs
- **Then** the document is deleted from the index and `IndexReport.deleted` reflects it

### 404 on fetch deletes the document
- **Given** a discovered URL that now returns 404
- **When** `indexSource(src)` runs
- **Then** the corresponding document (by stable id) is deleted

## Idempotency & fault tolerance

### Re-running is idempotent
- **Given** a source that was fully indexed
- **When** `indexSource(src)` runs again with no changes
- **Then** the index is unchanged and only `unchanged` counts increase

### One failing page does not abort the batch
- **Given** 5 discovered pages where 1 fails extraction
- **When** `indexSource(src)` runs
- **Then** the other 4 are indexed, the failure is logged, and `IndexReport.skipped == 1`

### Stable id prevents duplicates
- **Given** the same page indexed twice
- **When** compared
- **Then** only one document exists (upsert by `source + url` hash)

## Streaming for bootstrap

### streamAllDocuments yields every in-scope document
- **Given** a source with N in-scope pages
- **When** `streamAllDocuments(src)` is consumed
- **Then** it lazily yields N document maps suitable for `BatchWriter` / the bootstrap step
