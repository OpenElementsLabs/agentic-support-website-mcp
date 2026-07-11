package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Verifies that {@link ContentSourceProperties} binds from {@code open-elements.content.*} and that
 * the record's normalization (defaults for omitted values) works as specified.
 */
@DisplayName("Content source properties binding")
class ContentSourcePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("a website source binds from YAML with all its fields parsed")
    void websiteSourceBindsFromYaml() {
        runner.withPropertyValues(
            "open-elements.content.enabled=true",
            "open-elements.content.sources[0].id=open-elements",
            "open-elements.content.sources[0].type=website",
            "open-elements.content.sources[0].base-url=https://open-elements.com",
            "open-elements.content.sources[0].sitemaps[0]=/en/sitemap.xml",
            "open-elements.content.sources[0].sitemaps[1]=/de/sitemap.xml",
            "open-elements.content.sources[0].url-include[0]=/posts/**",
            "open-elements.content.sources[0].url-include[1]=/de/posts/**",
            "open-elements.content.sources[0].content-selector=article",
            "open-elements.content.sources[0].enabled=true"
        ).run(context -> {
            ContentSourceProperties props = context.getBean(ContentSourceProperties.class);
            assertThat(props.sources()).hasSize(1);
            ContentSource source = props.sources().get(0);
            assertThat(source.id()).isEqualTo("open-elements");
            assertThat(source.type()).isEqualTo(SourceType.WEBSITE);
            assertThat(source.baseUrl()).isEqualTo("https://open-elements.com");
            assertThat(source.sitemaps()).containsExactly("/en/sitemap.xml", "/de/sitemap.xml");
            assertThat(source.urlInclude()).containsExactly("/posts/**", "/de/posts/**");
            assertThat(source.contentSelector()).isEqualTo("article");
            assertThat(source.enabled()).isTrue();
        });
    }

    @Test
    @DisplayName("an omitted url-include defaults to [\"/**\"]")
    void omittedUrlIncludeDefaultsToMatchAll() {
        runner.withPropertyValues(
            "open-elements.content.sources[0].id=support-care",
            "open-elements.content.sources[0].type=website",
            "open-elements.content.sources[0].base-url=https://open-elements.com",
            "open-elements.content.sources[0].enabled=true"
        ).run(context -> {
            ContentSource source = context.getBean(ContentSourceProperties.class).sources().get(0);
            assertThat(source.urlInclude()).containsExactly("/**");
        });
    }

    @Test
    @DisplayName("a source declared with enabled=false is bound as disabled")
    void disabledSourceIsRepresented() {
        runner.withPropertyValues(
            "open-elements.content.sources[0].id=paused",
            "open-elements.content.sources[0].type=website",
            "open-elements.content.sources[0].base-url=https://open-elements.com",
            "open-elements.content.sources[0].enabled=false"
        ).run(context -> {
            ContentSource source = context.getBean(ContentSourceProperties.class).sources().get(0);
            assertThat(source.enabled()).isFalse();
        });
    }

    @Test
    @DisplayName("omitted global settings fall back to sensible defaults")
    void globalSettingsFallBackToDefaults() {
        runner.withPropertyValues("open-elements.content.enabled=true").run(context -> {
            ContentSourceProperties props = context.getBean(ContentSourceProperties.class);
            assertThat(props.refreshCron()).isEqualTo("0 0 * * * *");
            assertThat(props.userAgent()).isEqualTo("OpenElementsContentBot/1.0 (+https://open-elements.com)");
            assertThat(props.rateLimitPerHost()).isEqualTo(2.0);
            assertThat(props.requestTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(props.maxBodyBytes()).isEqualTo(DataSize.ofMegabytes(5));
            assertThat(props.sources()).isEmpty();
        });
    }

    @Test
    @DisplayName("global settings bind from YAML when provided")
    void globalSettingsBindWhenProvided() {
        runner.withPropertyValues(
            "open-elements.content.enabled=true",
            "open-elements.content.refresh-cron=0 30 * * * *",
            "open-elements.content.user-agent=CustomBot/2.0",
            "open-elements.content.rate-limit-per-host=5",
            "open-elements.content.request-timeout=20s",
            "open-elements.content.max-body-bytes=8MB"
        ).run(context -> {
            ContentSourceProperties props = context.getBean(ContentSourceProperties.class);
            assertThat(props.refreshCron()).isEqualTo("0 30 * * * *");
            assertThat(props.userAgent()).isEqualTo("CustomBot/2.0");
            assertThat(props.rateLimitPerHost()).isEqualTo(5.0);
            assertThat(props.requestTimeout()).isEqualTo(Duration.ofSeconds(20));
            assertThat(props.maxBodyBytes()).isEqualTo(DataSize.ofMegabytes(8));
        });
    }

    @Configuration
    @EnableConfigurationProperties(ContentSourceProperties.class)
    static class TestConfig {
    }
}
