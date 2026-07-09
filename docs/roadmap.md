# Roadmap — Open Elements Content MCP

Derived from [`content-mcp-technical-design.md`](./content-mcp-technical-design.md).
Each top-level checkbox is one implementation step. Steps are processed
top-to-bottom by the `roadmap-execute` skill and build on each other, so the
order matters. Section references (§) point into the design document.

## Phase 1 — OE website source, end-to-end (Crawl → Index → 4 tools)

- [ ] Project skeleton — standalone Spring Boot app on top of `spring-services`
  - Own repo/app: `java-parent` as Maven parent, `spring-services` (1.3.x) as dependency (§1a).
  - `ContentMcpApplication` with `@SpringBootApplication`, `@EnableScheduling`, and `@Import({ McpConfiguration.class, SearchConfig.class })` (§1a).
  - Add `org.jsoup:jsoup` dependency (§9). No fork of `spring-services` — dependency + import only (§2).
  - `application.yaml` scaffolding; empty `com.openelements.content` package with a `ContentConfig` `@Configuration` (§3).
  - Verify the app boots and the existing `/mcp` endpoint is exposed by the imported config.

- [ ] `ContentSource` abstraction + configuration properties
  - `ContentSource` record: `id`, `type` (`website`|`git`), `baseUrl`, `sitemaps`, `urlInclude`/`urlExclude`, `contentSelector`, `contentExclude`, locale derivation, `enabled` (§3, §3a).
  - `ContentSourceProperties` as `@ConfigurationProperties("open-elements.content")` incl. `refreshCron`, `userAgent`, `rateLimitPerHost`, `requestTimeout`, `maxBodyBytes`, `enabled` (§6, §10).
  - Ant-glob matching for `urlInclude`/`urlExclude` against the URL path via Spring `AntPathMatcher`; default include `["/**"]` (§6 pattern language).
  - Bind the example OE source from §6 in `application.yaml`.

- [ ] `ContentDocument` data model + Meilisearch index settings
  - `ContentDocument` record matching the canonical JSON: `id`, `source`, `locale`, `url`, `title`, `excerpt`, `body`, `author`, `categories`, `publishedDate`, `lastmod`, `previewImage` (§4).
  - Stable primary key = hash of `source + url` (§4).
  - `ContentIndexSettings` `@Bean IndexSettings`: searchable `["title","excerpt","body"]`, filterable `["source","locale","author","categories","publishedDate"]`, sortable `["publishedDate"]`, `publishedDate:desc` tie-breaker (§4, §8).
  - Index name via `MeilisearchProperties.resolveIndex("content")` (§4).

- [ ] `SitemapCrawler` — URL discovery
  - `RestClient`-based reading of the sitemap index + child sitemaps → all `<loc>` + `<lastmod>` (§5.1).
  - Filter candidates against the source's `urlInclude`/`urlExclude` patterns (§5.1, §6).
  - Fallback when no sitemap: use `baseUrl` as entry point, follow internal links within `urlInclude` scope with bounded depth (§6).

- [ ] `PageFetcher` — robust HTTP fetching
  - `RestClient` GET with UA `OpenElementsContentBot/1.0 (+https://open-elements.com)`, `If-Modified-Since`/ETag, timeout, `maxBodyBytes` (§5.3, §10).
  - Rate limit per host (≤2 req/s), retry with backoff (§5.3).
  - Handle 304 Not Modified as "unchanged" (§5.3).

- [ ] `ContentExtractor` — jsoup content + metadata extraction
  - Select main container via `contentSelector` (comma list, first match wins); always strip `<script>/<style>/<noscript>/<template>`/comments; apply `contentExclude` (§6 `content-selector`).
  - Whitespace-normalize to clean text/markdown for `body`; readability-style largest-text-block fallback when no selector matches (§5.4, §6).
  - Metadata independent of `contentSelector`: `<meta>` (OpenGraph/Article), JSON-LD, `<time datetime>` → title/date/author/categories/excerpt (§6 metadata).

- [ ] `ContentSourceStrategy` interface + `WebsiteSourceStrategy`
  - `ContentSourceStrategy` with `discover(src) → List<DiscoveredItem>` and `fetch(item) → ContentDocument` (§3a).
  - `WebsiteSourceStrategy` wiring `SitemapCrawler` + `PageFetcher` + `ContentExtractor`; one strategy per `type`, selected for `type: website` (§3a).

- [ ] `ContentIndexer` — orchestration, diff & upsert/delete
  - Drive Discover → Diff → Fetch → Extract → Index through the strategy interface (§3a, §5).
  - Diff `lastmod` against the value stored in the index: fetch only new/changed URLs; delete documents whose URLs disappeared from the sitemap (§5.2).
  - Map to `ContentDocument` and write via `BatchWriter` (`addDocuments` batched, `waitForTask`); idempotent upsert by stable `id` (§5.5, §11).

- [ ] `ContentBootstrapStep` — initial reindex
  - Implement `SearchIndexBootstrapStep`; runs inside `MeilisearchBootstrapRunner`, sets `SearchReadinessState` only after completion (§5.6, §11).
  - A single failing page skips only that document; bootstrap still completes (§11 fault tolerance).

- [ ] `ContentRefreshScheduler` — incremental re-crawl
  - `@Scheduled` (cron `refresh-cron`, default hourly) incremental re-crawl via the `lastmod` diff, reusing `ContentIndexer` (§5.6, §6).

- [ ] `ContentSearchService` — search facade
  - Facade over `MeilisearchClient.multiSearch` with filters (`source`, `locale`, `categories`, date) and `Highlighter.safeHighlight` on `_formatted` snippets (§8).
  - Ranking: searchable order `title > excerpt > body`, `publishedDate:desc` tie-breaker (§8).

- [ ] `ContentMcpToolProvider` — the 4 MCP tools
  - Implement `McpToolProvider.toolSpecifications()` so tools appear automatically on `/mcp` (§2, §7).
  - `search_content` (`query` req, `locale?`/`source?`/`category?`, paging) → title, URL, date, highlighted snippet, score (§7).
  - `list_posts` (`locale?`/`source?`/`category?`/`since?`/paging) sorted `publishedDate:desc` (§7).
  - `get_post` (`url` or `id`) → full body + metadata; `list_categories` (`source?`/`locale?`) via facets (§7).
  - Argument parsing/paging via `McpTools`/`McpPaging`/`McpToolSupport` (§7).

- [ ] Read-only scoped Meilisearch key for the content index
  - Dedicated read-only scoped key limited to the content index via `MeilisearchScopedKeyInitializer`/`createScopedKey`; writes only through the indexer (§8 security).

- [ ] Operations & robustness hardening
  - Respect `robots.txt` per host (§11).
  - Observability: logging/metrics analogous to `BatchWriter` (documents pushed, task status) (§11).

## Phase 2 — Additional website sources

- [ ] Add `hiero.org/blog` and Support & Care as config-only sources
  - New `application.yaml` source entries for `hiero` (`/blog/**`, selector `main article`) and `support-and-care` (`content-selector: body` + `content-exclude`) — no new code (§6, §12 P2).
  - Selector fine-tuning per site; verify extraction quality; resolve the §"Open questions" (hiero sitemap presence, Support & Care page count) before finalizing.

## Phase 3 — Git / GitHub Markdown sources

- [ ] `type: git` source with `GitSourceStrategy`
  - Extend the config schema with `type: git` (`provider`, `repo`, `ref`, `paths`, `base-url`, `token`) (§13).
  - `GitSourceStrategy`: GitHub Trees API discovery (`git/trees/{ref}?recursive=1`) filtered by `paths`, contents via raw endpoint using `RestClient` (§13 discovery & fetch).
  - Incremental via per-file commit SHA as the `lastmod` equivalent (§13 incremental).
  - Extraction: YAML frontmatter → metadata, Markdown body direct, resolve/clean Hugo shortcodes; URL mapping file path → canonical URL; locale from filename suffix (§13 extraction & URL mapping).
  - Secrets: token only from env/secret store (`${GITHUB_TOKEN_*}`), never logged, server-side only, read-only fine-grained scope (§13 secrets).
  - Everything downstream (`ContentDocument` → index → 4 tools) stays unchanged (§13).

## Phase 4 — Optional enhancements

- [ ] Facets, synonyms, and semantic search add-on
  - Per-language synonyms/stop words; category facets surfacing (§8, §12 P4).
  - Optional embeddings-based semantic search as an add-on (§12 P4).
