# Design: Project Skeleton

## Summary

Create the standalone Spring Boot application that is the Open Elements Content MCP.
The app is its **own repository/deployment**, built on `spring-services` pulled in as a
Maven library. It imports the library's ready-made MCP and Meilisearch configuration
and adds an empty `com.openelements.content` package with a `ContentConfig`
`@Configuration` that later specs fill in. Goal of this step: the app boots and exposes
the existing `/mcp` streamable-HTTP endpoint with zero content-specific tools yet.

## GitHub Issue

— (derived from `docs/roadmap.md`, Phase 1 step 1; see `docs/content-mcp-technical-design.md` §1a, §2, §9)

## Goals

- A buildable, bootable Spring Boot application (`ContentMcpApplication`).
- Maven setup: `com.open-elements:java-parent:1.0.0` as parent, `com.open-elements:spring-services:1.3.0-SNAPSHOT` as dependency. Java 21 (enforced by the parent).
- Import `McpConfiguration` (MCP server + `/mcp` endpoint) and `SearchConfig` (full Meilisearch stack) from the library.
- `@EnableScheduling` enabled (needed later by `ContentRefreshScheduler`).
- `org.jsoup:jsoup` added as a dependency (used later by `ContentExtractor`).
- Empty `com.openelements.content` package with a `ContentConfig` `@Configuration` that is component-scanned.
- `application.yaml` scaffold with the Meilisearch connection and an `open-elements.content` section placeholder.

## Non-goals

- No crawler, indexer, search service, or MCP tools yet (later specs).
- No `IndexSettings`/`SearchIndexBootstrapStep` bean yet (spec 003 / 009).
- Not a module inside `spring-services` — this is a separate repo (no fork).

## Technical approach

`spring-services` is designed as a library whose features are each a `@Configuration`
that can be imported individually. This app imports the **subset** it needs rather than
`FullSpringServiceConfig`:

```java
@SpringBootApplication
@EnableScheduling
@Import({
    McpConfiguration.class,   // MCP server + /mcp streamable-HTTP endpoint + provider aggregation
    SearchConfig.class        // full Meilisearch stack
    // SecurityConfig.class   // optional, added in spec 013 if MCP auth is wanted
})
public class ContentMcpApplication {
    public static void main(String[] args) { SpringApplication.run(ContentMcpApplication.class, args); }
}
```

> **Verified against `spring-services` 1.3.0-SNAPSHOT:**
> - `com.openelements.spring.base.mcp.McpConfiguration` is the correct import. It binds
>   `McpProperties` and imports `McpPaging`, `McpToolSupport`, `McpSecurityConfig`,
>   `McpServerConfig`. The MCP endpoint/server beans are gated by `openelements.mcp.enabled=true`.
> - `com.openelements.spring.base.services.search.SearchConfig` is the correct import. It
>   component-scans the search package (`MeilisearchClient`, the startup runners,
>   `SearchReadinessState`) and binds `MeilisearchProperties`. Gated by
>   `openelements.meilisearch.enabled=true`.
> - `McpServerConfig` aggregates all `McpToolProvider` beans found in the context, so any
>   `McpToolProvider` declared in `com.openelements.content` appears automatically on `/mcp`
>   (used by spec 012).
> - **Importing `McpConfiguration` also imports `McpSecurityConfig`, which enables
>   `X-API-Key` auth on `/mcp/**` by default** (`openelements.mcp.auth.api-key.enabled`
>   defaults to `true`). To let the app boot and be probed locally without a key in this
>   first step, set `openelements.mcp.auth.api-key.enabled: false` in the dev profile. Real
>   auth is handled in spec 013.

The new content code lives in package `com.openelements.content` and is exposed via
component scan. `ContentConfig` is an empty `@Configuration` placeholder that later specs
extend with `@Bean` definitions and `@ComponentScan`-discovered collaborators.

### Rationale

- **Standalone from the start** (design §12): its own release cycle and deployment; no later extraction needed.
- **Import subset, not `FullSpringServiceConfig`**: the content MCP only needs MCP + search, keeping the surface small.
- **jsoup added now** even though unused until spec 006, so the dependency graph is stable early.

## Dependencies

- `com.open-elements:java-parent:1.0.0` (Maven parent — provides plugin/dependency management, Java version).
- `com.open-elements:spring-services:1.3.x` (MCP + Meilisearch building blocks).
- `org.jsoup:jsoup` (HTML parsing — used from spec 006 on).
- Spring Boot (transitively via parent/spring-services), `spring-boot-starter` `@EnableScheduling`.
- A running Meilisearch instance for local run (connection via `MeilisearchProperties`, already provided by the library).

## Configuration

`application.yaml` scaffold. Note the library uses the `openelements.*` prefix (no hyphen);
our own content properties use `open-elements.content` per design doc §10 (a distinct prefix
under Spring relaxed binding — kept as the design specifies).

```yaml
spring:
  application:
    name: content-mcp

openelements:
  mcp:
    enabled: true
    server-name: "Open Elements Content MCP"
    server-version: "0.1.0"
    auth:
      api-key:
        enabled: false        # dev only; real auth handled in spec 013
  meilisearch:
    enabled: true
    host: ${MEILISEARCH_HOST:http://localhost:7700}
    master-key: ${MEILISEARCH_MASTER_KEY:}
    index-prefix: ${MEILISEARCH_INDEX_PREFIX:content_}

open-elements:
  content:
    enabled: true
    # sources: []   # filled in by spec 002
```

> With `openelements.meilisearch.enabled=true` but **no `IndexSettings` / `SearchIndexBootstrapStep`
> beans yet** (those arrive in specs 003 / 009), the search stack starts up with nothing to
> initialize — that is expected for this step. The app must still be able to reach a
> Meilisearch instance for the search runners to complete cleanly.

## Open questions

- Whether to run bootstrap asynchronously: `SearchConfig` supports async reindex only if the
  app supplies `@EnableAsync` + a `searchIndexExecutor` bean; otherwise bootstrap runs
  synchronously at startup. For a small content index, synchronous is acceptable — revisit in spec 009.
- Minor prefix inconsistency: library is `openelements.*`, our content props are
  `open-elements.content` (design §10). Confirm this is intentional or align to `openelements.content`.
