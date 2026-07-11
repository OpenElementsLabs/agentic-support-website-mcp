package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ContentLocaleResolver}'s path-prefix locale rule.
 */
@DisplayName("Locale derivation")
class ContentLocaleResolverTest {

    private final ContentLocaleResolver resolver = new ContentLocaleResolver();

    @Test
    @DisplayName("a /de path is German")
    void germanPrefixIsGerman() {
        assertThat(resolver.resolve("/de/posts/2026/03/slug")).isEqualTo(Locale.GERMAN);
        assertThat(resolver.resolve("/de")).isEqualTo(Locale.GERMAN);
        assertThat(resolver.resolve("https://open-elements.com/de/posts/x")).isEqualTo(Locale.GERMAN);
    }

    @Test
    @DisplayName("any other path defaults to English")
    void otherPathsAreEnglish() {
        assertThat(resolver.resolve("/posts/2026/03/slug")).isEqualTo(Locale.ENGLISH);
        assertThat(resolver.resolve("/")).isEqualTo(Locale.ENGLISH);
        assertThat(resolver.resolve("https://open-elements.com/posts/x")).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("a segment merely starting with 'de' is not treated as German")
    void deSubstringIsNotGerman() {
        assertThat(resolver.resolve("/design/posts")).isEqualTo(Locale.ENGLISH);
        assertThat(resolver.resolve("/development")).isEqualTo(Locale.ENGLISH);
    }
}
