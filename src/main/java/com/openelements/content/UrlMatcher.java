package com.openelements.content;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * Decides whether a URL belongs to a {@link ContentSource}, based on its Ant-glob
 * {@code urlInclude}/{@code urlExclude} patterns.
 *
 * <p>Matching is performed against the <strong>path</strong> part of the URL only — scheme, host,
 * query string, and fragment are ignored. A URL is included if its path matches at least one
 * {@code urlInclude} pattern <em>and</em> no {@code urlExclude} pattern (exclude wins).
 *
 * <p>Pattern semantics follow Spring's {@link AntPathMatcher}: {@code ?} matches one non-{@code /}
 * character, {@code *} matches any characters within a single path segment, and {@code **} matches
 * any number of segments.
 */
@Component
public class UrlMatcher {

    private final AntPathMatcher matcher = new AntPathMatcher();

    /**
     * @param source  the source whose include/exclude patterns are applied
     * @param url     an absolute URL or a bare path (with or without a query string)
     * @return {@code true} if the URL's path is included by the source and not excluded
     */
    public boolean matches(ContentSource source, String url) {
        String path = normalizePath(url);
        boolean included = source.urlInclude().stream().anyMatch(pattern -> matcher.match(pattern, path));
        if (!included) {
            return false;
        }
        return source.urlExclude().stream().noneMatch(pattern -> matcher.match(pattern, path));
    }

    /**
     * Reduces a URL or path to the path segment used for matching: strips scheme/host/query/fragment
     * and guarantees a leading {@code /}.
     */
    private static String normalizePath(String url) {
        String path;
        try {
            path = new URI(url).getPath();
        } catch (URISyntaxException e) {
            // Fall back to a manual strip of the query/fragment for inputs URI cannot parse.
            int cut = indexOfAny(url, '?', '#');
            path = cut >= 0 ? url.substring(0, cut) : url;
        }
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static int indexOfAny(String value, char a, char b) {
        int ia = value.indexOf(a);
        int ib = value.indexOf(b);
        if (ia < 0) {
            return ib;
        }
        if (ib < 0) {
            return ia;
        }
        return Math.min(ia, ib);
    }
}
