package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that jsoup is on the classpath. It is unused until spec 006, but the dependency is added
 * now so the dependency graph is stable early.
 *
 * <p>Covers behavior: "jsoup is on the classpath".
 */
@DisplayName("jsoup on the classpath")
class JsoupClasspathTest {

    @Test
    @DisplayName("jsoup is available and parses HTML")
    void jsoupIsAvailableAndParsesHtml() {
        Document document = Jsoup.parse("<html><body><p>Open Elements</p></body></html>");
        assertThat(document.body().text()).isEqualTo("Open Elements");
    }
}
