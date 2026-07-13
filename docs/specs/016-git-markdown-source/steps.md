# Implementation Steps: Git Markdown Source

## Step 1: Config model

- [x] `GitConfig` record (`provider`, `repo`, `ref`, `paths`, `token`) with `hasToken()`
- [x] Add optional `git` field to `ContentSource` (present only for `type: git`); existing call sites pass `null`

**Related behaviors:** private-repo token; config binding

---

## Step 2: `GitSourceStrategy`

- [x] `@Component implements ContentSourceStrategy` for `SourceType.GIT` (auto-routed by `SourceStrategyRegistry`)
- [x] `discover`: GitHub Trees API (`/repos/{repo}/git/trees/{ref}?recursive=1`), keep blobs matching `paths` globs (`AntPathMatcher`), SHA as change marker; a hard failure (auth) throws so the source is isolated (not mass-deleted)
- [x] `fetch`: raw content (`raw.githubusercontent.com`), split YAML frontmatter from Markdown body, clean Hugo shortcodes, map path → canonical URL, derive locale from filename suffix, build `ContentDocument` (SHA = `lastmod`)
- [x] Secrets: bearer token applied server-side only; never logged; empty-default env placeholder

**Related behaviors:** discovery/filter; incremental SHA; frontmatter/shortcode/URL/locale extraction; token handling

---

## Step 3: Config + tests

- [x] `application.yaml`: example `oe-website-markdown` git source (disabled by default; token via `GITHUB_TOKEN_OE_WEBSITE`, empty default)
- [x] `GitSourceStrategyTest` (MockRestServiceServer): discovery+filter+SHA, bearer token, discovery-failure, fetch extraction (frontmatter/shortcode/URL/locale), fetch-failure skip, and pure `mapToUrl`/`localeFromPath`/`parseMarkdown`
- [x] `ConfiguredSourcesTest`: the git source binds as `SourceType.GIT`

**Acceptance criteria:**
- [x] All tests pass (`mvn test`); build green

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| Files discovered via the trees API and filtered by paths | Backend | Step 3 (`discoveryFiltersByPaths`) |
| Non-matching files excluded | Backend | Step 3 (`discoveryFiltersByPaths` — README/png excluded) |
| Unchanged file (same SHA) is skipped | Backend | SHA returned as `lastmod` (`discoveryFiltersByPaths`); the unchanged/changed diff is `ContentIndexer`'s (spec 008 tests) |
| Changed file (new SHA) is re-indexed | Backend | As above — diff handled by `ContentIndexer`; strategy supplies the SHA |
| Frontmatter becomes metadata | Backend | Step 3 (`fetchExtractsFrontmatterAndBody`) |
| Hugo shortcodes are cleaned | Backend | Step 3 (`fetchExtractsFrontmatterAndBody` — no `{{<`) |
| Path maps to canonical URL | Backend | Step 3 (`datedFilenameMapsToCanonicalUrl`, `fetchExtractsFrontmatterAndBody`) |
| Locale from filename suffix | Backend | Step 3 (`localeFromFilenameSuffix`) |
| Private repo accessed with token | Backend | Step 3 (`tokenIsSentForPrivateRepo`) |
| Token is never logged or served | Backend | Design: token applied only as a request header, never logged (auth logs use `repo`, not token); MCP serves only indexed docs |
| Missing token for private repo fails clearly | Backend | Step 3 (`missingTokenFailsClearly`) |
| Git-sourced docs behave like website docs | Backend | Downstream unchanged — same `ContentDocument`/indexer/tools (specs 008/011/012) |

All scenarios are backend; there is no frontend in this spec.
