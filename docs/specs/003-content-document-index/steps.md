# Implementation Steps: Content Document & Index Settings

## Step 1: `ContentDocument` record

- [x] Record with the canonical §4 fields (`id`, `source`, `locale`, `url`, `title`, `excerpt`, `body`, `author`, `categories`, `publishedDate`, `lastmod`, `previewImage`)
- [x] Compact constructor normalizes `categories` to a non-null list
- [x] `static String id(String source, String url)` = `sanitize(source) + "_" + sha256Hex(url)` (Meilisearch-legal key)
- [x] `Map<String,Object> toMap()` with all indexed fields (nulls tolerated)

**Acceptance criteria:**
- [x] Project builds; unit tests pass

**Related behaviors:** Stable id from source + url; Different url yields different id; Id is a valid Meilisearch primary key; toMap contains all indexed fields; Null optional fields map cleanly

---

## Step 2: `ContentIndexSettings` `@Bean`

- [x] `@Bean IndexSettings contentIndexSettings(MeilisearchProperties)` on `ContentConfig`
- [x] `primaryKey=id`, searchable `[title, excerpt, body]`, filterable `[source, locale, author, categories, publishedDate]`, sortable `[publishedDate]`
- [x] Index UID via `resolveIndex("content")` (prefix-aware)
- [x] Gated with `@ConditionalOnProperty(openelements.meilisearch.enabled=true)` so a search-disabled context still starts

**Acceptance criteria:**
- [x] Bean registered with correct attributes; picked up by `MeilisearchIndexSettingsInitializer`
- [x] Project builds; tests pass

**Related behaviors:** Settings bean registered with correct attributes; Settings applied to the index at startup; Index UID honors prefix

---

## Step 3: Tests

- [x] `ContentDocumentTest` — id determinism/uniqueness/validity, toMap fields + nulls, categories normalization
- [x] `ContentIndexSettingsTest` — attributes, prefix-aware UID, disabled-case absence

**Acceptance criteria:**
- [x] All tests pass (`mvn test`)

**Related behaviors:** all (except the live-Meilisearch application step — see coverage note)

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| Stable id from source + url | Backend | Step 3 (`ContentDocumentTest.idIsDeterministic`) |
| Different url yields different id | Backend | Step 3 (`ContentDocumentTest.differentUrlYieldsDifferentId`) |
| Id is a valid Meilisearch primary key | Backend | Step 3 (`ContentDocumentTest.idIsAValidMeilisearchPrimaryKey`) |
| toMap contains all indexed fields | Backend | Step 3 (`ContentDocumentTest.toMapContainsAllIndexedFields`) |
| Null optional fields map cleanly | Backend | Step 3 (`ContentDocumentTest.nullOptionalFieldsMapCleanly`) |
| Settings bean registered with correct attributes | Backend | Step 3 (`ContentIndexSettingsTest.attributesAreConfigured`) |
| Index UID honors prefix | Backend | Step 3 (`ContentIndexSettingsTest.indexUidHonorsPrefix`) |
| Settings applied to the index at startup | Backend (integration) | Delegated to the library's `MeilisearchIndexSettingsInitializer`, which consumes the registered bean. Not reproduced as an automated test because it requires a running Meilisearch (no live instance in CI) — consistent with spec 001's live-search boundary. |

All scenarios are backend; there is no frontend in this spec.
