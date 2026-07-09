# Behaviors: Additional Website Sources

## Configuration only

### New source needs no code
- **Given** a new `hiero` source added purely in `application.yaml`
- **When** the app restarts
- **Then** the source is crawled and indexed with no code change

## hiero.org/blog

### Blog posts are indexed
- **Given** the `hiero` source with `url-include: ["/blog/**"]`
- **When** ingestion runs
- **Then** blog posts under `/blog/` are indexed and non-blog pages are excluded

### Sitemap-absent fallback
- **Given** hiero.org has no usable sitemap for `/blog/`
- **When** ingestion runs
- **Then** the bounded fallback crawl from the blog index discovers the posts

### Clean extraction with the article selector
- **Given** `content-selector: "main article"`
- **When** a hiero post is extracted
- **Then** the `body` contains the post text without site chrome

### robots.txt honored for hiero.org
- **Given** hiero.org's `robots.txt`
- **When** ingestion runs
- **Then** disallowed paths are skipped

## Support & Care

### Body-selector with excludes yields clean text
- **Given** `content-selector: "body"` and `content-exclude` for nav/header/footer/cookie-banner
- **When** the Support & Care page is extracted
- **Then** the `body` contains only the meaningful content, no boilerplate

### URL scope matches single vs. multi page
- **Given** the resolved page count
- **When** `url-include` is set accordingly (`/support-care` and/or `/support-care/**`)
- **Then** exactly the intended pages are indexed

## Cross-source

### Source filter isolates results
- **Given** OE, hiero, and Support & Care all indexed
- **When** `search_content`/`list_posts` is filtered by `source`
- **Then** only that source's documents are returned
