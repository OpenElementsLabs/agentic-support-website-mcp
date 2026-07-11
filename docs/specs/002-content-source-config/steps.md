# Implementation Steps: Content Source Configuration

## Step 1: `ContentSource` typed model

- [x] `SourceType` enum (`WEBSITE`, `GIT`)
- [x] `ContentSource` record with `id`, `type`, `baseUrl`, `sitemaps`, `urlInclude`, `urlExclude`, `contentSelector`, `contentExclude`, `enabled`
- [x] Compact constructor normalizes optional lists (empty defaults; `urlInclude` defaults to `["/**"]`)

**Acceptance criteria:**
- [x] Project builds successfully
- [x] Defaults verified by tests

**Related behaviors:** Sources bind from YAML; Global settings bind with defaults; Disabled source is represented

---

## Step 2: `ContentSourceProperties` binding

- [x] `@ConfigurationProperties("open-elements.content")` record with `enabled`, `refreshCron`, `userAgent`, `rateLimitPerHost`, `requestTimeout`, `maxBodyBytes`, `sources`
- [x] Compact constructor applies sensible defaults for omitted global settings
- [x] Register via `@EnableConfigurationProperties(ContentSourceProperties.class)` on `ContentConfig`

**Acceptance criteria:**
- [x] Properties bind in the application context
- [x] Project builds successfully

**Related behaviors:** Sources bind from YAML; Global settings bind with defaults; Disabled source is represented

---

## Step 3: `UrlMatcher` Ant-glob matching

- [x] `@Component` using Spring `AntPathMatcher`
- [x] Match against the URL path only — strip scheme/host/query/fragment, ensure leading `/`
- [x] Include if `≥1 urlInclude` matches and no `urlExclude` matches (exclude wins)

**Acceptance criteria:**
- [x] All matching scenarios verified by unit tests
- [x] Project builds successfully

**Related behaviors:** all URL matching + edge-case scenarios

---

## Step 4: Configuration

- [x] Bind the example OE source + global settings in `application.yaml`

**Acceptance criteria:**
- [x] The application context binds the source at startup (covered by `ContentMcpApplicationTests`)

**Related behaviors:** Sources bind from YAML

---

## Step 5: Tests

- [x] `UrlMatcherTest` — include, single-segment star, page-plus-subpages, exclude-wins, default include, scheme/host/query stripping
- [x] `ContentSourcePropertiesTest` — binding, url-include default, disabled source, global defaults, global overrides

**Acceptance criteria:**
- [x] All tests pass (`mvn test`)

**Related behaviors:** all

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| Sources bind from YAML | Backend | Step 5 (`ContentSourcePropertiesTest.websiteSourceBindsFromYaml`) |
| Global settings bind with defaults (url-include default) | Backend | Step 5 (`ContentSourcePropertiesTest.omittedUrlIncludeDefaultsToMatchAll`) |
| Disabled source is represented | Backend | Step 5 (`ContentSourcePropertiesTest.disabledSourceIsRepresented`) |
| Blog-only include matches nested post | Backend | Step 5 (`UrlMatcherTest.blogOnlyIncludeMatchesNestedPost`) |
| Single-segment star does not cross slash | Backend | Step 5 (`UrlMatcherTest.singleSegmentStarDoesNotCrossSlash`) |
| Page plus subpages recipe | Backend | Step 5 (`UrlMatcherTest.pagePlusSubpagesRecipe`) |
| Exclude wins over include | Backend | Step 5 (`UrlMatcherTest.excludeWinsOverInclude`) |
| No include list means everything | Backend | Step 5 (`UrlMatcherTest.defaultIncludeMatchesEverything`) |
| Matching ignores scheme and host | Backend | Step 5 (`UrlMatcherTest.matchingIgnoresSchemeAndHost`) |
| Query string handling | Backend | Step 5 (`UrlMatcherTest.matchingIgnoresQueryString`) |

All scenarios are backend; there is no frontend in this spec.
