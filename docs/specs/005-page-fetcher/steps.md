# Implementation Steps: Page Fetcher

## Step 1: `FetchResult`

- [x] Record `FetchResult(Status, html, etag, lastModified, httpStatus)` with nested `Status {OK, NOT_MODIFIED, NOT_FOUND, ERROR}` and small factory helpers

**Related behaviors:** all (return shape)

---

## Step 2: `HostRateLimiter`

- [x] Per-host request spacing with injectable clock (`LongSupplier`) and sleeper (`LongConsumer`) for deterministic tests
- [x] Independent buckets per host; non-positive rate disables throttling

**Related behaviors:** Requests to the same host are throttled; Different hosts are not cross-throttled

---

## Step 3: `PageFetcher`

- [x] `@Component`; per-request bot `User-Agent`; conditional `If-None-Match`/`If-Modified-Since` headers
- [x] `.exchange(...)` callback classifies status without throwing: 304 → NOT_MODIFIED, 404/410 → NOT_FOUND, 2xx → OK, else ERROR
- [x] Body read under `max-body-bytes` cap via `readNBytes` (bounded memory); oversized → aborted ERROR with a logged warning
- [x] Retry transient failures (5xx, network/timeout) with exponential backoff (injectable sleeper); 404 and other 4xx not retried
- [x] Per-host rate limiting via `HostRateLimiter`

**Related behaviors:** 200 body+validators; bot UA; 304; oversized; timeout transient; 5xx retried then ERROR; 404 no retry; recovery

---

## Step 4: Wiring

- [x] `ContentHttpConfig` provides the timeout-configured `contentRestClient` and the production `HostRateLimiter` (kept separate from `ContentConfig` so non-HTTP config stays isolated)

**Related behaviors:** n/a (wiring)

---

## Step 5: Tests

- [x] `PageFetcherTest` via `MockRestServiceServer` (no network): 200, UA header, conditional/304, oversized, 404 no-retry, persistent 5xx, transient network error, 503→200 recovery — with a no-op rate limiter and recorded backoffs
- [x] `HostRateLimiterTest` with a virtual clock + recording sleeper: same-host spacing, per-host independence, disabled rate

**Acceptance criteria:**
- [x] All tests pass (`mvn test`); build green

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| 200 returns body and validators | Backend | Step 5 (`PageFetcherTest.success200ReturnsBodyAndValidators`) |
| Bot user-agent is sent | Backend | Step 5 (`PageFetcherTest.botUserAgentIsSent`) |
| Unchanged page returns 304 | Backend | Step 5 (`PageFetcherTest.conditionalRequestYieldsNotModified`) |
| Oversized body is capped | Backend | Step 5 (`PageFetcherTest.oversizedBodyIsAborted`) |
| Request times out | Backend | Step 5 (`PageFetcherTest.transientNetworkErrorIsRetried`) |
| Requests to the same host are throttled | Backend | Step 5 (`HostRateLimiterTest.sameHostIsThrottled`) |
| Different hosts are not cross-throttled | Backend | Step 5 (`HostRateLimiterTest.differentHostsAreIndependent`) |
| Transient 5xx is retried then fails | Backend | Step 5 (`PageFetcherTest.persistent5xxIsRetriedThenFails`) |
| 404 is not retried | Backend | Step 5 (`PageFetcherTest.notFoundIsNotRetried`) |
| Recovery after transient failure | Backend | Step 5 (`PageFetcherTest.recoversAfterTransientFailure`) |

All scenarios are backend; there is no frontend in this spec.
