package com.openelements.content;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional search-tuning settings bound from {@code open-elements.content.search.*} (spec 017).
 *
 * <p>Applied to the Meilisearch index at startup by {@link SearchSettingsInitializer}. Both are
 * empty by default, in which case nothing is pushed.
 *
 * @param synonyms word → equivalent terms (Meilisearch {@code synonyms}); e.g. {@code ai: [artificial intelligence]}
 * @param stopWords words ignored in ranking/matching (Meilisearch {@code stopWords})
 */
@ConfigurationProperties("open-elements.content.search")
public record ContentSearchProperties(
    Map<String, List<String>> synonyms,
    List<String> stopWords
) {

    public ContentSearchProperties {
        synonyms = synonyms == null ? Map.of() : Map.copyOf(synonyms);
        stopWords = stopWords == null ? List.of() : List.copyOf(stopWords);
    }

    /** @return {@code true} if there is anything to push to the index */
    public boolean hasSettings() {
        return !synonyms.isEmpty() || !stopWords.isEmpty();
    }
}
