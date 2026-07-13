package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that the website sources are bound from {@code application.yaml} with no code — the point
 * of spec 015 (hiero.org/blog and Support & Care are added purely as configuration).
 *
 * <p>Shares the application context of the other {@code api-key.enabled=false} tests. The actual crawl
 * and extraction against the live sites are verified manually against a dev index.
 */
@SpringBootTest(properties = "openelements.mcp.auth.api-key.enabled=false")
@DisplayName("Configured website sources")
class ConfiguredSourcesTest {

    @Autowired
    private ContentSourceProperties properties;

    private Map<String, ContentSource> sourcesById() {
        return properties.sources().stream()
            .collect(Collectors.toMap(ContentSource::id, Function.identity()));
    }

    @Test
    @DisplayName("open-elements, hiero and support-and-care are all configured")
    void allSourcesAreConfigured() {
        assertThat(sourcesById()).containsKeys("open-elements", "hiero", "support-and-care");
    }

    @Test
    @DisplayName("the hiero source targets /blog via the sitemap with the article selector")
    void hieroSourceIsConfigured() {
        ContentSource hiero = sourcesById().get("hiero");
        assertThat(hiero.type()).isEqualTo(SourceType.WEBSITE);
        assertThat(hiero.baseUrl()).isEqualTo("https://hiero.org");
        assertThat(hiero.sitemaps()).containsExactly("/sitemap.xml");
        assertThat(hiero.urlInclude()).containsExactly("/blog/**");
        assertThat(hiero.contentSelector()).isEqualTo("main article");
        assertThat(hiero.enabled()).isTrue();
    }

    @Test
    @DisplayName("the support-and-care source scopes to the /en/support-care pages with body + excludes")
    void supportAndCareSourceIsConfigured() {
        ContentSource support = sourcesById().get("support-and-care");
        assertThat(support.baseUrl()).isEqualTo("https://open-elements.com");
        assertThat(support.urlInclude()).contains("/en/support-care*");
        assertThat(support.contentSelector()).isEqualTo("body");
        assertThat(support.contentExclude()).contains("nav", "header", "footer", ".cookie-banner");
    }
}
