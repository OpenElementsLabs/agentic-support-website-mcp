package com.openelements.content;

import java.util.List;

/**
 * One declaratively-configured content source (a website or, later, a Git repository).
 *
 * <p>Sources are configured under {@code open-elements.content.sources} in {@code application.yaml};
 * adding a new source requires no code changes. This record is immutable and normalizes its optional
 * list fields to non-null defaults so that consumers never have to null-check them:
 *
 * <ul>
 *   <li>{@code sitemaps}, {@code urlExclude}, {@code contentExclude} default to an empty list.
 *   <li>{@code urlInclude} defaults to {@code ["/**"]} (match everything) when absent — see
 *       {@link UrlMatcher}.
 * </ul>
 *
 * @param id             stable identifier of the source (e.g. {@code open-elements})
 * @param type           discovery/extraction strategy selector; see {@link SourceType}
 * @param baseUrl        base URL of the source (e.g. {@code https://open-elements.com})
 * @param sitemaps       sitemap paths relative to {@code baseUrl} (e.g. {@code /en/sitemap.xml})
 * @param urlInclude     Ant-glob patterns; a URL path must match at least one to be crawled
 * @param urlExclude     Ant-glob patterns; a URL path matching any is skipped (exclude wins)
 * @param contentSelector CSS selector for the main content element (applied in spec 006)
 * @param contentExclude CSS selectors removed before text extraction (applied in spec 006)
 * @param enabled        whether the source is active; consumers in later specs skip disabled sources
 */
public record ContentSource(
    String id,
    SourceType type,
    String baseUrl,
    List<String> sitemaps,
    List<String> urlInclude,
    List<String> urlExclude,
    String contentSelector,
    List<String> contentExclude,
    boolean enabled
) {

    /** The include pattern applied when a source declares no {@code urlInclude}: match everything. */
    public static final List<String> DEFAULT_URL_INCLUDE = List.of("/**");

    public ContentSource {
        sitemaps = sitemaps == null ? List.of() : List.copyOf(sitemaps);
        urlInclude = (urlInclude == null || urlInclude.isEmpty())
            ? DEFAULT_URL_INCLUDE
            : List.copyOf(urlInclude);
        urlExclude = urlExclude == null ? List.of() : List.copyOf(urlExclude);
        contentExclude = contentExclude == null ? List.of() : List.copyOf(contentExclude);
    }
}
