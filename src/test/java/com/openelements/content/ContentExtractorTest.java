package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ContentExtractor}: body selection/cleaning and metadata extraction.
 */
@DisplayName("Content extractor")
class ContentExtractorTest {

    private final ContentExtractor extractor =
        new ContentExtractor(new ContentLocaleResolver(), new ObjectMapper());

    private static ContentSource source(String contentSelector, List<String> contentExclude) {
        return new ContentSource(
            "test", SourceType.WEBSITE, "https://ex.com",
            List.of(), List.of("/**"), List.of(), contentSelector, contentExclude, true, null);
    }

    private ExtractedContent extract(String contentSelector, List<String> exclude, String html) {
        return extractor.extract(source(contentSelector, exclude), "https://ex.com/posts/x", html);
    }

    // ---- body extraction ----

    @Test
    @DisplayName("the content selector picks the article container, excluding nav/header/footer")
    void selectorPicksArticleContainer() {
        String html = "<html><body>"
            + "<nav><p>Navigation</p></nav><header><p>Site header</p></header>"
            + "<article><p>The real article body.</p></article>"
            + "<footer><p>Footer stuff</p></footer>"
            + "</body></html>";

        String body = extract("article", List.of(), html).body();

        assertThat(body).contains("The real article body.");
        assertThat(body).doesNotContain("Navigation", "Site header", "Footer stuff");
    }

    @Test
    @DisplayName("a comma-separated selector list uses the first match")
    void commaSeparatedFallbackFirstMatchWins() {
        String html = "<html><body><main><p>Main content here.</p></main></body></html>";

        String body = extract("article, main, .prose", List.of(), html).body();

        assertThat(body).contains("Main content here.");
    }

    @Test
    @DisplayName("the body selector takes the whole document")
    void bodySelectorTakesWholeDocument() {
        String html = "<html><body><div>Section one.</div><div>Section two.</div></body></html>";

        String body = extract("body", List.of(), html).body();

        assertThat(body).contains("Section one.").contains("Section two.");
    }

    @Test
    @DisplayName("scripts and styles are always removed, even with the body selector")
    void scriptsAndStylesAreAlwaysRemoved() {
        String html = "<html><head><style>.a{color:red}</style></head><body>"
            + "<script>alert('x')</script><p>Visible text.</p></body></html>";

        String body = extract("body", List.of(), html).body();

        assertThat(body).contains("Visible text.");
        assertThat(body).doesNotContain("alert", "color:red");
    }

    @Test
    @DisplayName("contentExclude removes boilerplate elements")
    void contentExcludeRemovesBoilerplate() {
        String html = "<html><body>"
            + "<nav>Navigation</nav><div class=\"cookie-banner\">Accept cookies</div>"
            + "<p>Actual content.</p><footer>Footer</footer>"
            + "</body></html>";

        String body = extract("body", List.of("nav", "footer", ".cookie-banner"), html).body();

        assertThat(body).contains("Actual content.");
        assertThat(body).doesNotContain("Navigation", "Accept cookies", "Footer");
    }

    @Test
    @DisplayName("whitespace is collapsed and paragraphs are separated")
    void whitespaceIsNormalized() {
        String html = "<html><body><article>"
            + "<p>First    paragraph\n  with   irregular\twhitespace.</p>"
            + "<p>Second paragraph.</p>"
            + "</article></body></html>";

        String body = extract("article", List.of(), html).body();

        assertThat(body).isEqualTo("First paragraph with irregular whitespace.\n\nSecond paragraph.");
    }

    @Test
    @DisplayName("a readability fallback is used when no selector matches")
    void readabilityFallbackWhenNoSelectorMatches() {
        String html = "<html><body>"
            + "<nav><p>Nav</p></nav>"
            + "<article><p>The main readable article content.</p></article>"
            + "<footer><p>Footer</p></footer>"
            + "</body></html>";

        // selector matches nothing -> fallback should find the <article>
        String body = extract(".does-not-exist", List.of(), html).body();

        assertThat(body).contains("The main readable article content.");
        assertThat(body).doesNotContain("Nav", "Footer");
    }

    // ---- metadata extraction ----

    @Test
    @DisplayName("OpenGraph metadata populates title, excerpt, image and published date")
    void openGraphMetadataIsRead() {
        String html = "<html><head>"
            + "<meta property=\"og:title\" content=\"OG Title\">"
            + "<meta property=\"og:description\" content=\"OG summary\">"
            + "<meta property=\"og:image\" content=\"https://ex.com/img.png\">"
            + "<meta property=\"article:published_time\" content=\"2026-03-12T10:00:00Z\">"
            + "</head><body><article><p>Body</p></article></body></html>";

        ExtractedContent result = extract("article", List.of(), html);

        assertThat(result.title()).isEqualTo("OG Title");
        assertThat(result.excerpt()).isEqualTo("OG summary");
        assertThat(result.previewImage()).isEqualTo("https://ex.com/img.png");
        assertThat(result.publishedDate()).isEqualTo("2026-03-12T10:00:00Z");
    }

    @Test
    @DisplayName("JSON-LD supplies date and author when article meta is absent")
    void jsonLdFallbackForDateAndAuthor() {
        String html = "<html><head><title>T</title>"
            + "<script type=\"application/ld+json\">"
            + "{\"@type\":\"Article\",\"datePublished\":\"2026-03-12\",\"author\":{\"name\":\"Hendrik\"}}"
            + "</script></head><body><article><p>Body</p></article></body></html>";

        ExtractedContent result = extract("article", List.of(), html);

        assertThat(result.publishedDate()).isEqualTo("2026-03-12");
        assertThat(result.author()).isEqualTo("Hendrik");
    }

    @Test
    @DisplayName("categories are read from multiple article:tag meta entries")
    void categoriesFromArticleTags() {
        String html = "<html><head>"
            + "<meta property=\"article:tag\" content=\"ai\">"
            + "<meta property=\"article:tag\" content=\"web3\">"
            + "</head><body><article><p>Body</p></article></body></html>";

        ExtractedContent result = extract("article", List.of(), html);

        assertThat(result.categories()).containsExactly("ai", "web3");
    }

    @Test
    @DisplayName("locale is derived from the URL path")
    void localeDerivedFromPath() {
        ContentSource source = source("article", List.of());
        String html = "<html><body><article><p>Inhalt</p></article></body></html>";

        ExtractedContent result = extractor.extract(source, "https://ex.com/de/posts/x", html);

        assertThat(result.locale()).isEqualTo("de");
    }

    @Test
    @DisplayName("metadata is read from <head> regardless of the content selector")
    void metadataReadRegardlessOfContentSelector() {
        String html = "<html><head><meta property=\"og:title\" content=\"Head Title\"></head>"
            + "<body><article><span id=\"tiny\">x</span><p>Body</p></article></body></html>";

        ExtractedContent result = extract("#tiny", List.of(), html);

        assertThat(result.title()).isEqualTo("Head Title");
    }

    // ---- edge cases ----

    @Test
    @DisplayName("missing metadata degrades to null without error")
    void missingMetadataDegradesGracefully() {
        String html = "<html><body><article><p>Just a body, no meta.</p></article></body></html>";

        ExtractedContent result = extract("article", List.of(), html);

        assertThat(result.excerpt()).isNull();
        assertThat(result.author()).isNull();
        assertThat(result.previewImage()).isNull();
        assertThat(result.categories()).isEmpty();
        assertThat(result.body()).contains("Just a body, no meta.");
    }

    @Test
    @DisplayName("an empty matched container falls back and yields an empty body without error")
    void emptyContentContainerFallsBack() {
        String html = "<html><body><div id=\"empty\"></div></body></html>";

        String body = extract("#empty", List.of(), html).body();

        assertThat(body).isEmpty();
    }
}
