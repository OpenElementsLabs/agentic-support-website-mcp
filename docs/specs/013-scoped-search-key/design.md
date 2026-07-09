# Design: Scoped Search Key

## Summary

Restrict the runtime Meilisearch access to a **read-only scoped key** limited to the content
index, so the process serving MCP queries cannot write or touch other indexes. In
`spring-services` this is declarative: the app registers an optional `ScopedKeySpec` bean, and
the library's `MeilisearchScopedKeyInitializer` exchanges the master key for a scoped key at
startup. Writes (bootstrap/refresh) must occur before or independently of this restriction, so
the ordering and its implications are analyzed here.

## GitHub Issue

— (roadmap Phase 1 step 13; design doc §8 security)

## Goals

- Register a `ScopedKeySpec` bean scoping the runtime key to the content index with read-only actions (`search`, and any read actions the tools need).
- Confirm MCP-side auth posture (re-enable `openelements.mcp.auth.api-key` that spec 001 disabled for dev).
- Document the interaction between the scoped **read** key and the **write** path (indexer/bootstrap).

## Non-goals

- No new key-management code — the library owns the exchange.
- No per-tenant keys or key rotation automation (out of scope).

## Technical approach

### Read scoping via `ScopedKeySpec`

```java
@Bean
ScopedKeySpec contentScopedKey(MeilisearchProperties props) {
    return new ScopedKeySpec(
        List.of(props.resolveIndex("content")),   // index pattern(s) this key may touch
        List.of("search")                          // read-only actions
    );
}
```

At startup, `MeilisearchScopedKeyInitializer` (`@Order(10)`, first) mints a scoped key via
`MeilisearchClient.createScopedKey(indexPatterns, actions)` and calls `useApiKey(scoped)`. If no
bean is present or the exchange fails, the client keeps the master key (logged as degradation).

### Write-path ordering problem & resolution

The library search runners run in order: scoped-key exchange (`@Order 10`) → settings init
(`@Order 20`) → **bootstrap reindex** (`@Order 30`). If the key is swapped to search-only at
`@Order 10`, the later settings write and bootstrap **addDocuments** would fail (no write action).

Resolution options (decide at implementation, verifying library behavior):
1. **Grant the runtime key the write actions the startup runners need** (`search`, `documents.add`, `documents.delete`, `settings.update`, `indexes.create`) scoped to the content index only — still a major reduction from the master key (single index, no key-management, no other indexes), and the scheduler (spec 010) needs write access anyway since it runs continuously.
2. **Separate write vs. read keys** — keep master (or a write-scoped key) for the indexer/scheduler and a search-only key for query paths. This requires the client to hold two keys; `MeilisearchClient` swaps a single active key, so this needs care.

**Decision:** because the same process both indexes (bootstrap + scheduler) and serves reads,
option 1 — a **single index-scoped key granting exactly the actions this service performs on the
content index** — is the pragmatic security boundary (design intent: "read-only scoped key for
the content index only" is fully achievable for the *query* surface, but this process also
writes, so the key is scoped to the one index with the minimal action set it actually uses). If a
strict read-only query surface is required, split the query path into a separate deployment/process
with a search-only key (capture as follow-up).

### MCP auth

Spec 001 set `openelements.mcp.auth.api-key.enabled: false` for local boot. For any shared/prod
deployment, re-enable it (default is enabled) so `/mcp/**` requires `X-API-Key` via the library's
`McpSecurityConfig`. Document the env/config for provisioning API keys (`ApiKeyDataService`).

### Rationale

- **`ScopedKeySpec` bean** is the sanctioned, code-free mechanism (design §8) — no bespoke key handling.
- **Index-scoped, minimal-action key** shrinks blast radius from "all indexes, master" to "content index, only the actions used".
- **Explicit ordering analysis** prevents the classic "scoped key breaks bootstrap writes" startup failure.

## Security considerations

- Master key only in env/secret store; never logged (library already redacts).
- Scoped key is index-limited; the query surface cannot reach other indexes or admin actions.
- MCP endpoint protected by API-key auth in non-dev environments.

## Open questions

- Verify exactly which Meilisearch actions the startup runners + scheduler invoke, and grant only those.
- Whether to split read vs. write into two processes for a strict read-only query key — defer unless required.
