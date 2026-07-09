# Behaviors: Operations & Robustness

## robots.txt

### Disallowed path is skipped
- **Given** a host whose `robots.txt` disallows `/private/` for our user agent
- **When** discovery/fetch encounters `/private/page`
- **Then** the URL is skipped and a log line records the robots-skip

### Allowed path is fetched
- **Given** a host that allows `/`
- **When** an in-scope URL is processed
- **Then** it is fetched normally

### robots.txt is cached per host
- **Given** many URLs on the same host
- **When** they are processed
- **Then** `robots.txt` is fetched once (cached with TTL), not per URL

### Missing robots.txt means allow-all
- **Given** a host returning 404 for `/robots.txt`
- **When** URLs are processed
- **Then** all in-scope URLs are treated as allowed

### Crawl-delay tightens the rate limit
- **Given** a `robots.txt` with `Crawl-delay: 5`
- **When** fetching that host
- **Then** the effective per-host interval is at least 5s (stricter of config vs. crawl-delay)

## Fault tolerance

### Failing page is isolated
- **Given** one page that throws during fetch/extract
- **When** the source is indexed
- **Then** the failure is logged and counted, and all other pages still index

### Failing source is isolated
- **Given** one source that fails entirely
- **When** bootstrap or a scheduled refresh runs
- **Then** other sources still complete

## Observability

### IndexReport is logged per source
- **Given** a completed source run
- **When** it finishes
- **Then** a structured log line records discovered/upserted/unchanged/deleted/skipped counts

### Metrics record fetch and index activity
- **Given** metrics are enabled
- **When** crawling/indexing runs
- **Then** counters/timers for pages fetched/skipped, docs upserted/deleted, refresh duration, and Meilisearch task outcomes are updated

### Failed Meilisearch task is surfaced
- **Given** a Meilisearch task returns `FAILED` or `TIMED_OUT`
- **When** the batch write completes
- **Then** it is logged as a failure and reflected in the task-outcome metric (the run continues)
