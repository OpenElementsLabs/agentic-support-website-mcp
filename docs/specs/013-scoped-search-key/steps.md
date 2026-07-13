# Implementation Steps: Scoped Search Key

## Step 1: `ScopedKeySpec` bean

- [x] `@Bean ScopedKeySpec contentScopedKey(...)` in `ContentConfig` (gated on `meilisearch.enabled`)
- [x] Scoped to `resolveIndex("content")` with the minimal action set the service uses (`CONTENT_INDEX_ACTIONS`: search + documents.add/delete/get + settings.get/update + indexes.get/create + tasks.get)
- [x] Library `MeilisearchScopedKeyInitializer` exchanges the master key for this scoped key at startup; degrades to the master key on failure

**Related behaviors:** scoped key minted at startup; runtime key limited to content index; query + startup writes succeed; degradation on failure

---

## Step 2: MCP auth posture

- [x] `application.yaml`: api-key auth on `/mcp` defaults to **enabled** (`MCP_API_KEY_AUTH_ENABLED`, default `true`)
- [x] `application-dev.yaml` (`dev` profile): disables api-key auth for local convenience

**Related behaviors:** API-key auth enforced in non-dev; dev profile allows unauthenticated local access

---

## Step 3: Tests

- [x] `ContentScopedKeyTest`: scoped-key bean targets the content index, grants the used actions, and is absent when Meilisearch is disabled
- [x] Full app-context build confirms the scoped-key initializer degrades gracefully when Meilisearch is unreachable

**Acceptance criteria:**
- [x] All tests pass (`mvn test`); build green

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| Scoped key is minted at startup | Backend (integration) | Delegated to the library `MeilisearchScopedKeyInitializer` (consumes the `ScopedKeySpec` bean); needs a live Meilisearch. Bean presence verified in Step 3. |
| Degradation when exchange fails | Backend | Step 3 — full context boots with the initializer against an unreachable Meilisearch (warn + fall back), verified by the existing app-context tests. |
| Runtime key is limited to the content index | Backend | Step 3 (`scopedKeyTargetsContentIndex`) — the spec scopes to one index; enforcement is Meilisearch's (live). |
| Query operations succeed under the scoped key | Backend | Step 3 (`scopedKeyGrantsUsedActions` includes `search`); live behavior is Meilisearch's. |
| Startup writes succeed under the granted actions | Backend | Step 3 (`scopedKeyGrantsUsedActions` includes the write actions); live behavior is Meilisearch's. |
| API-key auth enforced in non-dev | Backend | Step 2 — default `enabled=true` drives the library `McpSecurityConfig` filter chain (its enforcement is library-tested). |
| Dev profile allows unauthenticated local access | Backend | Step 2 (`application-dev.yaml`). |

All scenarios are backend; there is no frontend in this spec. The live key-exchange/enforcement scenarios are the library's responsibility and require a running Meilisearch, consistent with the project's established boundary. The exact Meilisearch action names are verified against a live instance; if the exchange fails the app degrades to the master key.
