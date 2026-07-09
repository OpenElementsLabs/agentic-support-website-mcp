# Behaviors: Page Fetcher

## Successful fetch

### 200 returns body and validators
- **Given** a URL that returns 200 with an HTML body, `ETag`, and `Last-Modified`
- **When** `fetch(url, null, null)` runs
- **Then** the result is `OK` with the HTML body and the captured `etag`/`lastModified`

### Bot user-agent is sent
- **Given** the configured user agent
- **When** any fetch runs
- **Then** the request carries `User-Agent: OpenElementsContentBot/1.0 (+https://open-elements.com)`

## Conditional requests

### Unchanged page returns 304
- **Given** a prior `ETag`/`lastmod` for a URL and the server considers it unchanged
- **When** `fetch(url, priorEtag, priorLastmod)` runs
- **Then** the request sends `If-None-Match`/`If-Modified-Since` and the result is `NOT_MODIFIED` with no body

## Limits

### Oversized body is capped
- **Given** a page whose body exceeds `max-body-bytes`
- **When** it is fetched
- **Then** the fetch does not load unbounded memory (body is truncated or the fetch is aborted with a logged warning)

### Request times out
- **Given** a host that does not respond within `request-timeout`
- **When** `fetch` runs
- **Then** the request is aborted and treated as a transient error (eligible for retry)

## Rate limiting

### Requests to the same host are throttled
- **Given** `rate-limit-per-host: 2` and multiple queued URLs on the same host
- **When** they are fetched
- **Then** requests to that host are spaced to not exceed ~2 req/s

### Different hosts are not cross-throttled
- **Given** URLs on two different hosts
- **When** fetched concurrently
- **Then** each host is throttled independently

## Errors and retries

### Transient 5xx is retried then fails
- **Given** a URL returning 503 on every attempt
- **When** `fetch` runs
- **Then** it retries with backoff up to the retry limit and finally returns `ERROR`

### 404 is not retried
- **Given** a URL returning 404
- **When** `fetch` runs
- **Then** the result is `NOT_FOUND` immediately with no retry (signals deletion to the indexer)

### Recovery after transient failure
- **Given** a URL that returns 503 once then 200
- **When** `fetch` runs with retry
- **Then** the second attempt succeeds and returns `OK`
