package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SourceStrategyRegistry} using lightweight test-double strategies.
 */
@DisplayName("Source strategy registry")
class SourceStrategyRegistryTest {

    private static ContentSource source(SourceType type) {
        return new ContentSource(
            "s", type, "https://ex.com", List.of(), List.of("/**"), List.of(), "article", List.of(), true, null);
    }

    private static ContentSourceStrategy strategyFor(SourceType type) {
        return new ContentSourceStrategy() {
            @Override
            public SourceType type() {
                return type;
            }

            @Override
            public List<DiscoveredItem> discover(ContentSource src) {
                return List.of();
            }

            @Override
            public FetchOutcome fetch(ContentSource src, DiscoveredItem item) {
                return FetchOutcome.skip();
            }
        };
    }

    @Test
    @DisplayName("a WEBSITE source resolves to the website strategy")
    void websiteSourceResolvesToWebsiteStrategy() {
        ContentSourceStrategy website = strategyFor(SourceType.WEBSITE);
        SourceStrategyRegistry registry = new SourceStrategyRegistry(List.of(website));

        assertThat(registry.forSource(source(SourceType.WEBSITE))).isSameAs(website);
    }

    @Test
    @DisplayName("an unregistered type fails with a clear error naming the type")
    void unknownTypeFailsClearly() {
        SourceStrategyRegistry registry = new SourceStrategyRegistry(List.of(strategyFor(SourceType.WEBSITE)));

        assertThatThrownBy(() -> registry.forSource(source(SourceType.GIT)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("GIT");
    }

    @Test
    @DisplayName("an additional strategy bean is auto-discovered and indexed by its type")
    void newStrategyIsAutoDiscovered() {
        ContentSourceStrategy git = strategyFor(SourceType.GIT);
        SourceStrategyRegistry registry =
            new SourceStrategyRegistry(List.of(strategyFor(SourceType.WEBSITE), git));

        assertThat(registry.forSource(source(SourceType.GIT))).isSameAs(git);
    }

    @Test
    @DisplayName("two strategies for the same type fail fast")
    void duplicateStrategiesFailFast() {
        assertThatThrownBy(() -> new SourceStrategyRegistry(
            List.of(strategyFor(SourceType.WEBSITE), strategyFor(SourceType.WEBSITE))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("WEBSITE");
    }
}
