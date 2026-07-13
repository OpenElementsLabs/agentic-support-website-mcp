package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.services.search.MeilisearchProperties;
import com.openelements.spring.base.services.search.ScopedKeySpec;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies the {@link ScopedKeySpec} bean {@link ContentConfig} contributes — it scopes the runtime
 * Meilisearch key to the content index with the actions the service actually uses.
 *
 * <p>The actual key exchange is performed by the library's {@code MeilisearchScopedKeyInitializer}
 * against a live Meilisearch and is not reproduced here.
 */
@DisplayName("Content scoped key")
class ContentScopedKeyTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(MeilisearchProperties.class,
            () -> new MeilisearchProperties("http://localhost:7700", "", "content_", Duration.ofSeconds(10)))
        .withUserConfiguration(ContentConfig.class)
        .withPropertyValues("openelements.meilisearch.enabled=true");

    @Test
    @DisplayName("the scoped key is limited to the content index")
    void scopedKeyTargetsContentIndex() {
        runner.run(context -> assertThat(context.getBean(ScopedKeySpec.class).indexes())
            .containsExactly("content_content"));
    }

    @Test
    @DisplayName("the scoped key grants search plus the write actions used at startup and refresh")
    void scopedKeyGrantsUsedActions() {
        runner.run(context -> assertThat(context.getBean(ScopedKeySpec.class).actions())
            .contains("search", "documents.add", "documents.delete", "settings.update", "tasks.get"));
    }

    @Test
    @DisplayName("no scoped key bean is created when Meilisearch is disabled")
    void noScopedKeyWhenMeilisearchDisabled() {
        new ApplicationContextRunner()
            .withBean(MeilisearchProperties.class,
                () -> new MeilisearchProperties("http://localhost:7700", "", "", Duration.ofSeconds(10)))
            .withUserConfiguration(ContentConfig.class)
            .withPropertyValues("openelements.meilisearch.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(ScopedKeySpec.class));
    }
}
