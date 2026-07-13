# Open Elements Content MCP

An [MCP](https://modelcontextprotocol.io) server that crawls, indexes, and makes
searchable/retrievable the content (primarily blog posts) of several
Open-Elements-adjacent websites — exposed to AI agents over the standard `/mcp`
streamable-HTTP endpoint.

The application is standalone and built on
[`spring-services`](https://github.com/OpenElementsLabs/spring-services) as a library: it
reuses that library's MCP server and Meilisearch building blocks and adds the content
crawling/indexing/search pipeline in the `com.openelements.content` package.

> **Status:** project skeleton (spec `001-project-skeleton`). The app boots and exposes the
> `/mcp` endpoint with no content-specific tools yet. The crawler, indexer, search service, and
> MCP tools arrive in later specs — see [`docs/specs/INDEX.md`](docs/specs/INDEX.md) and
> [`docs/roadmap.md`](docs/roadmap.md).

## Prerequisites

- **Java 21** (enforced by the Maven parent)
- **Maven 3.9.11+**
- **Meilisearch** (optional for boot) — needed for actual indexing/search. The app tolerates an
  unreachable Meilisearch at startup and comes up anyway.

## Build

```bash
mvn clean package
```

This runs the tests and produces an executable jar in `target/`.

> The build depends on `spring-services:1.3.0-SNAPSHOT`, resolved from the Central Portal
> snapshot repository declared in `pom.xml`.

## Run

```bash
java -jar target/content-mcp-0.1.0-SNAPSHOT.jar
```

The app starts on port `8080` and exposes `/mcp`. It connects to Meilisearch at
`http://localhost:7700` by default (override via environment variables below).

By default `/mcp` requires an `X-API-Key`. For local development, run with the `dev` profile to
allow unauthenticated access:

```bash
java -jar target/content-mcp-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

## Test

```bash
mvn test
```

## Configuration

Configuration lives in [`src/main/resources/application.yaml`](src/main/resources/application.yaml).
Common overrides (all via environment variables):

| Variable | Default | Purpose |
|----------|---------|---------|
| `MEILISEARCH_HOST` | `http://localhost:7700` | Meilisearch base URL |
| `MEILISEARCH_MASTER_KEY` | *(empty)* | Meilisearch master key |
| `MEILISEARCH_INDEX_PREFIX` | `content_` | Index name prefix |
| `SPRING_DATASOURCE_URL` | in-memory H2 | Datasource for the library's api-key/user tables |
| `SPRING_DATASOURCE_USERNAME` | `sa` | Datasource user |
| `SPRING_DATASOURCE_PASSWORD` | *(empty)* | Datasource password |
| `OAUTH2_JWK_SET_URI` | placeholder | JWK set URI for the library's OAuth2 resource server |
| `MCP_API_KEY_AUTH_ENABLED` | `true` | Require `X-API-Key` on `/mcp` (set `false` or use the `dev` profile locally) |

### Notes on the wiring

`spring-services` couples its MCP server to a JPA-backed api-key/user stack and depends on
`spring-boot-starter-data-jpa` non-optionally, so a datasource is mandatory. This app supplies
an **embedded H2** database that backs only those library tables — the content pipeline itself
keeps no relational state (its store is Meilisearch). The `/mcp` endpoint requires an `X-API-Key` by
default (disable locally with the `dev` profile), and the runtime Meilisearch key is scoped to the
content index (spec 013). See
[`docs/specs/001-project-skeleton/design.md`](docs/specs/001-project-skeleton/design.md) for the
full rationale.

## Documentation

- [`docs/content-mcp-technical-design.md`](docs/content-mcp-technical-design.md) — overall technical design
- [`docs/roadmap.md`](docs/roadmap.md) — implementation roadmap
- [`docs/specs/`](docs/specs/) — per-step specifications
