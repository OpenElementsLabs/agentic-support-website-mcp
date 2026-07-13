package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UrlMatcher} covering the Ant-glob include/exclude semantics and URL
 * normalization described in the spec's behaviors.
 */
@DisplayName("URL matching")
class UrlMatcherTest {

    private final UrlMatcher matcher = new UrlMatcher();

    private static ContentSource source(List<String> include, List<String> exclude) {
        return new ContentSource(
            "test", SourceType.WEBSITE, "https://example.com",
            List.of(), include, exclude, "article", List.of(), true, null);
    }

    @Test
    @DisplayName("a blog-only include matches a deeply nested post")
    void blogOnlyIncludeMatchesNestedPost() {
        ContentSource source = source(List.of("/posts/**"), List.of());
        assertThat(matcher.matches(source, "/posts/2026/03/12/slug")).isTrue();
    }

    @Test
    @DisplayName("a single-segment star does not cross a slash")
    void singleSegmentStarDoesNotCrossSlash() {
        ContentSource source = source(List.of("/posts/*"), List.of());
        assertThat(matcher.matches(source, "/posts/2026/03/x")).isFalse();
        assertThat(matcher.matches(source, "/posts/hello")).isTrue();
    }

    @Test
    @DisplayName("the page-plus-subpages recipe includes both the page and its subpages")
    void pagePlusSubpagesRecipe() {
        ContentSource source = source(List.of("/support-care", "/support-care/**"), List.of());
        assertThat(matcher.matches(source, "/support-care")).isTrue();
        assertThat(matcher.matches(source, "/support-care/faq")).isTrue();
    }

    @Test
    @DisplayName("an exclude pattern wins over an include pattern")
    void excludeWinsOverInclude() {
        ContentSource source = source(List.of("/**"), List.of("/**/tag/**"));
        assertThat(matcher.matches(source, "/posts/tag/ai")).isFalse();
    }

    @Test
    @DisplayName("the default include matches everything, subject to excludes")
    void defaultIncludeMatchesEverything() {
        // urlInclude omitted -> ContentSource defaults it to ["/**"].
        ContentSource source = new ContentSource(
            "test", SourceType.WEBSITE, "https://example.com",
            List.of(), null, List.of("/private/**"), "article", List.of(), true, null);

        assertThat(matcher.matches(source, "/anything/at/all")).isTrue();
        assertThat(matcher.matches(source, "/private/secret")).isFalse();
    }

    @Test
    @DisplayName("matching ignores the scheme and host of a full URL")
    void matchingIgnoresSchemeAndHost() {
        ContentSource source = source(List.of("/posts/**"), List.of());
        assertThat(matcher.matches(source, "https://open-elements.com/posts/x")).isTrue();
    }

    @Test
    @DisplayName("matching ignores the query string")
    void matchingIgnoresQueryString() {
        ContentSource source = source(List.of("/posts/**"), List.of());
        assertThat(matcher.matches(source, "/posts/x?utm=1")).isTrue();
        assertThat(matcher.matches(source, "https://open-elements.com/posts/x?utm=1&ref=a")).isTrue();
    }
}
