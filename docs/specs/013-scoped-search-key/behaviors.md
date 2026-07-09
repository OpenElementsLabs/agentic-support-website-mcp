# Behaviors: Scoped Search Key

## Key exchange

### Scoped key is minted at startup
- **Given** a `ScopedKeySpec` bean scoping to the content index and a reachable Meilisearch with a master key
- **When** the app starts
- **Then** `MeilisearchScopedKeyInitializer` exchanges the master key for a scoped key and the client uses it thereafter

### Degradation when exchange fails
- **Given** the scoped-key exchange fails (e.g. master key lacks key-creation rights)
- **When** the app starts
- **Then** a warning is logged and the client falls back to the master key (the app still runs)

## Scope enforcement

### Runtime key is limited to the content index
- **Given** the scoped key is active
- **When** an operation targets a different index
- **Then** Meilisearch rejects it (the key has no access outside the content index)

### Query operations succeed under the scoped key
- **Given** the scoped key with `search` action
- **When** the MCP tools run searches
- **Then** searches succeed

### Startup writes succeed under the granted actions
- **Given** the key is granted the exact write actions the bootstrap/settings runners use, scoped to the content index
- **When** the app bootstraps the index
- **Then** settings-write and document-add succeed (no permission failure during startup)

## MCP authentication

### API-key auth enforced in non-dev
- **Given** `openelements.mcp.auth.api-key.enabled: true`
- **When** a client calls `/mcp` without a valid `X-API-Key`
- **Then** the request is rejected as unauthorized

### Dev profile allows unauthenticated local access
- **Given** the dev profile with api-key auth disabled
- **When** a local client calls `/mcp`
- **Then** the call is allowed (dev convenience only)
