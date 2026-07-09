# Behaviors: Content Document & Index Settings

## Primary key

### Stable id from source + url
- **Given** a source `open-elements` and url `https://open-elements.com/posts/x`
- **When** `ContentDocument.id(source, url)` is computed twice
- **Then** both calls return the identical id (deterministic)

### Different url yields different id
- **Given** the same source but two different URLs
- **When** ids are computed
- **Then** the two ids differ

### Id is a valid Meilisearch primary key
- **Given** a URL containing `/`, `:` and `.`
- **When** the id is computed
- **Then** the id contains only characters allowed by Meilisearch (`[A-Za-z0-9_-]`)

## Mapping

### toMap contains all indexed fields
- **Given** a fully populated `ContentDocument`
- **When** `toMap()` is called
- **Then** the map contains keys `id, source, locale, url, title, excerpt, body, author, categories, publishedDate, lastmod, previewImage`

### Null optional fields map cleanly
- **Given** a `ContentDocument` with null `author` and `previewImage`
- **When** `toMap()` is called
- **Then** the map is still valid for `addDocuments` (nulls omitted or set to null per Meilisearch tolerance)

## Index settings bean

### Settings bean registered with correct attributes
- **Given** the application context with `openelements.meilisearch.enabled=true`
- **When** the `IndexSettings` bean is inspected
- **Then** `searchableAttributes = [title, excerpt, body]`, `filterableAttributes = [source, locale, author, categories, publishedDate]`, `sortableAttributes = [publishedDate]`, `primaryKey = id`

### Settings applied to the index at startup
- **Given** a reachable Meilisearch instance and the registered `IndexSettings` bean
- **When** the app starts (`MeilisearchIndexSettingsInitializer` runs)
- **Then** the content index exists and reports the configured searchable/filterable/sortable attributes

### Index UID honors prefix
- **Given** `openelements.meilisearch.index-prefix` is set
- **When** `resolveIndex("content")` is used for the index UID
- **Then** the resulting UID includes the configured prefix
