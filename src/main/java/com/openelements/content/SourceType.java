package com.openelements.content;

/**
 * The kind of a {@link ContentSource}, which determines how its content is discovered and extracted.
 *
 * <ul>
 *   <li>{@link #WEBSITE} — discovery via sitemap/HTML, extraction via jsoup (the only type used in
 *       phases 1 and 2).
 *   <li>{@link #GIT} — discovery via a Git repository listing, extraction from Markdown files
 *       (planned; see spec 016).
 * </ul>
 */
public enum SourceType {
    WEBSITE,
    GIT
}
