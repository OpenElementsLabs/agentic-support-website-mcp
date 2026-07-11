# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project Context

### Features

The **Open Elements Content MCP** is an MCP server that will crawl, index, and make
searchable the content (primarily blog posts) of Open-Elements-adjacent websites, exposing
that content to AI agents over the standard `/mcp` streamable-HTTP endpoint.

Current state: **project skeleton** (spec `001-project-skeleton`). The app boots and exposes
`/mcp` with no content-specific tools yet. Planned capabilities (see
[`docs/roadmap.md`](docs/roadmap.md)): sitemap crawling, HTTP page fetching, jsoup content
extraction, Meilisearch indexing with scheduled refresh, and four MCP tools
(`search_content`, `list_posts`, `get_post`, `list_categories`).

### Tech Stack

- **Java 21**, **Spring Boot 3.5.14** (via the `com.open-elements:java-parent:1.0.0` Maven parent)
- **`com.open-elements:spring-services:1.3.0-SNAPSHOT`** — provides the reused MCP server and
  Meilisearch building blocks (resolved from the `central-portal-snapshots` repository)
- **Meilisearch** — the search/index backend (via the library's `MeilisearchClient` stack)
- **jsoup** — HTML parsing/content extraction (used from spec 006 on)
- **H2** (embedded, in-memory) — backs only the library's JPA api-key/user tables
- **Maven** — build; the `spring-boot-maven-plugin` produces the executable jar
- **JUnit 5 + AssertJ + Spring Boot Test** — testing

### Structure

```
├── pom.xml                                  # Maven build; parent, spring-services, jsoup, H2
├── src/main/java/com/openelements/content/
│   ├── ContentMcpApplication.java           # entry point; imports library configs, enables scheduling
│   ├── ContentConfig.java                   # @Configuration; enables ContentSourceProperties
│   ├── ContentSource.java / SourceType.java # typed, declarative source model (website|git)
│   ├── ContentSourceProperties.java         # @ConfigurationProperties("open-elements.content")
│   └── UrlMatcher.java                      # Ant-glob include/exclude matching against URL paths
├── src/main/resources/application.yaml      # datasource, JPA, OAuth2, MCP, Meilisearch, content config
├── src/test/java/com/openelements/content/  # behavior tests (context, MCP enabled/disabled, search-down, jsoup)
└── docs/
    ├── content-mcp-technical-design.md      # overall technical design
    ├── roadmap.md                           # implementation roadmap (one step per spec)
    └── specs/                               # per-step specifications (design.md, behaviors.md, steps.md)
```

### Architecture

The app is **standalone** and built on `spring-services` as a library, importing the subset of
building blocks it needs rather than the whole platform:

- `McpConfiguration` — the MCP server and `/mcp` endpoint. It aggregates every `McpToolProvider`
  bean in the context, so content tools added later (spec 012) appear on `/mcp` automatically.
- `SearchConfig` — the Meilisearch stack. Tolerant of an unreachable Meilisearch at startup.
- `SecurityConfig`, `TenantConfig`, `ApiKeyConfig`, `UserConfig` — the api-key/user stack that the
  MCP server transport **structurally requires** (`McpServerConfig` needs `ApiKeyDataService` →
  JPA-backed `ApiKeyRepository` + `UserService`). Because of this coupling, the app also enables JPA
  entity/repository scanning of `com.openelements.spring.base` and supplies an embedded H2 datasource.

All new content code lives under `com.openelements.content`. `ContentConfig` is the wiring point that
later specs extend. Content sources are configured declaratively under `open-elements.content.sources`
(bound to `ContentSourceProperties`) — a new source is one YAML entry, no code. `UrlMatcher` applies
each source's Ant-glob `urlInclude`/`urlExclude` against URL paths; the typed `ContentSource`
(`website`|`git`) keeps the pipeline open to Git sources (spec 016) without reworking the config.

> **Key gotcha:** the library ships no Spring Boot auto-configuration and couples MCP to a JPA
> datasource, so `@Import({ McpConfiguration, SearchConfig })` alone does **not** boot. See
> [`docs/specs/001-project-skeleton/design.md`](docs/specs/001-project-skeleton/design.md) for the
> full explanation.

## Working conventions

- **Spec-driven development:** each roadmap step is a spec under `docs/specs/<id>-<name>/` with
  `design.md`, `behaviors.md`, and (optionally) `steps.md`. Specs are implemented sequentially — each
  builds on the previous. See [`docs/specs/INDEX.md`](docs/specs/INDEX.md) for status.
- **English only** in all spec and code documentation.
- **Build & test:** `mvn clean package` builds and tests; `mvn test` runs tests.
- **Every behavior scenario must be covered by a test** before a spec is considered done.
