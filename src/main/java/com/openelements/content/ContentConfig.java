package com.openelements.content;

import com.openelements.spring.base.services.search.IndexSettings;
import com.openelements.spring.base.services.search.MeilisearchProperties;
import com.openelements.spring.base.services.search.ScopedKeySpec;
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
 * {@link ContentLocaleResolver}, {@link SitemapCrawler}, {@link PageFetcher}). It also declares the
 * Meilisearch {@link IndexSettings} for the content index. The HTTP client and rate limiter used by
 * {@link PageFetcher} live in {@link ContentHttpConfig}. Later specs extend it with further
 * {@code @Bean} definitions (indexer, search service, MCP tools).
 */
@Configuration
@EnableConfigurationProperties({ContentSourceProperties.class, ContentSearchProperties.class})
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

    /**
     * The Meilisearch actions this service performs on the content index. The runtime key is scoped
     * to exactly these — a large reduction from the master key (one index, no admin/key-management,
     * no other indexes) even though the process writes (bootstrap + scheduler) as well as reads.
     */
    static final List<String> CONTENT_INDEX_ACTIONS = List.of(
        "search",
        "documents.add", "documents.delete", "documents.get",
        "settings.get", "settings.update",
        "indexes.get", "indexes.create",
        "tasks.get");

    /**
     * Scopes the runtime Meilisearch key to the content index with {@link #CONTENT_INDEX_ACTIONS}.
     * The library's {@code MeilisearchScopedKeyInitializer} picks up this optional bean at startup,
     * exchanges the master key for the scoped key, and switches the client to it; if the exchange
     * fails (e.g. Meilisearch unreachable), it logs a warning and keeps the master key.
     *
     * @param meilisearchProperties library properties, used to resolve the prefixed index UID
     * @return the scoped-key specification
     */
    @Bean
    @ConditionalOnProperty(prefix = "openelements.meilisearch", name = "enabled", havingValue = "true")
    ScopedKeySpec contentScopedKey(MeilisearchProperties meilisearchProperties) {
        return new ScopedKeySpec(
            List.of(meilisearchProperties.resolveIndex("content")),
            CONTENT_INDEX_ACTIONS);
    }
}
