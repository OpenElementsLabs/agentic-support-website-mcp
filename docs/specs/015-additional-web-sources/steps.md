# Implementation Steps: Additional Website Sources

## Step 1: Resolve open questions (live checks)

- [x] **hiero.org sitemap:** `https://hiero.org/sitemap.xml` exists — a flat `<urlset>` with 66 `<loc>`s, 62 of them `/blog/<post>`. So `/sitemap.xml` + `url-include: [/blog/**]` covers all posts; the no-sitemap fallback crawl is not needed.
- [x] **hiero.org robots.txt:** served as a Next.js HTML catch-all (no `User-agent`/`Disallow` rules) → treated as allow-all by `HttpRobotsPolicy`.
- [x] **hiero post markup:** posts are wrapped in `<main><article>` → selector `main article`. No OpenGraph/Article meta, so `title` falls back to `<title>`/`<h1>` and `publishedDate` may be null (metadata is limited for hiero).
- [x] **Support & Care:** two pages — `/en/support-care` and `/en/support-care-maven` — listed in `/en/sitemap.xml` (under `/en/`, not `/support-care` as the design assumed). OE `robots.txt` is `Allow: /`.

## Step 2: Configuration (no code)

- [x] Add `hiero` source: `base-url https://hiero.org`, `sitemaps [/sitemap.xml]`, `url-include [/blog/**]`, `content-selector "main article"`
- [x] Add `support-and-care` source: `base-url https://open-elements.com`, `sitemaps [/en/sitemap.xml]`, `url-include ["/en/support-care*", "/en/support-care/**"]` (matches both current pages), `content-selector "body"` + `content-exclude [nav, header, footer, .cookie-banner, aside]`

## Step 3: Tests

- [x] `ConfiguredSourcesTest` — the three sources bind from `application.yaml` with the expected type/URLs/selectors (no code)

**Acceptance criteria:**
- [x] All tests pass (`mvn test`); build green

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| New source needs no code | Backend | Step 3 (`ConfiguredSourcesTest.allSourcesAreConfigured`) |
| Blog posts are indexed (/blog/** only) | Backend | Step 3 (hiero `urlInclude` `[/blog/**]`); live index verified manually |
| Sitemap-absent fallback | Backend | N/A for hiero — the sitemap exists and lists posts (finding); generic fallback is covered by spec 004 |
| Clean extraction with the article selector | Backend | Step 1 finding (`main article`); live extraction verified manually |
| robots.txt honored for hiero.org | Backend | Covered by spec 014 (`HttpRobotsPolicy`); hiero has no rules → allow-all (finding) |
| Body-selector with excludes yields clean text | Backend | Step 2 config; live extraction verified manually |
| URL scope matches single vs. multi page | Backend | Step 3 (`supportAndCareSourceIsConfigured`) — multi-page `/en/support-care*` |
| Source filter isolates results | Backend | Covered by spec 011 (`ContentSearchServiceTest` source filter) |

Config-only spec: binding is unit-tested; live crawl/extraction against the third-party sites is verified manually against a dev index (not run in CI, which must not hit external sites).
