# Technical Design: Open Elements Content MCP

> Status: Concept / design (no code). **Standalone repository** that pulls in
> `spring-services` as a library and reuses its MCP and Meilisearch features.

## 1. Goal & scope

An MCP server that **crawls, indexes, and makes searchable/retrievable** the
content (primarily blog posts) of several Open-Elements-adjacent websites.

Initial sources:

- `open-elements.com` — `/posts` (EN) + `/de/posts` (DE)
- `hiero.org/blog`
- Support & Care page

## 1a. Repo & setup — standalone, built on `spring-services`

The project is its **own repository** (its own Spring Boot application, its own
deployment, its own release cycle) — **not a module inside `spring-services`**.
It is *based on* `spring-services` by pulling it in as a **Maven library** and
importing its ready-made building blocks. `spring-services` is designed exactly
for this: a library whose features are each a `@Configuration` that can be
imported individually or as a whole.

```xml
<!-- pom.xml of the new repo -->
<parent>
  <groupId>com.open-elements</groupId>
  <artifactId>java-parent</artifactId>
  <version>1.0.0</version>
</parent>

<dependency>
  <groupId>com.open-elements</groupId>
  <artifactId>spring-services</artifactId>
  <version>1.3.x</version>
</dependency>
```

```java
@SpringBootApplication
@EnableScheduling
@Import({
    McpConfiguration.class,   // MCP server + /mcp streamable-HTTP endpoint + provider aggregation
    SearchConfig.class        // full Meilisearch stack
    // SecurityConfig.class   // optional, if MCP auth (JWT / API key) is wanted
})
public class ContentMcpApplication { }
```

> `FullSpringServiceConfig` would bring up the **entire** platform; for this
> server the **subset** `McpConfiguration` + `SearchConfig` (+ optional
> `SecurityConfig`) is enough. The new content code (package `…content`, see §3)
> lives in the **new repo** and exposes its beans via component scan — including
> the `McpToolProvider`, which `McpServerConfig` (from the library) picks up
> automatically.

## 2. What is reused from `spring-services` (do not rebuild)

| Building block | Class(es) | Use in the content MCP |
|---|---|---|
| **MCP server infrastructure** | `McpServerConfig` (aggregates all providers), `McpToolProvider` (interface) | A new `ContentMcpToolProvider` implements `toolSpecifications()` → tools appear automatically on the existing `/mcp` endpoint (streamable HTTP). |
| **Tool building & paging** | `McpToolSupport.spec(...)`, `.paginate(...)`, `McpTools.tool/prop/paginationProps/string/integer/bool`, `McpPaging` | Tool definitions and argument parsing in the house style. |
| **Meilisearch stack** | `MeilisearchClient` (`ensureIndex`, `addDocuments`, `deleteDocument`, `updateSettings`, `multiSearch`, scoped keys, `waitForTask`), `BatchWriter`, `IndexSettings`, `MeilisearchIndexSettingsInitializer`, `MeilisearchBootstrapRunner`, `SearchIndexBootstrapStep`, `Highlighter`, `MeilisearchProperties.resolveIndex(...)`, `SearchReadinessState` | The entire search/index layer. We only provide one `IndexSettings` bean + one `SearchIndexBootstrapStep`. |
| **HTTP client** | `RestClient` (as in `webhook`/`dbbackup`) | Fetching sitemaps and pages. |
| **Security / scoped keys** | `MeilisearchScopedKeyInitializer`, `McpSecurityConfig` | Read-only scoped key for the content index; MCP auth as-is. |

**New in the new repo** (not present in `spring-services`): an HTML parser
(**jsoup**), `@EnableScheduling` for periodic re-crawling, plus all of the
content/crawler code from §3. No fork of `spring-services` — dependency + import
only.

## 3. New components (what actually needs to be built)

Own package in the new repo, e.g. `com.openelements.content`:

```
content/
  ContentSource.java            (record: id, TYPE (website|git), baseUrl, sitemapUrls,
                                  urlInclude/urlExclude patterns, contentSelector,
                                  contentExclude, locale derivation, enabled — see §3a)
  ContentSourceProperties.java  (@ConfigurationProperties "open-elements.content")
  SitemapCrawler.java           RestClient → sitemap index + child sitemaps → URL list (+ <lastmod>)
  PageFetcher.java              RestClient GET (ETag/If-Modified-Since, UA, timeouts, retry)
  ContentExtractor.java         jsoup: main content + metadata (title/date/author/excerpt/
                                  categories) → clean text/markdown
  ContentDocument.java          (record: the canonical document, see §4)
  ContentIndexer.java           orchestrates Crawl→Fetch→Extract→BatchWriter (upsert/delete)
  ContentBootstrapStep.java     implements SearchIndexBootstrapStep (initial reindex)
  ContentRefreshScheduler.java  @Scheduled: incremental re-crawl via <lastmod>
  ContentIndexSettings.java     @Bean IndexSettings (searchable/filterable/sortable)
  ContentSearchService.java     facade over MeilisearchClient.multiSearch + Highlighter
  ContentMcpToolProvider.java   implements McpToolProvider → the 4 MCP tools
  ContentConfig.java            @Configuration, wires the beans
```

## 3a. `ContentSource` abstraction (typed, extensible)

`ContentSource` is deliberately modeled as a **typed source**, so that later,
besides websites, **Git repos** can be connected **without rebuilding the core
pipeline** (see §13):

- `type: website` — discovery via sitemap/HTML, extraction via jsoup (the only
  type in P1/P2).
- `type: git` — discovery via Git repo listing, extraction from Markdown files
  (planned, see §13).

Shared interface (conceptual):

```
interface ContentSourceStrategy {
    List<DiscoveredItem> discover(ContentSource src);   // URLs/files + change marker
    ContentDocument fetch(DiscoveredItem item);          // → canonical document (§4)
}
```

One strategy per `type` (`WebsiteSourceStrategy`, later `GitSourceStrategy`).
`ContentIndexer` only knows the interface; the `ContentDocument` flow into the
Meilisearch index stays identical.

## 4. Data model (Meilisearch document)

One index, e.g. `MeilisearchProperties.resolveIndex("content")` →
`content_documents`. Primary key = stable hash of `source + url`.

```json
{
  "id":            "oe:posts/2026/03/12/agentic-wallets…",
  "source":        "open-elements",
  "locale":        "en",
  "url":           "https://open-elements.com/posts/2026/03/12/…",
  "title":         "...",
  "excerpt":       "...",
  "body":          "... full text (Markdown/plaintext) ...",
  "author":        "hendrik",
  "categories":    ["ai", "web3"],
  "publishedDate": "2026-03-12",
  "lastmod":       "2026-03-12T…",
  "previewImage":  "/posts/preview-images/…svg"
}
```

`ContentIndexSettings` (via an `IndexSettings` bean):

- `searchableAttributes`: `["title","excerpt","body"]` (order = ranking weight)
- `filterableAttributes`: `["source","locale","author","categories","publishedDate"]`
- `sortableAttributes`: `["publishedDate"]`
- Default ranking rules + `publishedDate:desc` as tie-breaker; stop words/synonyms optional per language.

## 5. Ingestion pipeline

1. **Discover:** `SitemapCrawler` reads the sitemap index per source
   (`/en/sitemap.xml`, `/de/sitemap.xml` for OE) → child sitemaps → all `<loc>`
   + `<lastmod>`; filters on the source's post URL pattern.
2. **Diff:** compare `lastmod` against the value stored in the index → only fetch
   new/changed URLs; URLs that disappeared → `deleteDocument`.
3. **Fetch:** `PageFetcher` GET with UA
   `OpenElementsContentBot/1.0 (+https://open-elements.com)`,
   `If-Modified-Since`/ETag, timeout, rate limit (e.g. ≤2 req/s/host), retry with backoff.
4. **Extract:** `ContentExtractor` (jsoup) pulls the main container (per source
   selector) and metadata. Robust for OE since the markup is known; for external
   sites a readability-style heuristic + selector override.
5. **Index:** `ContentIndexer` maps to `ContentDocument` → `BatchWriter`
   (`addDocuments` in batches, `waitForTask`).
6. **Bootstrap vs. refresh:** initial build via `ContentBootstrapStep` (runs in
   `MeilisearchBootstrapRunner`, sets `SearchReadinessState`); afterwards
   `ContentRefreshScheduler` (`@Scheduled`, e.g. hourly) incrementally.

> **Fidelity option for OE:** since we own the website repo, the OE source could
> optionally pull the body from the raw Markdown (`content/posts/*.md`) instead
> of rendered HTML — higher quality. That is exactly the Git source type from
> **§13**; for external sites HTML scraping remains.

## 6. Source configuration (declarative, pluggable)

`application.yaml` → `open-elements.content.sources`. Another site = one new list
entry, **no code**.

```yaml
open-elements:
  content:
    refresh-cron: "0 0 * * * *"
    sources:
      - id: open-elements
        type: website
        base-url: https://open-elements.com
        sitemaps: [ /en/sitemap.xml, /de/sitemap.xml ]
        url-include: [ "/posts/**", "/de/posts/**" ]
        content-selector: "article"
      - id: hiero
        type: website
        base-url: https://hiero.org
        sitemaps: [ /sitemap.xml ]
        url-include: [ "/blog/**" ]
        content-selector: "main article"
      - id: support-and-care          # plain-HTML example: everything except boilerplate
        type: website
        base-url: https://open-elements.com
        url-include: [ "/support-care", "/support-care/**" ]
        content-selector: "body"
        content-exclude: [ "nav", "header", "footer", ".cookie-banner", "aside" ]
```

### `url-include` / `url-exclude` — pattern language

Both are **lists of Ant-style glob patterns** (Spring's `AntPathMatcher`, already
present in the framework). Matching is done against the **path part** of the URL
(without scheme/host, leading `/`). A URL is included if it matches **at least
one `url-include` pattern** **and no** `url-exclude` pattern.

Tokens:

| Token | Meaning | Example | Matches |
|---|---|---|---|
| `?` | exactly one character (not `/`) | `/post?` | `/posts` (not `/post`) |
| `*` | any number of characters **within one segment** (not `/`) | `/posts/*` | `/posts/hello`, **not** `/posts/2026/03/x` |
| `**` | any number of segments (across `/`) | `/posts/**` | `/posts/2026/03/12/slug` |

Recipes:

- **Crawl everything:** `url-include: [ "/**" ]` (every page of the sitemap/host).
- **Blog only:** `[ "/posts/**", "/de/posts/**" ]`.
- **A single page + subpages:** `[ "/support-care", "/support-care/**" ]`
  (the pattern `/x/**` matches `/x/...`, **not** `/x` itself — hence both).
- **Exclusions:** e.g. `url-exclude: [ "/**/tag/**", "/**/*.pdf" ]`.

If `url-include` is missing, the default `[ "/**" ]` (everything) applies.
`sitemaps` narrows the candidate set further; without a sitemap, `base-url` is
used as the entry point and internal links are followed within the `url-include`
scope (bounded crawl depth).

### `content-selector` — what it is and why

A **CSS selector (jsoup)** that identifies the DOM element wrapping the **actual
article text**. Purpose: **remove boilerplate** — navigation, header, footer,
cookie banner, sidebars, "related posts" — so that only the relevant content ends
up in `body` (and thus in the search index).

- Syntax = normal CSS selectors: tag (`article`), class (`.post-content`), ID
  (`#content`), nesting (`main article`), fallback lists (`article, main, .prose`).
- **How to find it:** open the page in a browser → DevTools → find the element
  that wraps exactly the body text (heading + paragraphs) and take its
  tag/class/ID. For our own Next.js site this is usually `article`.
- **Multiple candidates:** a comma-separated list is tried in order; the first
  match wins.
- **"Take everything" (plain HTML, just a few `<div>`s, no clear container):**
  `content-selector: "body"` — jsoup selects the entire `<body>`. This is the
  equivalent of "match all" for `url-include`. `<script>`, `<style>`,
  `<noscript>`, `<template>` and HTML comments are **always** removed by the
  `ContentExtractor` (even for `body`); text is whitespace-normalized. For the
  little remaining boilerplate → `content-exclude` (next point).
- **`content-exclude` (the counterpart):** an optional **list of CSS selectors of
  elements removed before text extraction** — the clean way to say "take the whole
  `body`, but without these parts". Example:
  `content-exclude: [ "nav", "header", "footer", ".cookie-banner", "aside", ".related-posts" ]`.
  Works together with any `content-selector` (including `body`).
- **Fallback:** if `content-selector` is missing or none matches, the
  `ContentExtractor` uses a **readability-style heuristic** (largest contiguous
  text block). The explicit selector produces more stable results, though, and is
  recommended per external site.
- **Metadata** (title, date, author, categories, excerpt) is read **independently
  of `content-selector`**, preferably from structured sources: `<meta>` tags
  (OpenGraph/Article), JSON-LD, `<time datetime>` — with our frontmatter knowledge
  as a supplement for our own site.

## 7. MCP tools (on the existing `/mcp`)

All defined via `McpTools.tool(...)`, in
`ContentMcpToolProvider.toolSpecifications()`:

| Tool | Parameters | Behavior |
|---|---|---|
| **`search_content`** | `query` (req), `locale?`, `source?`, `category?`, `limit?`/paging | `MeilisearchClient.multiSearch` with filters; result: title, URL, date, **highlighted snippet** (`Highlighter`), score. |
| **`list_posts`** | `locale?`, `source?`, `category?`, `since?`, `page?`, `size?` | Filtered list sorted by `publishedDate:desc` (Meilisearch with empty query + filter, or `McpToolSupport.paginate`). |
| **`get_post`** | `url` **or** `id` (req) | Full text (Markdown) + all metadata of one document. |
| **`list_categories`** | `source?`, `locale?` | Categories + hit counts (Meilisearch facets over `categories`). |

Paging/argument parsing via `McpPaging` / `McpTools` — identical to the existing
tools.

## 8. Search with Meilisearch — details

- **DE/EN:** one index, language as a filter; Meilisearch detects the language
  automatically, with optional per-language synonyms/stop words. (Alternative: two
  indexes `content_en`/`content_de` — only needed if separate ranking-tuning
  requirements arise; start with one index + `locale` filter.)
- **Ranking:** searchable order `title > excerpt > body`; `publishedDate:desc` as
  tie-breaker.
- **Highlighting/snippets:** `Highlighter.safeHighlight(...)` on `_formatted`
  fields → clean preview in the tool result.
- **Security:** a dedicated **read-only scoped key** for the content index only
  (`MeilisearchScopedKeyInitializer` / `createScopedKey`); writing only through
  the indexer.

## 9. New dependencies

- `org.jsoup:jsoup` — HTML parsing/content extraction.
- (None needed for Markdown output if the body is stored as cleaned text; if
  HTML→Markdown is wanted: `com.vladsch.flexmark:flexmark-html2md-converter`.)
- Spring `@EnableScheduling` (available via Boot, just enable it).

## 10. Configuration (`@ConfigurationProperties`)

`open-elements.content.*`: source list (§6), `refresh-cron`, `user-agent`,
`rate-limit-per-host`, `request-timeout`, `max-body-bytes`, `enabled`.
Meilisearch properties already exist (`MeilisearchProperties`).

## 11. Operations & robustness

- Respect **robots.txt** per host (OE allows `/`).
- **Fault tolerance:** a single failing page only skips that document; bootstrap
  marks readiness only after completion.
- **Idempotency:** upsert by stable `id`; deletions from the sitemap diff.
- **Observability:** logging/metrics analogous to `BatchWriter` (documents pushed,
  task status).

## 12. Phases

The repo is **standalone from the start** (no later extraction needed).

1. **P1:** OE source `type: website` (EN+DE) end-to-end — Crawl→Index→4 tools.
2. **P2:** `hiero.org/blog` + Support & Care as config sources; selector fine-tuning.
3. **P3:** **Git/GitHub Markdown sources** (`type: git`, incl. private repos via
   access token) — see §13.
4. **P4 (optional):** facets/synonyms, semantic search (embeddings) as an add-on.

## 13. Future extension: Markdown from Git repos (incl. private GitHub repos)

> **Planned, not part of P1/P2.** Deliberately considered here so the architecture
> (§3a: typed `ContentSource` + `ContentSourceStrategy`) can absorb it without
> rebuilding the core pipeline.

**Goal:** index Markdown files directly from Git repositories — public **and
private** GitHub repos (via access token) — e.g. docs, whitepapers, the website's
`content/posts/*.md` in raw form (higher quality than HTML scraping).

**New source type `type: git`** with its own `GitSourceStrategy`; everything after
that (mapping to `ContentDocument` §4 → Meilisearch index → the same 4 MCP tools)
stays unchanged.

Configuration schema (extension of §6):

```yaml
      - id: oe-website-markdown
        type: git
        provider: github               # github (API/raw) | generic-git (clone)
        repo: OpenElements/open-elements-website
        ref: main                       # branch/tag/commit
        paths: [ "content/posts/**/*.md", "content/posts/**/*.mdx" ]
        base-url: https://open-elements.com   # to derive the canonical URL
        token: ${GITHUB_TOKEN_OE_WEBSITE}     # only for private repos; see below
```

**Discovery & fetch:**

- **GitHub provider:** via the GitHub REST/GraphQL API or `raw.githubusercontent.com`.
  File listing via the Git Trees API
  (`GET /repos/{owner}/{repo}/git/trees/{ref}?recursive=1`), filtered via `paths`
  (same Ant-glob semantics as §6). Contents via the contents/raw endpoint.
  Reusable: Spring's `RestClient` (as in the existing `webhook`/`dbbackup` code).
- **Generic-git (optional):** shallow clone (JGit) for non-GitHub hosts.

**Incremental:** the change marker is the **commit SHA per file** (or the tree
SHA); only changed files are re-fetched — analogous to the `<lastmod>` diff for
websites. The SHA goes into the document as the `lastmod` equivalent.

**Extraction:** YAML **frontmatter** → metadata (`title`, `date`, `author`,
`excerpt`, `categories`) — the format already exists in `content/posts/*.md`. The
Markdown body is taken directly; Hugo-style shortcodes (`{{< … >}}`) are
cleaned/resolved.

**URL mapping:** file path → canonical website URL via a per-source rule (e.g.
`content/posts/2026-03-12-slug.md` + `base-url` → `/posts/2026/03/12/slug`), so
`get_post`/search results link to the published page. Locale derivation via the
filename suffix (`*.de.md`) as in the website repo.

**Secrets/token handling:**

- Tokens **only** from the environment/secret store (`${GITHUB_TOKEN_*}`), **never**
  in the YAML in plaintext; never logged. One dedicated **read-only** fine-grained
  GitHub token per source (fine-grained PAT / GitHub App installation token) with
  access limited to the required repos only.
- The token is used server-side in the indexer only; over MCP only finished,
  indexed content is served — the token never leaves the server.

**New dependencies (only when §13 is implemented):** GitHub via `RestClient` (no
new lib needed) or optionally `org.kohsuke:github-api`; for generic-git
`org.eclipse.jgit:org.eclipse.jgit`. Markdown frontmatter/parsing:
`commonmark`/`flexmark` (+ YAML via the existing Jackson).

## Open questions

- Does `hiero.org` have a `sitemap.xml` with `/blog/` entries? (check briefly
  before P2 — otherwise crawl the paginated blog index page.)
- Support & Care: a single landing page or multiple docs pages? (determines
  `url-include`.)
- One index + `locale` filter (recommended) vs. two per-language indexes.

## Referenced existing classes in `spring-services`

`com.openelements.spring.base.mcp`: `McpServerConfig`, `McpToolProvider`,
`McpToolSupport`, `McpTools`, `McpPaging`.
`com.openelements.spring.base.services.search`: `MeilisearchClient`,
`BatchWriter`, `IndexSettings`, `MeilisearchIndexSettingsInitializer`,
`MeilisearchBootstrapRunner`, `SearchIndexBootstrapStep`, `Highlighter`,
`MeilisearchProperties`, `MeilisearchScopedKeyInitializer`, `SearchReadinessState`.
