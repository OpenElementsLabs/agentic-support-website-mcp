package com.openelements.content;

import com.openelements.spring.base.services.search.MeilisearchClient;
import com.openelements.spring.base.services.search.MeilisearchProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Applies optional {@code synonyms}/{@code stopWords} settings to the content index at startup, via
 * {@code MeilisearchClient.updateSettings} — which the library's {@code IndexSettings} bean
 * (spec 003) deliberately omits. These take effect on subsequent searches without a data reindex.
 *
 * <p>Runs after the library's settings/bootstrap initializers ({@code @Order} 20/30) and is a no-op
 * when no synonyms or stop words are configured. Only created when the Meilisearch stack is enabled;
 * a failure to reach Meilisearch is logged, not fatal.
 */
@Component
@Order(40)
@ConditionalOnProperty(prefix = "openelements.meilisearch", name = "enabled", havingValue = "true")
public class SearchSettingsInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchSettingsInitializer.class);

    private final MeilisearchClient client;
    private final ContentSearchProperties searchProperties;
    private final String indexUid;

    public SearchSettingsInitializer(MeilisearchClient client, ContentSearchProperties searchProperties,
                                     MeilisearchProperties meilisearchProperties) {
        this.client = client;
        this.searchProperties = searchProperties;
        this.indexUid = meilisearchProperties.resolveIndex("content");
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!searchProperties.hasSettings()) {
            return;
        }
        try {
            client.updateSettings(indexUid, buildSettings(searchProperties));
            log.info("Applied search settings to {}: {} synonym(s), {} stop word(s)",
                indexUid, searchProperties.synonyms().size(), searchProperties.stopWords().size());
        } catch (Exception e) {
            log.warn("Could not apply search settings to {}: {}", indexUid, e.toString());
        }
    }

    /** Builds the Meilisearch settings payload for the configured synonyms/stop words. */
    static Map<String, Object> buildSettings(ContentSearchProperties properties) {
        Map<String, Object> settings = new LinkedHashMap<>();
        if (!properties.synonyms().isEmpty()) {
            settings.put("synonyms", properties.synonyms());
        }
        if (!properties.stopWords().isEmpty()) {
            settings.put("stopWords", properties.stopWords());
        }
        return settings;
    }
}
