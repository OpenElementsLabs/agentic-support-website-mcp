package com.openelements.content;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Discovers the candidate URLs of a {@code website} {@link ContentSource}.
 *
 * <p>The primary strategy reads the source's configured sitemaps: each sitemap is fetched and, if it
 * is a {@code <sitemapindex>}, its child sitemaps are followed recursively; {@code <urlset>} entries
 * contribute their {@code <loc>} and optional {@code <lastmod>}. Every discovered URL is filtered
 * through the source's Ant-glob include/exclude rules via {@link UrlMatcher}.
 *
 * <p>When a source declares no sitemaps, discovery falls back to a bounded, same-host link-following
 * crawl from the {@code baseUrl}: it follows links up to {@link #MAX_FALLBACK_DEPTH} levels deep,
 * guarded by a visited-set (so cycles terminate) and a total-page cap.
 *
 * <p>Discovery is fault-tolerant: a sitemap that cannot be fetched or parsed is logged and skipped;
 * it never aborts discovery of the other sitemaps or the source as a whole.
 */
@Component
public class SitemapCrawler {

    private static final Logger log = LoggerFactory.getLogger(SitemapCrawler.class);

    /** Maximum link-following depth for the no-sitemap fallback crawl. */
    static final int MAX_FALLBACK_DEPTH = 3;

    /** Safety cap on the number of pages fetched during a fallback crawl. */
    static final int MAX_FALLBACK_PAGES = 200;

    /** Guard against pathologically nested (or self-referential) sitemap indexes. */
    private static final int MAX_SITEMAP_DEPTH = 10;

    private final RestClient restClient;
    private final UrlMatcher urlMatcher;
    private final RobotsPolicy robotsPolicy;

    public SitemapCrawler(RestClient.Builder restClientBuilder, UrlMatcher urlMatcher, RobotsPolicy robotsPolicy) {
        this.restClient = restClientBuilder.build();
        this.urlMatcher = urlMatcher;
        this.robotsPolicy = robotsPolicy;
    }

    /**
     * Discovers the in-scope URLs of a source.
     *
     * @param source the source to crawl
     * @return the discovered items whose URL path is included by the source (order preserved, deduplicated)
     */
    public List<DiscoveredItem> discover(ContentSource source) {
        if (source.sitemaps().isEmpty()) {
            return fallbackCrawl(source);
        }

        Map<String, DiscoveredItem> collected = new LinkedHashMap<>();
        Set<String> visitedSitemaps = new HashSet<>();
        for (String sitemapPath : source.sitemaps()) {
            String sitemapUrl = resolve(source.baseUrl(), sitemapPath);
            collectSitemap(sitemapUrl, collected, visitedSitemaps, 0);
        }

        return collected.values().stream()
            .filter(item -> urlMatcher.matches(source, item.url()))
            .filter(this::allowedByRobots)
            .toList();
    }

    private boolean allowedByRobots(DiscoveredItem item) {
        if (robotsPolicy.isAllowed(item.url())) {
            return true;
        }
        log.info("Skipping {} — disallowed by robots.txt", item.url());
        return false;
    }

    private void collectSitemap(String sitemapUrl, Map<String, DiscoveredItem> collected,
                                Set<String> visitedSitemaps, int depth) {
        if (depth > MAX_SITEMAP_DEPTH || !visitedSitemaps.add(sitemapUrl)) {
            return;
        }
        String xml;
        try {
            xml = fetch(sitemapUrl);
        } catch (Exception e) {
            log.warn("Skipping unreachable sitemap {}: {}", sitemapUrl, e.toString());
            return;
        }
        try {
            Document doc = Jsoup.parse(xml, sitemapUrl, Parser.xmlParser());
            var childSitemaps = doc.select("sitemapindex > sitemap > loc");
            if (!childSitemaps.isEmpty()) {
                for (Element loc : childSitemaps) {
                    String childUrl = loc.text().trim();
                    if (childUrl.isEmpty()) {
                        continue;
                    }
                    // Only follow child sitemaps on the same host, so a sitemap index cannot point the
                    // crawler at arbitrary (e.g. internal) hosts.
                    if (!hostOf(childUrl).equalsIgnoreCase(hostOf(sitemapUrl))) {
                        log.warn("Skipping off-host child sitemap {} (parent {})", childUrl, sitemapUrl);
                        continue;
                    }
                    collectSitemap(childUrl, collected, visitedSitemaps, depth + 1);
                }
                return;
            }
            for (Element urlEntry : doc.select("urlset > url")) {
                Element loc = urlEntry.selectFirst("loc");
                if (loc == null || loc.text().trim().isEmpty()) {
                    continue;
                }
                String url = loc.text().trim();
                Element lastmod = urlEntry.selectFirst("lastmod");
                String lastmodValue = lastmod == null || lastmod.text().trim().isEmpty()
                    ? null : lastmod.text().trim();
                collected.putIfAbsent(url, new DiscoveredItem(url, lastmodValue));
            }
        } catch (Exception e) {
            log.warn("Skipping malformed sitemap {}: {}", sitemapUrl, e.toString());
        }
    }

    private List<DiscoveredItem> fallbackCrawl(ContentSource source) {
        String host = hostOf(source.baseUrl());
        Map<String, DiscoveredItem> results = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        Deque<CrawlEntry> queue = new ArrayDeque<>();
        queue.add(new CrawlEntry(source.baseUrl(), 0));

        while (!queue.isEmpty() && visited.size() < MAX_FALLBACK_PAGES) {
            CrawlEntry entry = queue.poll();
            if (!visited.add(entry.url())) {
                continue;
            }
            String html;
            try {
                html = fetch(entry.url());
            } catch (Exception e) {
                log.warn("Skipping unreachable page {}: {}", entry.url(), e.toString());
                continue;
            }
            Document doc = Jsoup.parse(html, entry.url());
            for (Element anchor : doc.select("a[href]")) {
                String link = stripFragment(anchor.absUrl("href"));
                if (link.isEmpty() || !host.equalsIgnoreCase(hostOf(link))) {
                    continue;
                }
                if (urlMatcher.matches(source, link) && allowedByRobots(new DiscoveredItem(link, null))) {
                    results.putIfAbsent(link, new DiscoveredItem(link, null));
                }
                if (entry.depth() < MAX_FALLBACK_DEPTH && !visited.contains(link)) {
                    queue.add(new CrawlEntry(link, entry.depth() + 1));
                }
            }
        }
        if (visited.size() >= MAX_FALLBACK_PAGES) {
            log.warn("Fallback crawl of {} hit the {}-page cap; discovery may be incomplete",
                source.baseUrl(), MAX_FALLBACK_PAGES);
        }
        return List.copyOf(results.values());
    }

    private String fetch(String url) {
        return restClient.get().uri(URI.create(url)).retrieve().body(String.class);
    }

    private static String resolve(String baseUrl, String path) {
        return URI.create(baseUrl).resolve(path).toString();
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String stripFragment(String url) {
        int hash = url.indexOf('#');
        return hash >= 0 ? url.substring(0, hash) : url;
    }

    private record CrawlEntry(String url, int depth) {
    }
}
