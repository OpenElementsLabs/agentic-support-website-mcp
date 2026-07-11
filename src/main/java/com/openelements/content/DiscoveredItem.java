package com.openelements.content;

/**
 * A single URL discovered for a {@link ContentSource}, together with its change marker.
 *
 * <p>Produced by discovery (the {@link SitemapCrawler} for websites, and the Git strategy in
 * spec 016) and consumed by the fetch stage (spec 005). The {@code lastmod} is the sitemap
 * {@code <lastmod>} value when available and {@code null} otherwise (e.g. for pages found by the
 * fallback crawl) — a {@code null} marker means the fetch stage cannot skip via a diff and will
 * always fetch the page.
 *
 * @param url     the absolute URL of the discovered item
 * @param lastmod the last-modified marker (ISO datetime), or {@code null} if unknown
 */
public record DiscoveredItem(String url, String lastmod) {
}
