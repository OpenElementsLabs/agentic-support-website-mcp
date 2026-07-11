# Implementation Steps: Project Skeleton

> Note: the original design proposed a minimal `@Import({ McpConfiguration, SearchConfig })`
> with no database. That does not boot against `spring-services` 1.3.0-SNAPSHOT (the MCP server
> requires the JPA-backed api-key/user stack, and `spring-boot-starter-data-jpa` is a non-optional
> transitive dependency). The steps below reflect the wiring that actually boots, verified by running
> the app and the test suite. See `design.md` → *Technical approach* for the full rationale.

## Step 1: Maven project setup

- [x] Create `pom.xml` with `com.open-elements:java-parent:1.0.0` as parent
- [x] Add `com.open-elements:spring-services:1.3.0-SNAPSHOT` dependency
- [x] Add `org.jsoup:jsoup` dependency
- [x] Add `com.h2database:h2` (runtime) — datasource for the library's api-key/user tables
- [x] Add `spring-boot-starter-test` (test scope)
- [x] Declare the `central-portal-snapshots` repository so the SNAPSHOT resolves in CI
- [x] Configure the `spring-boot-maven-plugin` (`repackage`) for an executable jar

**Acceptance criteria:**
- [x] `mvn -DskipTests package` produces an executable jar
- [x] Project builds successfully

**Related behaviors:** jsoup is on the classpath

---

## Step 2: Application entry point and content configuration

- [x] Create `ContentMcpApplication` with `@SpringBootApplication`, `@EnableScheduling`
- [x] `@Import` the required library configs: `SecurityConfig`, `TenantConfig`, `ApiKeyConfig`, `UserConfig`, `SearchConfig`, `McpConfiguration`
- [x] Point `@EntityScan`/`@EnableJpaRepositories` at `com.openelements.spring.base` and `com.openelements.content`
- [x] Create empty `ContentConfig` `@Configuration` in `com.openelements.content`

**Acceptance criteria:**
- [x] Application context initializes without errors
- [x] Project builds successfully

**Related behaviors:** App boots with the imported library configs; Scheduling is enabled; Content package is component-scanned

---

## Step 3: Application configuration (`application.yaml`)

- [x] `spring.datasource` → embedded H2 (env-overridable)
- [x] `spring.jpa` → `ddl-auto: update`, `open-in-view: false`
- [x] `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` → placeholder (lazy `JwtDecoder`)
- [x] `openelements.mcp` → enabled, server-name/version, api-key auth disabled (dev)
- [x] `openelements.meilisearch` → host/master-key/index-prefix (env-overridable)
- [x] `open-elements.content` → `enabled: true`, sources placeholder for spec 002

**Acceptance criteria:**
- [x] App boots with this configuration
- [x] Project builds successfully

**Related behaviors:** App boots with the imported library configs

---

## Step 4: Tests for all behavioral scenarios

- [x] `ContentMcpApplicationTests` — context loads; `ContentConfig` bean present; `ScheduledAnnotationBeanPostProcessor` present; MCP transport provider + `McpSyncServer` present with configured server-name/version and empty tool-provider catalog
- [x] `McpDisabledTest` — with `openelements.mcp.enabled=false`, no MCP server/transport beans
- [x] `MeilisearchUnreachableTest` — with Meilisearch enabled but unreachable, context starts and the HTTP listener serves
- [x] `JsoupClasspathTest` — jsoup parses HTML (classpath check)

**Acceptance criteria:**
- [x] All tests pass (`mvn test`)
- [x] Every behavior in `behaviors.md` maps to a test (see coverage table below)

**Related behaviors:** all

---

## Step 5: Project documentation

- [x] `README.md` — what the app is, prerequisites, build/run, configuration, test
- [x] `CLAUDE.md` — Project Context (Features, Tech Stack, Structure, Architecture)

**Acceptance criteria:**
- [x] Docs reflect the actual build/run and wiring

**Related behaviors:** n/a (documentation)

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| App boots with the imported library configs | Backend | Step 2, 3 (`ContentMcpApplicationTests.contextLoads`) |
| /mcp endpoint is exposed | Backend | Step 4 (`ContentMcpApplicationTests.mcpEndpointIsExposedWithEmptyContentToolCatalog`) |
| Scheduling is enabled | Backend | Step 4 (`ContentMcpApplicationTests.schedulingIsEnabled`) |
| Content package is component-scanned | Backend | Step 4 (`ContentMcpApplicationTests.contentConfigIsComponentScanned`) |
| jsoup is on the classpath | Build | Step 1, 4 (`JsoupClasspathTest`) |
| Meilisearch unreachable at startup | Backend | Step 4 (`MeilisearchUnreachableTest`) |
| MCP disabled | Backend | Step 4 (`McpDisabledTest`) |

There is no frontend in this spec — all scenarios are backend/build.
