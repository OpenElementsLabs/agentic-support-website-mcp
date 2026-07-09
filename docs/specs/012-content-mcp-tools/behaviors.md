# Behaviors: Content MCP Tools

## Tool registration

### Four tools appear on /mcp
- **Given** the `ContentMcpToolProvider` bean and the running MCP server
- **When** an MCP client lists tools
- **Then** `search_content`, `list_posts`, `get_post`, and `list_categories` are present with their input schemas

## search_content

### Query returns highlighted hits
- **Given** indexed content
- **When** `search_content` is called with `query: "wallet"`
- **Then** it returns matching hits with title, url, publishedDate, highlighted snippet, and score

### Missing required query is rejected
- **Given** a call with no `query`
- **When** `search_content` runs
- **Then** it returns a JSON-RPC invalid-argument error (from `requiredString`)

### Filters are passed through
- **Given** `query: "x", locale: "de", source: "open-elements"`
- **When** `search_content` runs
- **Then** only `de` posts from `open-elements` matching are returned

### Paging is honored and clamped
- **Given** `size` larger than the configured max page size
- **When** `search_content` runs
- **Then** the size is clamped to the max (via `McpPaging`)

## list_posts

### Lists newest first
- **Given** several posts
- **When** `list_posts` runs with no filters
- **Then** posts are returned sorted `publishedDate` descending, paged

### since filter applied
- **Given** `since: "2026-01-01"`
- **When** `list_posts` runs
- **Then** only posts on/after that date are returned

## get_post

### Get by url returns full post
- **Given** an indexed post url
- **When** `get_post` is called with `url`
- **Then** the full body and all metadata are returned

### Get by id returns full post
- **Given** an indexed post id
- **When** `get_post` is called with `id`
- **Then** the full document is returned

### Neither url nor id is an error
- **Given** a call with neither `url` nor `id`
- **When** `get_post` runs
- **Then** a JSON-RPC invalid-argument error is returned

### Unknown post is not-found
- **Given** a url/id that does not exist
- **When** `get_post` runs
- **Then** a JSON-RPC not-found error is returned (from `NoSuchElementException`)

## list_categories

### Returns categories with counts
- **Given** posts across several categories
- **When** `list_categories` runs
- **Then** it returns each category with its hit count

### Scoped by source/locale
- **Given** `source: "hiero"`
- **When** `list_categories` runs
- **Then** counts reflect only `hiero` posts

## Availability

### Tool reports unavailable during bootstrap
- **Given** `SearchReadinessState.isBootstrapping()` is true
- **When** `search_content` or `list_posts` is called
- **Then** a temporary-unavailable JSON-RPC error is returned (from `McpUnavailableException`)

## Logging

### Each tool call is access-logged
- **Given** any tool invocation
- **When** it runs through `McpToolSupport.spec`
- **Then** one structured access-log line is emitted (`tool=… actor=…`)
