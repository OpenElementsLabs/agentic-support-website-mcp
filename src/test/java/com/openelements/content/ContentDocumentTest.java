package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ContentDocument}: primary-key derivation and the {@link ContentDocument#toMap()}
 * mapping used when indexing.
 */
@DisplayName("Content document")
class ContentDocumentTest {

    private static ContentDocument fullyPopulated() {
        return new ContentDocument(
            ContentDocument.id("open-elements", "https://open-elements.com/posts/x"),
            "open-elements", "en", "https://open-elements.com/posts/x",
            "Title", "Excerpt", "Body text", "hendrik",
            List.of("ai", "web3"), "2026-03-12", "2026-03-12T10:00:00Z",
            "/posts/preview.svg");
    }

    @Test
    @DisplayName("id() is deterministic for the same source and url")
    void idIsDeterministic() {
        String first = ContentDocument.id("open-elements", "https://open-elements.com/posts/x");
        String second = ContentDocument.id("open-elements", "https://open-elements.com/posts/x");
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("id() differs when the url differs")
    void differentUrlYieldsDifferentId() {
        String a = ContentDocument.id("open-elements", "https://open-elements.com/posts/a");
        String b = ContentDocument.id("open-elements", "https://open-elements.com/posts/b");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("id() contains only Meilisearch-legal characters for a URL with / : .")
    void idIsAValidMeilisearchPrimaryKey() {
        String id = ContentDocument.id("open-elements", "https://open-elements.com/posts/2026/03/12/a.b");
        assertThat(id).matches("[A-Za-z0-9_-]+");
    }

    @Test
    @DisplayName("toMap() contains every indexed field")
    void toMapContainsAllIndexedFields() {
        Map<String, Object> map = fullyPopulated().toMap();
        assertThat(map).containsOnlyKeys(
            "id", "source", "locale", "url", "title", "excerpt", "body",
            "author", "categories", "publishedDate", "lastmod", "previewImage");
        assertThat(map).containsEntry("source", "open-elements");
        assertThat(map).containsEntry("categories", List.of("ai", "web3"));
    }

    @Test
    @DisplayName("toMap() keeps all keys when optional fields are null")
    void nullOptionalFieldsMapCleanly() {
        ContentDocument doc = new ContentDocument(
            ContentDocument.id("open-elements", "https://open-elements.com/posts/x"),
            "open-elements", "en", "https://open-elements.com/posts/x",
            "Title", "Excerpt", "Body", null, null, "2026-03-12", "2026-03-12T10:00:00Z", null);

        Map<String, Object> map = doc.toMap();
        assertThat(map).containsKey("author").containsKey("previewImage");
        assertThat(map.get("author")).isNull();
        assertThat(map.get("previewImage")).isNull();
        // categories is normalized to an empty list rather than null.
        assertThat(map.get("categories")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("categories is normalized to an empty list when null")
    void categoriesNormalizedWhenNull() {
        ContentDocument doc = new ContentDocument(
            "id", "s", "en", "u", "t", "e", "b", "a", null, "2026-03-12", "2026-03-12T10:00:00Z", null);
        assertThat(doc.categories()).isEmpty();
    }
}
