# Behaviors: Content Search Service

## Search

### Query returns relevant hits with snippet
- **Given** an index with posts about "agentic wallets"
- **When** `search("agentic wallets", empty filters, page 0, size 10)` runs
- **Then** matching posts are returned with title, url, publishedDate, a highlighted snippet, and a score

### Title matches rank above body matches
- **Given** two posts, one with the term in the title and one only in the body
- **When** searched for that term
- **Then** the title-match ranks higher (searchable order `title > excerpt > body`)

### Highlight markers become safe em tags
- **Given** a hit whose `_formatted` field carries the library boundary markers
- **When** the snippet is produced via `Highlighter.safeHighlight`
- **Then** the snippet contains `<em>…</em>` around matches and any raw HTML in the text is escaped

## Filters

### Locale filter restricts results
- **Given** posts in `en` and `de`
- **When** `search(query, locale="de", …)` runs
- **Then** only `de` posts are returned

### Source filter restricts results
- **Given** posts from `open-elements` and `hiero`
- **When** filtered by `source="hiero"`
- **Then** only `hiero` posts are returned

### Category filter restricts results
- **Given** posts tagged `ai` and `web3`
- **When** filtered by `category="ai"`
- **Then** only posts tagged `ai` are returned

### since filters by published date
- **Given** posts from 2025 and 2026
- **When** `since="2026-01-01"` is applied
- **Then** only posts published on/after that date are returned

### Combined filters are AND-combined
- **Given** filters `source=open-elements` and `locale=en` and `category=ai`
- **When** searched
- **Then** only posts matching all three are returned

## List

### listPosts sorts by published date descending
- **Given** several posts with different `publishedDate`s
- **When** `listPosts(empty filters, page 0, size 20)` runs
- **Then** results are ordered newest-first

### Empty query returns all (filtered) posts
- **Given** no query string
- **When** `listPosts` runs with a `source` filter
- **Then** all posts of that source are returned, paged

## Get

### Get by url returns full document
- **Given** an indexed post at a known url
- **When** `getByUrlOrId(url, null)` runs
- **Then** the full `ContentDocument` (including `body`) is returned

### Get by id returns full document
- **Given** an indexed post with a known id
- **When** `getByUrlOrId(null, id)` runs
- **Then** the full document is returned

### Get for unknown url/id is empty
- **Given** a url/id not in the index
- **When** `getByUrlOrId` runs
- **Then** an empty `Optional` is returned

## Facets

### Category facets return counts
- **Given** posts across categories `ai` (3) and `web3` (2)
- **When** `categoryFacets(null, null)` runs
- **Then** it returns `ai→3` and `web3→2`

### Facets respect source/locale filter
- **Given** a `source` filter
- **When** `categoryFacets(source, null)` runs
- **Then** counts reflect only that source's posts

## Edge cases

### No results
- **Given** a query matching nothing
- **When** `search` runs
- **Then** an empty hit list with `estimatedTotal == 0` is returned (no error)

### Paging beyond the end
- **Given** 5 total hits and `page=10, size=10`
- **When** `search` runs
- **Then** an empty page is returned without error
