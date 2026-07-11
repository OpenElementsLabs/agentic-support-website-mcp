# Design: Project Skeleton

## Summary

Create the standalone Spring Boot application that is the Open Elements Content MCP.
The app is its **own repository/deployment**, built on `spring-services` pulled in as a
Maven library. It imports the library's ready-made MCP and Meilisearch configuration —
plus the security/api-key/user stack the MCP server structurally depends on — and adds an
empty `com.openelements.content` package with a `ContentConfig` `@Configuration` that
later specs fill in. Goal of this step: the app boots and exposes the existing `/mcp`
streamable-HTTP endpoint with zero content-specific tools yet.

> **Implementation note (during spec 001).** The minimal `@Import({ McpConfiguration,
> SearchConfig })` from the original design does **not** boot against `spring-services`
> 1.3.0-SNAPSHOT: the library pulls in `spring-boot-starter-data-jpa` non-optionally, and
> its MCP server (`McpServerConfig`/`McpSecurityConfig`) structurally requires an
> `ApiKeyDataService` → JPA-backed `ApiKeyRepository` + `UserService`. The design below is
> updated to the wiring that actually boots (verified by running the app and the test
> suite): additional library configs are imported, JPA entity/repository scanning is pointed
> at `com.openelements.spring.base`, and an embedded H2 datasource is provided.

## GitHub Issue

— (derived from `docs/roadmap.md`, Phase 1 step 1; see `docs/content-mcp-technical-design.md` §1a, §2, §9)

## Goals

- A buildable, bootable Spring Boot application (`ContentMcpApplication`).
- Maven setup: `com.open-elements:java-parent:1.0.0` as parent, `com.open-elements:spring-services:1.3.0-SNAPSHOT` as dependency. Java 21 (enforced by the parent).
- Import `McpConfiguration` (MCP server + `/mcp` endpoint), `SearchConfig` (full Meilisearch stack), and the api-key/user stack the MCP server requires (`SecurityConfig`, `TenantConfig`, `ApiKeyConfig`, `UserConfig`).
- Point JPA entity + repository scanning at `com.openelements.spring.base` (the library's entities/repositories) and `com.openelements.content`.
- Provide an embedded H2 datasource so the library's JPA-backed api-key/user tables have a store.
- `@EnableScheduling` enabled (needed later by `ContentRefreshScheduler`).
- `org.jsoup:jsoup` added as a dependency (used later by `ContentExtractor`).
- Empty `com.openelements.content` package with a `ContentConfig` `@Configuration` that is component-scanned.
- `application.yaml` scaffold with the datasource, Meilisearch connection, OAuth2 resource-server placeholder, and an `open-elements.content` section placeholder.

## Non-goals

- No crawler, indexer, search service, or MCP tools yet (later specs).
- No `IndexSettings`/`SearchIndexBootstrapStep` bean yet (spec 003 / 009).
- Not a module inside `spring-services` — this is a separate repo (no fork).
- No real MCP/JWT authentication wiring — the `/mcp` endpoint is left secured by the library defaults (api-key auth disabled in dev, JWT resource-server pointed at a placeholder). Proper scoped keys and auth are spec 013.
- No production database — H2 in-memory backs only the library's api-key/user tables. The content pipeline itself keeps no relational state (its store is Meilisearch).

## Technical approach

`spring-services` is designed as a library whose features are each a `@Configuration`
that can be imported individually. This app imports the **subset** it needs rather than
`FullSpringServiceConfig` — but that subset is larger than MCP + search alone, because the
MCP server transitively depends on the api-key/user stack:

```java
@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = {"com.openelements.spring.base", "com.openelements.content"})
@EnableJpaRepositories(basePackages = {"com.openelements.spring.base", "com.openelements.content"})
@Import({
    SecurityConfig.class,   // web security + AuthService + JSON auth entry point
    TenantConfig.class,     // tenant beans referenced by the user/security stack
    ApiKeyConfig.class,     // ApiKeyDataService + ApiKeyRepository (needed by the MCP transport)
    UserConfig.class,       // UserService (needed by ApiKeyDataService)
    SearchConfig.class,     // full Meilisearch stack
    McpConfiguration.class  // MCP server + /mcp streamable-HTTP endpoint + provider aggregation
})
public class ContentMcpApplication {
    public static void main(String[] args) { SpringApplication.run(ContentMcpApplication.class, args); }
}
```

> **Verified by running `spring-services` 1.3.0-SNAPSHOT (boot + test suite):**
> - `McpConfiguration` binds `McpProperties` and imports `McpPaging`, `McpToolSupport`,
>   `McpSecurityConfig`, `McpServerConfig`. The MCP endpoint/server beans are gated by
>   `openelements.mcp.enabled=true` (`@ConditionalOnProperty`).
> - `McpServerConfig.mcpTransportProvider(...)` **requires an `ApiKeyDataService`** to build the
>   `/mcp` transport (it authenticates the api-key on each request). `ApiKeyDataService` needs
>   `ApiKeyRepository` (JPA) + `UserService`; `UserService` needs `AuthService` (from
>   `SecurityConfig`). Hence `SecurityConfig`, `TenantConfig`, `ApiKeyConfig`, `UserConfig` are
>   all imported. The library ships **no** Spring Boot auto-configuration, so none of this is
>   wired implicitly, and `FullSpringServiceConfig` does not include MCP.
> - The library's entities extend a JPA `AbstractEntity` in `com.openelements.spring.base` and its
>   repositories live there too, so `@EntityScan`/`@EnableJpaRepositories` must point there (Boot's
>   default scan of the main class package `com.openelements.content` would miss them).
> - Because `spring-boot-starter-data-jpa` is a non-optional dependency of the library, a datasource
>   is mandatory; the skeleton supplies embedded H2 (see Configuration).
> - `SecurityConfig`'s default filter chain wires an OAuth2 resource server, so a `JwtDecoder` must
>   exist; a `jwk-set-uri` provides a lazily-initialized decoder with no startup network call.
> - `McpServerConfig` aggregates all `McpToolProvider` beans found in the context, so any
>   `McpToolProvider` declared in `com.openelements.content` appears automatically on `/mcp`
>   (used by spec 012).
> - `McpSecurityConfig` enables `X-API-Key` auth on `/mcp/**` by default
>   (`openelements.mcp.auth.api-key.enabled` defaults to `true`). Setting it to `false` in dev
>   **skips the MCP api-key filter chain**, which means `/mcp` then falls under the library's
>   default (JWT) chain (`anyRequest().authenticated()`) — it does **not** become an open,
>   unauthenticated endpoint. Real auth (scoped keys) is spec 013.

The new content code lives in package `com.openelements.content` and is exposed via
component scan. `ContentConfig` is an empty `@Configuration` placeholder that later specs
extend with `@Bean` definitions and `@ComponentScan`-discovered collaborators.

### Rationale

- **Standalone from the start** (design §12): its own release cycle and deployment; no later extraction needed.
- **Import the required subset, not `FullSpringServiceConfig`**: the content MCP needs MCP + search + the api-key/user stack that the MCP transport depends on, but not email/slack/webhook/dbbackup/etc. — keeping the surface as small as the library allows.
- **Embedded H2, not a real DB**: the content pipeline stores everything in Meilisearch; the only relational tables come from the library's api-key/user stack, which in-memory H2 satisfies for boot. A persistent DB can be configured later if that data must survive restarts.
- **jsoup added now** even though unused until spec 006, so the dependency graph is stable early.

## Dependencies

- `com.open-elements:java-parent:1.0.0` (Maven parent — provides plugin/dependency management, Java version).
- `com.open-elements:spring-services:1.3.x` (MCP + Meilisearch building blocks). Resolved from the `central-portal-snapshots` repository (`https://central.sonatype.com/repository/maven-snapshots/`), declared in `pom.xml` so CI can fetch the SNAPSHOT.
- `org.jsoup:jsoup` (HTML parsing — used from spec 006 on).
- `com.h2database:h2` (runtime — embedded datasource for the library's api-key/user tables).
- Spring Boot (transitively via parent/spring-services), `spring-boot-starter` `@EnableScheduling`.
- A Meilisearch instance for a fully functional local run (connection via `MeilisearchProperties`, already provided by the library) — optional for boot: the app tolerates an unreachable Meilisearch at startup.

## Configuration

`application.yaml` scaffold. Note the library uses the `openelements.*` prefix (no hyphen);
our own content properties use `open-elements.content` per design doc §10 (a distinct prefix
under Spring relaxed binding — kept as the design specifies).

```yaml
spring:
  application:
    name: content-mcp
  datasource:
    # Backs only the library's api-key/user tables; the content pipeline uses Meilisearch.
    url: ${SPRING_DATASOURCE_URL:jdbc:h2:mem:content-mcp;DB_CLOSE_DELAY=-1;MODE=PostgreSQL}
    username: ${SPRING_DATASOURCE_USERNAME:sa}
    password: ${SPRING_DATASOURCE_PASSWORD:}
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
  security:
    oauth2:
      resourceserver:
        jwt:
          # Lazily-initialized JwtDecoder for the library's default (non-MCP) filter chain.
          # No network call at startup; point at the real IdP in production.
          jwk-set-uri: ${OAUTH2_JWK_SET_URI:http://localhost:9999/.well-known/jwks.json}

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
> initialize — that is expected for this step. If Meilisearch is unreachable, the library's
> `MeilisearchBootstrapRunner` logs a warning and skips the bootstrap; the HTTP listener and `/mcp`
> still come up, so a live Meilisearch is not required for the app to boot.

## Open questions

- Whether to run bootstrap asynchronously: `SearchConfig` supports async reindex only if the
  app supplies `@EnableAsync` + a `searchIndexExecutor` bean; otherwise bootstrap runs
  synchronously at startup. For a small content index, synchronous is acceptable — revisit in spec 009.
- Minor prefix inconsistency: library is `openelements.*`, our content props are
  `open-elements.content` (design §10). Confirm this is intentional or align to `openelements.content`.
- **Library ergonomics (surfaced during implementation):** `McpConfiguration` cannot boot on its
  own — it drags in the full JPA-backed api-key/user/security stack. Consider a follow-up in
  `spring-services` to make MCP self-contained (bundle its api-key store, or let the transport
  tolerate an absent `ApiKeyDataService`) so downstream apps can import MCP without a database. Not
  addressed here to keep spec 001 scoped to this repo.
- **Persistence of api-key/user data:** in-memory H2 resets on restart. If the library's api-key/user
  data ever needs to persist for this app, configure a real datasource (the config already supports
  overriding via `SPRING_DATASOURCE_*`). Revisit alongside spec 013 (auth).
