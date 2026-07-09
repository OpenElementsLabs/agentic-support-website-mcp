# Behaviors: Content Source Configuration

## Property binding

### Sources bind from YAML
- **Given** an `application.yaml` with an `open-elements.content.sources` list containing one `website` source
- **When** the context starts
- **Then** `ContentSourceProperties.sources()` contains one `ContentSource` with the parsed fields (`id`, `type=WEBSITE`, `baseUrl`, `sitemaps`, `urlInclude`, `contentSelector`)

### Global settings bind with defaults
- **Given** YAML that omits `url-include` on a source
- **When** the source is bound
- **Then** its effective `urlInclude` defaults to `["/**"]`

### Disabled source is represented
- **Given** a source with `enabled: false`
- **When** properties are bound
- **Then** the `ContentSource.enabled()` is `false` (consumers in later specs skip it)

## URL matching — include

### Blog-only include matches nested post
- **Given** a source with `url-include: ["/posts/**"]`
- **When** matching path `/posts/2026/03/12/slug`
- **Then** the URL is included

### Single-segment star does not cross slash
- **Given** `url-include: ["/posts/*"]`
- **When** matching `/posts/2026/03/x`
- **Then** the URL is **not** included (but `/posts/hello` is)

### Page plus subpages recipe
- **Given** `url-include: ["/support-care", "/support-care/**"]`
- **When** matching `/support-care` and `/support-care/faq`
- **Then** both are included (the `/x/**` pattern alone would not match `/x` itself)

## URL matching — exclude

### Exclude wins over include
- **Given** `url-include: ["/**"]` and `url-exclude: ["/**/tag/**"]`
- **When** matching `/posts/tag/ai`
- **Then** the URL is **not** included

### No include list means everything
- **Given** a source with no `url-include`
- **When** matching any path
- **Then** the URL is included (default `["/**"]`) unless an `url-exclude` pattern matches

## Edge cases

### Matching ignores scheme and host
- **Given** a full URL `https://open-elements.com/posts/x`
- **When** matched
- **Then** only the path `/posts/x` is considered (scheme/host stripped)

### Query string handling
- **Given** a URL with a query string `/posts/x?utm=1`
- **When** matched against `/posts/**`
- **Then** the path `/posts/x` matches (the query part is not part of the path match)
