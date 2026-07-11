package com.openelements.content;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Derives the locale of a content URL from its path.
 *
 * <p>This is the deliberately simple, path-prefix rule described in the design: a path under
 * {@code /de} is German, everything else is English. It is kept behind this small component so the
 * rule can grow (e.g. per-source configuration or additional locales) without touching callers —
 * the exact shape is an open question revisited if a third locale appears (specs 006/007).
 *
 * <p>Named {@code ContentLocaleResolver} rather than {@code LocaleResolver} to avoid the reserved
 * Spring MVC bean name {@code localeResolver} (which must be a {@code org.springframework.web.servlet.LocaleResolver}).
 */
@Component
public class ContentLocaleResolver {

    /** Locale used for German content. */
    public static final Locale GERMAN = Locale.GERMAN;

    /** Locale used for all content that is not matched by a more specific rule. */
    public static final Locale DEFAULT = Locale.ENGLISH;

    private static final String GERMAN_PREFIX = "/de";

    /**
     * @param url an absolute URL or a bare path
     * @return {@link #GERMAN} if the path is under {@code /de}, otherwise {@link #DEFAULT}
     */
    public Locale resolve(String url) {
        String path = normalizePath(url);
        boolean german = path.equals(GERMAN_PREFIX) || path.startsWith(GERMAN_PREFIX + "/");
        return german ? GERMAN : DEFAULT;
    }

    private static String normalizePath(String url) {
        String path;
        try {
            path = new URI(url).getPath();
        } catch (URISyntaxException e) {
            int cut = url.indexOf('?');
            path = cut >= 0 ? url.substring(0, cut) : url;
        }
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
