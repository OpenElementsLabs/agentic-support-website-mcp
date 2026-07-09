# Design: Content Source Configuration

## Summary

Introduce the declarative, typed `ContentSource` abstraction and its
`@ConfigurationProperties` binding so that content sources are configured in
`application.yaml` with **no code changes per source**. Includes the Ant-glob URL matching
(`urlInclude`/`urlExclude`) against URL paths and global crawl settings (user agent, rate
limit, timeouts, refresh cron). This is pure configuration + matching logic — no crawling
or indexing yet.

## GitHub Issue

— (roadmap Phase 1 step 2; design doc §3, §3a, §6, §10)

## Goals

- `ContentSource` record capturing one source: `id`, `type` (`website`|`git`), `baseUrl`, `sitemaps`, `urlInclude`, `urlExclude`, `contentSelector`, `contentExclude`, locale derivation, `enabled`.
- `ContentSourceProperties` bound to `open-elements.content` with the source list + global settings.
- A `UrlMatcher` (or method on `ContentSource`) implementing Ant-glob include/exclude against the URL **path**.
- Bind the example OE source from design §6 in `application.yaml`.

## Non-goals

- No sitemap reading, fetching, extraction, or indexing (specs 004–008).
- `type: git` fields are modeled as optional/absent here; the git source is spec 016.

## Technical approach

### `ContentSource` (record)

```java
public record ContentSource(
    String id,
    SourceType type,               // WEBSITE | GIT
    String baseUrl,
    List<String> sitemaps,         // e.g. ["/en/sitemap.xml", "/de/sitemap.xml"]
    List<String> urlInclude,       // Ant globs; default ["/**"]
    List<String> urlExclude,       // Ant globs; default []
    String contentSelector,        // CSS selector, e.g. "article" (used in spec 006)
    List<String> contentExclude,   // CSS selectors removed before extraction
    boolean enabled
) {}
public enum SourceType { WEBSITE, GIT }
```

Locale derivation is a per-source concern (e.g. `/de/posts/**` → `de`, else `en`). Model it
as a simple rule now (path-prefix based) and keep it extensible; details are exercised in
spec 006/007.

### `ContentSourceProperties` (`@ConfigurationProperties("open-elements.content")`)

```java
@ConfigurationProperties("open-elements.content")
public record ContentSourceProperties(
    boolean enabled,
    String refreshCron,            // e.g. "0 0 * * * *"
    String userAgent,              // "OpenElementsContentBot/1.0 (+https://open-elements.com)"
    double rateLimitPerHost,       // e.g. 2.0 req/s
    Duration requestTimeout,       // e.g. 10s
    DataSize maxBodyBytes,         // e.g. 5MB
    List<ContentSource> sources
) {}
```

Registered via `@EnableConfigurationProperties(ContentSourceProperties.class)` on `ContentConfig`.

### URL matching

Ant-style glob patterns via Spring's `AntPathMatcher` (already on the classpath), matched
against the **path part** of the URL (no scheme/host, leading `/`). A URL is included iff it
matches **≥1 `urlInclude`** pattern **and no** `urlExclude` pattern. Default `urlInclude` is
`["/**"]` when absent.

Token semantics (design §6): `?` = one non-`/` char, `*` = any chars within one segment,
`**` = any number of segments.

```java
@Component
public class UrlMatcher {
    private final AntPathMatcher matcher = new AntPathMatcher();
    public boolean matches(ContentSource src, String path) { /* include && !exclude */ }
}
```

### Rationale

- **Typed source (`type`)** so `type: git` (spec 016) plugs in without reworking config (design §3a).
- **`AntPathMatcher`** — already present, well-understood glob semantics, matches design §6 exactly.
- **Records + `@ConfigurationProperties`** — house style, immutable, relaxed binding for kebab-case YAML keys.

## Data model

No persistence. Configuration only.

## Configuration

```yaml
open-elements:
  content:
    enabled: true
    refresh-cron: "0 0 * * * *"
    user-agent: "OpenElementsContentBot/1.0 (+https://open-elements.com)"
    rate-limit-per-host: 2
    request-timeout: 10s
    max-body-bytes: 5MB
    sources:
      - id: open-elements
        type: website
        base-url: https://open-elements.com
        sitemaps: [ /en/sitemap.xml, /de/sitemap.xml ]
        url-include: [ "/posts/**", "/de/posts/**" ]
        content-selector: "article"
        enabled: true
```

## Open questions

- Exact shape of locale derivation config (path-prefix map vs. hard-coded `/de` rule). Start with a simple rule; revisit if a third locale appears.
- Whether `rate-limit-per-host` is global or per-source (design implies per-host global; keep global for now).
