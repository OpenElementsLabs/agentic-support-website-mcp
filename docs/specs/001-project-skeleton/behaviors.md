# Behaviors: Project Skeleton

## Application startup

### App boots with the imported library configs
- **Given** a valid `application.yaml` with `openelements.mcp.enabled=true` and `openelements.meilisearch.enabled=true` and a reachable Meilisearch instance
- **When** `ContentMcpApplication` starts
- **Then** the Spring context initializes without errors and the app reports healthy

### /mcp endpoint is exposed
- **Given** the running application with `openelements.mcp.enabled=true`
- **When** the context is inspected
- **Then** the MCP server is configured — the streamable-HTTP transport provider that registers the `/mcp` route and the `McpSyncServer` bean are present, the server advertises the configured `server-name`/`server-version`, and no content-tool providers are registered yet (empty content-tool catalog)

> **Note (verified during implementation):** the `/mcp` route is always secured by the library.
> With `openelements.mcp.auth.api-key.enabled=false` the MCP api-key filter chain is skipped and
> `/mcp` falls under the library's default (JWT) chain (`anyRequest().authenticated()`), so an
> unauthenticated HTTP request returns `401` regardless of whether the route exists. Because HTTP
> status therefore cannot distinguish an exposed endpoint from a missing one, exposure is asserted at
> the bean level. An authenticated round-trip against `/mcp` is covered by spec 013 (auth).

### Scheduling is enabled
- **Given** the running application
- **When** the context is inspected
- **Then** `@EnableScheduling` is active (a `TaskScheduler` is present), so later `@Scheduled` beans (spec 010) will run

### Content package is component-scanned
- **Given** the `com.openelements.content` package with `ContentConfig` `@Configuration`
- **When** the context starts
- **Then** `ContentConfig` is registered as a bean and is available for later specs to extend

## Dependencies

### jsoup is on the classpath
- **Given** the built application
- **When** the dependency tree is resolved
- **Then** `org.jsoup:jsoup` is present (even though unused until spec 006)

## Error cases

### Meilisearch unreachable at startup
- **Given** `openelements.meilisearch.enabled=true` but no reachable Meilisearch host
- **When** the app starts
- **Then** the search startup runners log a warning/degradation but the HTTP listener and `/mcp` still come up (the app does not crash on a missing search backend)

### MCP disabled
- **Given** `openelements.mcp.enabled=false`
- **When** the app starts
- **Then** no `/mcp` endpoint is exposed (the MCP server beans are not created)
