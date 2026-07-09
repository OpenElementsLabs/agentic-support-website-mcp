# Behaviors: Content Bootstrap Step

## Registration

### Step is discovered by the runner
- **Given** the `ContentBootstrapStep` bean and a reachable Meilisearch instance
- **When** the app starts
- **Then** `MeilisearchBootstrapRunner` discovers the step and invokes `documents()`

### indexUid targets the content index
- **Given** the configured index prefix
- **When** `indexUid()` is called
- **Then** it returns `resolveIndex("content")`, matching the `IndexSettings` bean (spec 003)

## Document streaming

### All enabled sources contribute documents
- **Given** two enabled sources and one disabled source
- **When** `documents()` is consumed
- **Then** documents from both enabled sources are streamed and none from the disabled source

### Stream is lazy
- **Given** a large source
- **When** `documents()` is consumed by the runner in batches
- **Then** documents are produced lazily (not all materialized in memory at once)

## Readiness

### Readiness flips after bootstrap
- **Given** the app starting with content to index
- **When** bootstrap begins and completes
- **Then** `SearchReadinessState.isBootstrapping()` is true during, and false after completion

### Search short-circuits during bootstrap
- **Given** bootstrap still running
- **When** a search is attempted (spec 011)
- **Then** the search layer can observe `isBootstrapping() == true` and behave accordingly (e.g. degrade)

## Fault tolerance

### A failing source does not block others
- **Given** two sources where one throws while streaming
- **When** bootstrap runs
- **Then** the other source's documents are still indexed and the failure is logged (per-step try/catch in the runner)
