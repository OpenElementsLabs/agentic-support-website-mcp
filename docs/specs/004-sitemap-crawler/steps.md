# Implementation Steps: Sitemap Crawler

## Step 1: `DiscoveredItem` record

- [x] Record `DiscoveredItem(String url, String lastmod)` (lastmod nullable)

**Acceptance criteria:**
- [x] Project builds

**Related behaviors:** Missing lastmod is tolerated

---

## Step 2: `SitemapCrawler`

- [x] `@Component` taking `RestClient.Builder` (builds its own client) + `UrlMatcher`
- [x] Parse `<urlset>` entries (loc + optional lastmod) with jsoup XML parser
- [x] Follow `<sitemapindex>` children recursively (visited-set + depth guard against self-reference)
- [x] Filter every discovered URL through `UrlMatcher`; dedupe preserving order
- [x] Fault tolerance: unreachable or malformed sitemap is logged and skipped, never aborts the source
- [x] No-sitemap fallback: bounded, same-host link-following crawl from `baseUrl` (depth + page caps, visited-set)

**Acceptance criteria:**
- [x] Project builds; behaviors verified by tests

**Related behaviors:** all sitemap parsing, filtering, fallback, and error scenarios

---

## Step 3: Tests

- [x] `SitemapCrawlerTest` using `MockRestServiceServer` (no real network): urlset, index recursion, missing lastmod, include/exclude filtering, unreachable-sitemap tolerance, malformed-XML tolerance, bounded fallback crawl, cycle termination

**Acceptance criteria:**
- [x] All tests pass (`mvn test`)

**Related behaviors:** all

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| Flat urlset is collected | Backend | Step 3 (`SitemapCrawlerTest.flatUrlsetIsCollected`) |
| Sitemap index is followed | Backend | Step 3 (`SitemapCrawlerTest.sitemapIndexIsFollowed`) |
| Missing lastmod is tolerated | Backend | Step 3 (`SitemapCrawlerTest.missingLastmodIsTolerated`) |
| Only included URLs are returned | Backend | Step 3 (`SitemapCrawlerTest.onlyIncludedUrlsAreReturned`) |
| Excluded URLs are dropped | Backend | Step 3 (`SitemapCrawlerTest.excludedUrlsAreDropped`) |
| No sitemap triggers bounded crawl | Backend | Step 3 (`SitemapCrawlerTest.noSitemapTriggersBoundedCrawl`) |
| Crawl terminates on cycles | Backend | Step 3 (`SitemapCrawlerTest.fallbackCrawlTerminatesOnCycles`) |
| Unreachable sitemap | Backend | Step 3 (`SitemapCrawlerTest.unreachableSitemapDoesNotAbortDiscovery`) |
| Malformed XML | Backend | Step 3 (`SitemapCrawlerTest.malformedXmlContributesNothing`) |

All scenarios are backend; there is no frontend in this spec.
