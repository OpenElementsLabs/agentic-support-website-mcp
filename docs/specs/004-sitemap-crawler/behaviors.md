# Behaviors: Sitemap Crawler

## Sitemap parsing

### Flat urlset is collected
- **Given** a source whose sitemap is a `<urlset>` with three `<url><loc>` entries
- **When** `discover(src)` runs
- **Then** three `DiscoveredItem`s are returned with their URLs and `lastmod` values

### Sitemap index is followed
- **Given** a sitemap that is a `<sitemapindex>` pointing to two child sitemaps
- **When** `discover(src)` runs
- **Then** the crawler recurses into both children and returns the union of their `<loc>` entries

### Missing lastmod is tolerated
- **Given** a `<url>` entry without `<lastmod>`
- **When** it is collected
- **Then** the `DiscoveredItem.lastmod` is null (and the fetch stage will always fetch it)

## Filtering

### Only included URLs are returned
- **Given** `url-include: ["/posts/**"]` and a sitemap containing `/posts/a` and `/about`
- **When** `discover(src)` runs
- **Then** only `/posts/a` is returned

### Excluded URLs are dropped
- **Given** `url-exclude: ["/**/tag/**"]` and a sitemap containing `/posts/tag/ai`
- **When** `discover(src)` runs
- **Then** `/posts/tag/ai` is not returned

## Fallback crawl

### No sitemap triggers bounded crawl
- **Given** a source with empty `sitemaps` and a `baseUrl` page linking to in-scope pages
- **When** `discover(src)` runs
- **Then** in-scope linked pages are discovered up to the depth bound, and out-of-host links are ignored

### Crawl terminates on cycles
- **Given** pages that link to each other cyclically
- **When** the fallback crawl runs
- **Then** the visited-set prevents infinite looping and the crawl terminates

## Error cases

### Unreachable sitemap
- **Given** a sitemap URL that returns 404 or times out
- **When** `discover(src)` runs
- **Then** the error is logged and discovery returns the URLs from the other sitemaps (one failing sitemap does not abort the whole source)

### Malformed XML
- **Given** a sitemap with malformed XML
- **When** parsing fails
- **Then** the failure is logged and that sitemap contributes no URLs (no exception propagates to the caller)
