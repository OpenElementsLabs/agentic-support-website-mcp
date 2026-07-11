package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.search.IndexSettings;
import com.openelements.spring.base.services.search.MeilisearchProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies the {@link IndexSettings} bean that {@link ContentConfig} contributes for the content
 * index — its attributes, primary key, and prefix-aware index UID.
 *
 * <p>Whether the settings are actually written to a live index at startup is the responsibility of
 * the library's {@code MeilisearchIndexSettingsInitializer} (which consumes every {@code IndexSettings}
 * bean); that path requires a running Meilisearch and is not reproduced here.
 */
@DisplayName("Content index settings")
class ContentIndexSettingsTest {

    private ApplicationContextRunner runnerWithPrefix(String indexPrefix) {
        MeilisearchProperties properties =
            new MeilisearchProperties("http://localhost:7700", "", indexPrefix, Duration.ofSeconds(10));
        return new ApplicationContextRunner()
            .withBean(MeilisearchProperties.class, () -> properties)
            .withUserConfiguration(ContentConfig.class)
            .withPropertyValues("openelements.meilisearch.enabled=true");
    }

    @Test
    @DisplayName("the bean declares the configured searchable, filterable and sortable attributes")
    void attributesAreConfigured() {
        runnerWithPrefix("").run(context -> {
            IndexSettings settings = context.getBean(IndexSettings.class);
            assertThat(settings.primaryKey()).isEqualTo("id");
            assertThat(settings.searchableAttributes()).containsExactly("title", "excerpt", "body");
            assertThat(settings.filterableAttributes())
                .containsExactly("source", "locale", "author", "categories", "publishedDate");
            assertThat(settings.sortableAttributes()).containsExactly("publishedDate");
        });
    }

    @Test
    @DisplayName("the index UID honors the configured index prefix")
    void indexUidHonorsPrefix() {
        runnerWithPrefix("content_").run(context -> {
            IndexSettings settings = context.getBean(IndexSettings.class);
            assertThat(settings.indexUid()).isEqualTo("content_content");
        });
    }

    @Test
    @DisplayName("no settings bean is created when Meilisearch is disabled")
    void noBeanWhenMeilisearchDisabled() {
        new ApplicationContextRunner()
            .withBean(MeilisearchProperties.class,
                () -> new MeilisearchProperties("http://localhost:7700", "", "", Duration.ofSeconds(10)))
            .withUserConfiguration(ContentConfig.class)
            .withPropertyValues("openelements.meilisearch.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(IndexSettings.class));
    }
}
