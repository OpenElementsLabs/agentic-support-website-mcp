# Implementation Steps: Operations & Robustness

## Step 1: robots.txt

- [x] `RobotsPolicy` interface (`isAllowed`, `crawlDelay`) with an `ALLOW_ALL` no-op for tests
- [x] `HttpRobotsPolicy` (`@Component`): fetches `robots.txt` per host over HTTPS, caches with a TTL (injectable clock), selects the group for our user agent (falling back to `*`), longest-prefix matching with `Allow` winning ties, missing robots → allow-all, parses `Crawl-delay`
- [x] `SitemapCrawler` filters discovered URLs (sitemap + fallback crawl) through `RobotsPolicy`, logging skips
- [x] `PageFetcher` honors `Crawl-delay` via `HostRateLimiter.acquire(host, minInterval)` (stricter of config vs. crawl-delay)
- [x] Hardening: `SitemapCrawler` only follows child sitemaps on the same host (a sitemap index cannot point the crawler at arbitrary/internal hosts) — closes the SSRF surface flagged in earlier specs

**Related behaviors:** disallowed skipped; allowed fetched; cached per host; missing → allow-all; crawl-delay tightens the rate limit

---

## Step 2: Fault tolerance (audit)

- [x] Verified end-to-end (covered by existing tests): one failing page → `SKIP` (`WebsiteSourceStrategyTest`, `ContentIndexerTest.oneFailingPageIsSkipped`); one failing source isolated (`ContentBootstrapStepTest.failingSourceDoesNotBlockOthers`, `ContentRefreshSchedulerTest.oneSourceFailingDoesNotStopOthers`); idempotent upserts (`ContentIndexerTest`)

**Related behaviors:** failing page isolated; failing source isolated

---

## Step 3: Observability (logging; metrics deferred)

- [x] Structured logging: `IndexReport` per source (`ContentIndexer`/`ContentRefreshScheduler`); robots-skip (`SitemapCrawler`/`HttpRobotsPolicy`); non-success Meilisearch task outcomes logged (`MeilisearchContentIndexStore`)
- [ ] Micrometer metrics — **deferred** per the design's open question: no metrics backend is wired (only `micrometer-observation`/`-commons` are on the classpath, not `micrometer-core`/a `MeterRegistry`). Structured logging is shipped now; add counters/timers when a metrics backend exists.

**Related behaviors:** IndexReport logged per source; failed Meilisearch task surfaced (logging half); metrics behaviors deferred

---

## Step 4: Tests

- [x] `HttpRobotsPolicyTest` (allow/disallow/allow-override/agent-group/cache/missing/crawl-delay via `MockRestServiceServer`)
- [x] `HostRateLimiterTest.crawlDelayTightensInterval`
- [x] `SitemapCrawlerTest.robotsDisallowedUrlsAreDropped`
- [x] Existing crawler/fetcher/strategy tests updated to `RobotsPolicy.ALLOW_ALL`

**Acceptance criteria:**
- [x] All tests pass (`mvn test`); build green

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| Disallowed path is skipped | Backend | Step 4 (`HttpRobotsPolicyTest.disallowedPathIsRejected`, `SitemapCrawlerTest.robotsDisallowedUrlsAreDropped`) |
| Allowed path is fetched | Backend | Step 4 (`HttpRobotsPolicyTest.disallowedPathIsRejected` asserts allowed too) |
| robots.txt is cached per host | Backend | Step 4 (`HttpRobotsPolicyTest.robotsIsCachedPerHost`) |
| Missing robots.txt means allow-all | Backend | Step 4 (`HttpRobotsPolicyTest.missingRobotsAllowsAll`) |
| Crawl-delay tightens the rate limit | Backend | Step 4 (`HttpRobotsPolicyTest.crawlDelayIsParsed` + `HostRateLimiterTest.crawlDelayTightensInterval`) |
| Failing page is isolated | Backend | Step 2 (existing `WebsiteSourceStrategyTest`/`ContentIndexerTest`) |
| Failing source is isolated | Backend | Step 2 (existing `ContentBootstrapStepTest`/`ContentRefreshSchedulerTest`) |
| IndexReport is logged per source | Backend | Step 3 (structured logging in `ContentIndexer`/`ContentRefreshScheduler`) |
| Metrics record fetch and index activity | Backend | **Deferred** (Step 3) — no metrics backend wired; design open question |
| Failed Meilisearch task is surfaced | Backend | Step 3 (logged in `MeilisearchContentIndexStore`; metric part deferred) |

All scenarios are backend; there is no frontend in this spec.
