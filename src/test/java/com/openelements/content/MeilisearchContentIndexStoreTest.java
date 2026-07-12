package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MeilisearchContentIndexStore#parseHits(JsonNode)} — the parsing of a
 * Meilisearch multi-search response, verifiable without a running Meilisearch.
 */
@DisplayName("Meilisearch index store — hit parsing")
class MeilisearchContentIndexStoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode json(String raw) throws Exception {
        return objectMapper.readTree(raw);
    }

    @Test
    @DisplayName("hits are parsed into id -> (url, lastmod), tolerating a missing lastmod")
    void parsesHits() throws Exception {
        JsonNode response = json("""
            {"results":[{"hits":[
              {"id":"oe_abc","url":"https://ex.com/a","lastmod":"2026-01-01"},
              {"id":"oe_def","url":"https://ex.com/b"}
            ]}]}""");

        Map<String, ContentIndexStore.StoredDocument> state = MeilisearchContentIndexStore.parseHits(response);

        assertThat(state).hasSize(2);
        assertThat(state.get("oe_abc")).isEqualTo(
            new ContentIndexStore.StoredDocument("https://ex.com/a", "2026-01-01"));
        assertThat(state.get("oe_def")).isEqualTo(
            new ContentIndexStore.StoredDocument("https://ex.com/b", null));
    }

    @Test
    @DisplayName("an empty result set yields an empty map")
    void parsesEmptyResults() throws Exception {
        assertThat(MeilisearchContentIndexStore.parseHits(json("{\"results\":[{\"hits\":[]}]}"))).isEmpty();
        assertThat(MeilisearchContentIndexStore.parseHits(json("{\"results\":[]}"))).isEmpty();
    }

    @Test
    @DisplayName("hits without an id are ignored")
    void ignoresHitsWithoutId() throws Exception {
        JsonNode response = json("""
            {"results":[{"hits":[
              {"url":"https://ex.com/no-id"},
              {"id":"oe_ok","url":"https://ex.com/ok"}
            ]}]}""");

        Map<String, ContentIndexStore.StoredDocument> state = MeilisearchContentIndexStore.parseHits(response);

        assertThat(state).containsOnlyKeys("oe_ok");
    }
}
