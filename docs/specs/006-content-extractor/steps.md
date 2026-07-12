# Implementation Steps: Content Extractor

## Step 1: `ExtractedContent` record

- [x] Record with `title`, `excerpt`, `body`, `author`, `categories`, `publishedDate`, `previewImage`, `locale`; `categories` normalized to non-null

**Related behaviors:** all (return shape)

---

## Step 2: `ContentExtractor` — body extraction

- [x] `@Component` taking `ContentLocaleResolver` + `ObjectMapper`
- [x] Read metadata first, then strip `script`/`style`/`noscript`/`template` + comments (so JSON-LD survives)
- [x] Container selection: `contentSelector` comma list (first non-empty match), `body` = whole document, readability fallback (semantic tag, else largest div/section) when nothing matches
- [x] Remove `contentExclude` selectors from the container
- [x] Whitespace-normalized, block-separated body (`\n\n` between paragraphs); fallback to collapsed container text

**Related behaviors:** selector container; comma fallback; body selector; scripts/styles removed; contentExclude; whitespace normalized; readability fallback; empty container

---

## Step 3: `ContentExtractor` — metadata extraction

- [x] title: `og:title` → `<title>` → first `<h1>`
- [x] excerpt: `description` → `og:description`
- [x] previewImage: `og:image`
- [x] publishedDate: `article:published_time` → `<time datetime>` → JSON-LD `datePublished`
- [x] author: `author`/`article:author` meta → JSON-LD `author.name`
- [x] categories: `article:tag` metas → JSON-LD `keywords`
- [x] locale: from `ContentLocaleResolver` (path rule)
- [x] JSON-LD parsed via Jackson, following arrays and `@graph`
- [x] Graceful degradation to null when fields are absent

**Related behaviors:** OpenGraph metadata; JSON-LD date/author; article tags; locale; metadata independent of selector; missing metadata

---

## Step 4: Tests

- [x] `ContentExtractorTest` (14 cases) — body selection/cleaning + metadata + edge cases

**Acceptance criteria:**
- [x] All tests pass (`mvn test`); build green

---

## Behavior Coverage

| Scenario | Layer | Covered in Step |
|----------|-------|-----------------|
| Selector picks the article container | Backend | Step 4 (`selectorPicksArticleContainer`) |
| Comma-separated fallback list, first match wins | Backend | Step 4 (`commaSeparatedFallbackFirstMatchWins`) |
| body selector takes the whole document | Backend | Step 4 (`bodySelectorTakesWholeDocument`) |
| Scripts and styles are always removed | Backend | Step 4 (`scriptsAndStylesAreAlwaysRemoved`) |
| contentExclude removes boilerplate | Backend | Step 4 (`contentExcludeRemovesBoilerplate`) |
| Whitespace is normalized | Backend | Step 4 (`whitespaceIsNormalized`) |
| Readability fallback when no selector matches | Backend | Step 4 (`readabilityFallbackWhenNoSelectorMatches`) |
| OpenGraph metadata is read | Backend | Step 4 (`openGraphMetadataIsRead`) |
| JSON-LD fallback for date/author | Backend | Step 4 (`jsonLdFallbackForDateAndAuthor`) |
| Categories from article tags | Backend | Step 4 (`categoriesFromArticleTags`) |
| Locale derived from path | Backend | Step 4 (`localeDerivedFromPath`) |
| Metadata read regardless of content selector | Backend | Step 4 (`metadataReadRegardlessOfContentSelector`) |
| Missing metadata degrades gracefully | Backend | Step 4 (`missingMetadataDegradesGracefully`) |
| Empty content container | Backend | Step 4 (`emptyContentContainerFallsBack`) |

All scenarios are backend; there is no frontend in this spec.

## Notes

- Excerpt open question: kept `null` when no `description`/`og:description` (no body-snippet fallback) — the behavior allows null.
- Markdown vs plaintext: plaintext body kept (no `flexmark` dependency added).
