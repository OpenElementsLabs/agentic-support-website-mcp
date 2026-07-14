package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openelements.spring.base.services.search.MeilisearchClient;
import com.openelements.spring.base.services.search.MeilisearchProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SearchSettingsInitializer} using a capturing {@link MeilisearchClient}
 * subclass — verifies the synonym/stop-word settings are pushed via {@code updateSettings} (no
 * reindex) and that it is a no-op when nothing is configured.
 */
@DisplayName("Search settings initializer")
class SearchSettingsInitializerTest {

    private static MeilisearchProperties meilisearch() {
        return new MeilisearchProperties("http://localhost:7700", "", "content_", Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("configured synonyms and stop words are pushed to the index via updateSettings")
    void appliesConfiguredSettings() {
        ContentSearchProperties properties = new ContentSearchProperties(
            Map.of("ai", List.of("artificial intelligence")), List.of("the", "a"));
        CapturingClient client = new CapturingClient();

        new SearchSettingsInitializer(client, properties, meilisearch()).run(null);

        assertThat(client.calls).isEqualTo(1);
        assertThat(client.lastIndex).isEqualTo("content_content");
        assertThat(client.lastSettings).containsKey("synonyms").containsKey("stopWords");
        assertThat(client.lastSettings.get("stopWords")).isEqualTo(List.of("the", "a"));
    }

    @Test
    @DisplayName("nothing is pushed when no synonyms or stop words are configured")
    void noOpWhenEmpty() {
        CapturingClient client = new CapturingClient();

        new SearchSettingsInitializer(client, new ContentSearchProperties(Map.of(), List.of()), meilisearch())
            .run(null);

        assertThat(client.calls).isZero();
    }

    @Test
    @DisplayName("buildSettings includes only the configured keys")
    void buildSettingsIncludesOnlyConfigured() {
        Map<String, Object> onlySynonyms = SearchSettingsInitializer.buildSettings(
            new ContentSearchProperties(Map.of("ai", List.of("ml")), List.of()));
        assertThat(onlySynonyms).containsOnlyKeys("synonyms");

        Map<String, Object> onlyStopWords = SearchSettingsInitializer.buildSettings(
            new ContentSearchProperties(Map.of(), List.of("the")));
        assertThat(onlyStopWords).containsOnlyKeys("stopWords");
    }

    /** Test double capturing the updateSettings call. */
    private static final class CapturingClient extends MeilisearchClient {
        private int calls;
        private String lastIndex;
        private Map<String, Object> lastSettings;

        CapturingClient() {
            super(new MeilisearchProperties("http://localhost:7700", "", "content_", Duration.ofSeconds(10)),
                new ObjectMapper());
        }

        @Override
        public long updateSettings(String indexUid, Map<String, Object> settings) {
            calls++;
            lastIndex = indexUid;
            lastSettings = settings;
            return 1L;
        }
    }
}
