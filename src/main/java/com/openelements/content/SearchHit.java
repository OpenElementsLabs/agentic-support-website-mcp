package com.openelements.content;

/**
 * One search or list result: the display fields plus a safe, highlighted snippet.
 *
 * @param title         the document title
 * @param url           the canonical URL
 * @param publishedDate the published date
 * @param snippet       an HTML-safe snippet with {@code <em>} around matches (may be empty)
 * @param score         the ranking score (0 when not available)
 */
public record SearchHit(String title, String url, String publishedDate, String snippet, double score) {
}
