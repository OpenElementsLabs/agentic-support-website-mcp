# Design: Additional Website Sources

## Summary

Add `hiero.org/blog` and the Open Elements Support & Care pages as **configuration-only**
sources — the whole point of the `ContentSource` abstraction (spec 002) is that a new website is
a new YAML entry, not new code. This spec is primarily selector/URL tuning and verification: it
resolves the design's open questions (does hiero.org have a usable sitemap? is Support & Care one
page or many?) and confirms extraction quality on real third-party markup.

## GitHub Issue

— (roadmap Phase 2; design doc §6, §12 P2, §"Open questions")

## Goals

- Add a `hiero` website source (`/blog/**`, content selector `main article`).
- Add a `support-and-care` source (`content-selector: body` + `content-exclude` for boilerplate).
- Tune selectors/URL patterns until extracted `body`/metadata are clean for each.
- Verify robots.txt (spec 014) is honored for hiero.org.

## Non-goals

- No code changes expected; if a real extraction gap is found, capture it as a follow-up spec rather than special-casing here.

## Technical approach

Add to `application.yaml`:

```yaml
open-elements:
  content:
    sources:
      - id: hiero
        type: website
        base-url: https://hiero.org
        sitemaps: [ /sitemap.xml ]      # verify it exists & contains /blog/ (open question)
        url-include: [ "/blog/**" ]
        content-selector: "main article"
        enabled: true
      - id: support-and-care
        type: website
        base-url: https://open-elements.com
        url-include: [ "/support-care", "/support-care/**" ]
        content-selector: "body"
        content-exclude: [ "nav", "header", "footer", ".cookie-banner", "aside" ]
        enabled: true
```

### Open-question resolution (design §"Open questions")

- **hiero.org sitemap:** check `https://hiero.org/sitemap.xml` for `/blog/` entries. If absent or incomplete, either point `sitemaps` at the correct sitemap path or rely on the no-sitemap bounded fallback crawl from `/blog` (spec 004). Record the finding.
- **Support & Care shape:** determine whether it is a single landing page or multiple docs pages; set `url-include` to `["/support-care"]` (single) or `["/support-care", "/support-care/**"]` (multi) accordingly.

### Verification

For each new source: run a bootstrap/refresh against a dev index and spot-check that `body` is
free of nav/footer/cookie boilerplate, metadata (title/date) is populated, and the MCP tools
return sensible results filtered by `source`.

### Rationale

- **Config-only** proves the abstraction and keeps the core untouched (design §6/§12).
- **`body` + `content-exclude`** is the design's recommended recipe for plain pages without a clean article container (design §6).

## Open questions

- hiero.org sitemap presence/coverage (resolved during this spec).
- Support & Care page count (resolved during this spec).
- Locale handling for hiero.org (English-only assumed; confirm).
