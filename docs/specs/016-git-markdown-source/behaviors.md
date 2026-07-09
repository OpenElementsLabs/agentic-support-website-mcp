# Behaviors: Git Markdown Source

## Discovery

### Files discovered via the trees API and filtered by paths
- **Given** a git source with `paths: ["content/posts/**/*.md"]`
- **When** discovery runs against the repo tree
- **Then** only Markdown files under `content/posts/` are returned, each with its blob/commit SHA

### Non-matching files excluded
- **Given** a repo containing `README.md` and `content/posts/x.md`
- **When** discovery runs with the posts glob
- **Then** `README.md` is excluded

## Incremental

### Unchanged file (same SHA) is skipped
- **Given** a file whose SHA equals the stored document `lastmod`
- **When** ingestion runs
- **Then** the file is not re-fetched (unchanged)

### Changed file (new SHA) is re-indexed
- **Given** a file whose SHA changed
- **When** ingestion runs
- **Then** the file is fetched, re-extracted, and upserted with the new SHA as `lastmod`

## Extraction

### Frontmatter becomes metadata
- **Given** a Markdown file with YAML frontmatter (`title`, `date`, `author`, `categories`)
- **When** extracted
- **Then** those values populate the corresponding `ContentDocument` fields and the body excludes the frontmatter block

### Hugo shortcodes are cleaned
- **Given** a body containing `{{< figure ... >}}`
- **When** extracted
- **Then** the shortcode is resolved/removed and does not appear raw in `body`

### Path maps to canonical URL
- **Given** `content/posts/2026-03-12-slug.md` and `base-url: https://open-elements.com`
- **When** extracted
- **Then** the document `url` is the published canonical URL (e.g. `/posts/2026/03/12/slug`)

### Locale from filename suffix
- **Given** a file named `slug.de.md`
- **When** extracted
- **Then** `locale` is `de`

## Security

### Private repo accessed with token
- **Given** a private repo and `token: ${GITHUB_TOKEN_OE_WEBSITE}` set in the environment
- **When** discovery/fetch runs
- **Then** requests carry the bearer token and succeed

### Token is never logged or served
- **Given** a configured token
- **When** logs are inspected and MCP responses are returned
- **Then** the token appears in neither (server-side use only)

### Missing token for private repo fails clearly
- **Given** a private repo with no token configured
- **When** discovery runs
- **Then** the failure is logged clearly (auth error) and the source is skipped without crashing others

## Downstream unchanged

### Git-sourced docs behave like website docs
- **Given** documents indexed from a git source
- **When** the MCP tools query them
- **Then** they appear in `search_content`/`list_posts`/`get_post`/`list_categories` identically to website-sourced docs (filterable by `source`)
