# Design: Operations & Robustness

## Summary

Harden the crawler for production operation: honor each host's `robots.txt`, ensure the
ingestion pipeline is fault-tolerant end-to-end, and add observability (logging/metrics) around
crawling and indexing analogous to the library `BatchWriter`. This step adds cross-cutting
robustness rather than new features, closing out Phase 1.

## GitHub Issue

— (roadmap Phase 1 step 14; design doc §11)

## Goals

- Fetch and cache `robots.txt` per host; skip URLs disallowed for our user agent.
- Verify/complete fault tolerance: a single failing page/source never aborts the run.
- Observability: structured logs and metrics for documents pushed, task status, pages fetched/skipped/failed, refresh duration.

## Non-goals

- No new sources or tools.
- No distributed crawl coordination (single-instance assumption).

## Technical approach

### robots.txt (design §11)

```java
@Component
public class RobotsPolicy {
    boolean isAllowed(String host, String path);   // fetches & caches robots.txt per host
}
```

- On first request to a host, GET `/robots.txt`, parse the `User-agent`/`Disallow`/`Allow` rules relevant to `OpenElementsContentBot` (and `*`), cache with a TTL.
- `SitemapCrawler` (004) and `PageFetcher` (005) consult `RobotsPolicy` before requesting a path; disallowed paths are skipped and logged.
- OE allows `/`; the policy mainly matters for external hosts (hiero.org, spec 015).
- Honor `Crawl-delay` if present by feeding it into the per-host rate limiter (spec 005), taking the stricter of configured rate vs. crawl-delay.

### Fault tolerance (verify + fill gaps)

Audit the whole pipeline against design §11:
- One failing page → `SKIP` (spec 007/008) — verify logged, counted, non-fatal.
- One failing source in bootstrap/refresh → isolated (library runner try/catch; scheduler loop try/catch) — verify.
- Bootstrap marks readiness only after completion (spec 009) — verify.
- Idempotent upserts (spec 008) — verify re-run safety.

### Observability

- **Logging:** structured lines per source run — discovered/upserted/unchanged/deleted/skipped (the `IndexReport`), plus per-fetch warnings (404/timeout/robots-skip).
- **Metrics** (Micrometer if available via the parent/Boot): counters/timers for `content.pages.fetched`, `content.pages.skipped`, `content.docs.upserted`, `content.docs.deleted`, `content.refresh.duration`, and Meilisearch task outcomes (`SUCCEEDED`/`FAILED`/`TIMED_OUT` from `TaskOutcome`). Mirror the granularity `BatchWriter` already logs.
- Optionally expose crawl health via an actuator health indicator that reflects `SearchReadinessState` and last-refresh success.

### Rationale

- **robots.txt** is table stakes for polite external crawling and required before P2 adds third-party hosts (design §11, §15).
- **Explicit fault-tolerance audit** ensures the guarantees claimed across specs 007–010 actually hold together.
- **Metrics mirroring `BatchWriter`** gives ops the same visibility the rest of the platform has.

## Dependencies

- `SitemapCrawler` (004), `PageFetcher` (005), `ContentIndexer` (008), `ContentRefreshScheduler` (010), `SearchReadinessState`/`TaskOutcome` (spring-services), Micrometer (via Boot, if present).

## Open questions

- robots.txt parser: hand-rolled minimal parser vs. a small library (e.g. crawler-commons). Prefer minimal in-house parsing to avoid a heavy dependency unless coverage is insufficient.
- Metrics backend availability (is Micrometer/Prometheus wired in the deployment?). If not, ship structured logging first and add metrics when the backend exists.
