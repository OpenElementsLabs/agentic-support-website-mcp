# Behaviors: Content Extractor

## Body extraction

### Selector picks the article container
- **Given** `content-selector: "article"` and HTML with a nav, header, an `<article>`, and a footer
- **When** `extract` runs
- **Then** the `body` contains the article text and none of the nav/header/footer text

### Comma-separated fallback list, first match wins
- **Given** `content-selector: "article, main, .prose"` and HTML with only a `<main>`
- **When** `extract` runs
- **Then** the `<main>` content is selected

### body selector takes the whole document
- **Given** `content-selector: "body"` with a few top-level `<div>`s
- **When** `extract` runs
- **Then** the whole body text is taken (minus always-stripped nodes)

### Scripts and styles are always removed
- **Given** any `content-selector` (including `body`) and HTML containing `<script>` and `<style>`
- **When** `extract` runs
- **Then** no script or style text appears in `body`

### contentExclude removes boilerplate
- **Given** `content-selector: "body"` and `content-exclude: ["nav","header","footer",".cookie-banner"]`
- **When** `extract` runs
- **Then** the excluded elements' text is absent from `body`

### Whitespace is normalized
- **Given** HTML with irregular whitespace and line breaks
- **When** `extract` runs
- **Then** the `body` has collapsed whitespace and trimmed edges while preserving paragraph separation

### Readability fallback when no selector matches
- **Given** no `content-selector` (or a selector that matches nothing) on an article page
- **When** `extract` runs
- **Then** the largest contiguous text block is used as `body`

## Metadata extraction

### OpenGraph metadata is read
- **Given** HTML with `og:title`, `og:description`, `og:image`, `article:published_time`
- **When** `extract` runs
- **Then** `title`, `excerpt`, `previewImage`, and `publishedDate` are populated from those tags

### JSON-LD fallback for date/author
- **Given** HTML with no `article:*` meta but a JSON-LD block containing `datePublished` and `author.name`
- **When** `extract` runs
- **Then** `publishedDate` and `author` are populated from JSON-LD

### Categories from article tags
- **Given** HTML with multiple `article:tag` meta entries
- **When** `extract` runs
- **Then** `categories` contains all tag values

### Locale derived from path
- **Given** a URL under `/de/posts/...`
- **When** `extract` runs
- **Then** `locale` is `de`

### Metadata read regardless of content selector
- **Given** `content-selector` matching only a small inner element
- **When** `extract` runs
- **Then** metadata is still read from the document `<head>` (independent of the content container)

## Edge cases

### Missing metadata degrades gracefully
- **Given** HTML with no description meta and no JSON-LD
- **When** `extract` runs
- **Then** `excerpt`/`author`/`previewImage` are null (or excerpt falls back to a body snippet) without error

### Empty content container
- **Given** a `content-selector` that matches an empty element
- **When** `extract` runs
- **Then** the readability fallback is attempted, and if still empty, `body` is empty and the page can be skipped by the indexer
