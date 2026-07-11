package com.openelements.content;

import com.openelements.spring.base.services.search.IndexSettings;
import com.openelements.spring.base.services.search.MeilisearchProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central configuration for the content-specific part of the application.
 *
 * <p>Enables {@link ContentSourceProperties} binding and is component-scanned together with the rest
 * of the {@code com.openelements.content} package (e.g. {@link UrlMatcher},
 * {@link ContentLocaleResolver}). It also declares the Meilisearch {@link IndexSettings} for the
 * content index. Later specs extend it with further {@code @Bean} definitions (crawler, indexer,
 * search service, MCP tools).
 */
@Configuration
@EnableConfigurationProperties(ContentSourceProperties.class)
public class ContentConfig {

    /**
     * The Meilisearch index schema for content documents. The library's
     * {@code MeilisearchIndexSettingsInitializer} picks up every {@link IndexSettings} bean and
     * applies it to the index at startup, so no manual {@code updateSettings} call is needed.
     *
     * <p>Searchable attribute order encodes ranking weight ({@code title > excerpt > body}). The
     * {@code publishedDate:desc} tie-breaker is applied at query time in the search service
     * (spec 011), not as a custom ranking rule here.
     *
     * <p>Only created when the Meilisearch stack is enabled, matching the condition under which
     * {@link MeilisearchProperties} is available.
     *
     * @param meilisearchProperties library properties, used to resolve the prefixed index UID
     * @return the content index settings
     */
    @Bean
    @ConditionalOnProperty(prefix = "openelements.meilisearch", name = "enabled", havingValue = "true")
    IndexSettings contentIndexSettings(MeilisearchProperties meilisearchProperties) {
        return new IndexSettings(
            meilisearchProperties.resolveIndex("content"),
            "id",
            List.of("title", "excerpt", "body"),
            List.of("source", "locale", "author", "categories", "publishedDate"),
            List.of("publishedDate"));
    }
}
