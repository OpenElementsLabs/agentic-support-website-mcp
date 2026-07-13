package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests for {@link SitemapCrawler} using {@link MockRestServiceServer} to stub the HTTP responses,
 * so no real network access is required.
 */
@DisplayName("Sitemap crawler")
class SitemapCrawlerTest {

    private MockRestServiceServer server;
    private SitemapCrawler crawler;

    private RestClient.Builder builder;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        crawler = new SitemapCrawler(builder, new UrlMatcher(), RobotsPolicy.ALLOW_ALL);
    }

    private static ContentSource source(List<String> sitemaps, List<String> include, List<String> exclude) {
        return source("https://ex.com", sitemaps, include, exclude);
    }

    private static ContentSource source(String baseUrl, List<String> sitemaps,
                                        List<String> include, List<String> exclude) {
        return new ContentSource(
            "test", SourceType.WEBSITE, baseUrl, sitemaps, include, exclude, "article", List.of(), true, null);
    }

    private void stubXml(String url, String body) {
        server.expect(manyTimes(), requestTo(url)).andRespond(withSuccess(body, MediaType.APPLICATION_XML));
    }

    private void stubHtml(String url, String body) {
        server.expect(manyTimes(), requestTo(url)).andRespond(withSuccess(body, MediaType.TEXT_HTML));
    }

    private static String urlset(String... entries) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"
            + String.join("", entries) + "</urlset>";
    }

    private static String urlEntry(String loc, String lastmod) {
        String mod = lastmod == null ? "" : "<lastmod>" + lastmod + "</lastmod>";
        return "<url><loc>" + loc + "</loc>" + mod + "</url>";
    }

    @Test
    @DisplayName("a flat <urlset> is collected with URLs and lastmod values")
    void flatUrlsetIsCollected() {
        stubXml("https://ex.com/sitemap.xml", urlset(
            urlEntry("https://ex.com/posts/a", "2026-03-01"),
            urlEntry("https://ex.com/posts/b", "2026-03-02"),
            urlEntry("https://ex.com/posts/c", "2026-03-03")));

        List<DiscoveredItem> items = crawler.discover(source(List.of("/sitemap.xml"), List.of("/**"), List.of()));

        assertThat(items).containsExactly(
            new DiscoveredItem("https://ex.com/posts/a", "2026-03-01"),
            new DiscoveredItem("https://ex.com/posts/b", "2026-03-02"),
            new DiscoveredItem("https://ex.com/posts/c", "2026-03-03"));
    }

    @Test
    @DisplayName("a <sitemapindex> is followed into its child sitemaps")
    void sitemapIndexIsFollowed() {
        stubXml("https://ex.com/sitemap.xml",
            "<?xml version=\"1.0\"?><sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"
                + "<sitemap><loc>https://ex.com/child-1.xml</loc></sitemap>"
                + "<sitemap><loc>https://ex.com/child-2.xml</loc></sitemap></sitemapindex>");
        stubXml("https://ex.com/child-1.xml", urlset(urlEntry("https://ex.com/posts/a", "2026-03-01")));
        stubXml("https://ex.com/child-2.xml", urlset(urlEntry("https://ex.com/posts/b", "2026-03-02")));

        List<DiscoveredItem> items = crawler.discover(source(List.of("/sitemap.xml"), List.of("/**"), List.of()));

        assertThat(items).extracting(DiscoveredItem::url)
            .containsExactlyInAnyOrder("https://ex.com/posts/a", "https://ex.com/posts/b");
    }

    @Test
    @DisplayName("an off-host child sitemap in an index is not followed")
    void offHostChildSitemapIsSkipped() {
        stubXml("https://ex.com/sitemap.xml",
            "<?xml version=\"1.0\"?><sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"
                + "<sitemap><loc>https://ex.com/child.xml</loc></sitemap>"
                + "<sitemap><loc>https://evil.internal/child.xml</loc></sitemap></sitemapindex>");
        stubXml("https://ex.com/child.xml", urlset(urlEntry("https://ex.com/posts/a", null)));

        List<DiscoveredItem> items = crawler.discover(source(List.of("/sitemap.xml"), List.of("/**"), List.of()));

        // The off-host child sitemap is never fetched (no stub for evil.internal) and contributes nothing.
        assertThat(items).extracting(DiscoveredItem::url).containsExactly("https://ex.com/posts/a");
    }

    @Test
    @DisplayName("a <url> without <lastmod> yields a null change marker")
    void missingLastmodIsTolerated() {
        stubXml("https://ex.com/sitemap.xml", urlset(urlEntry("https://ex.com/posts/a", null)));

        List<DiscoveredItem> items = crawler.discover(source(List.of("/sitemap.xml"), List.of("/**"), List.of()));

        assertThat(items).containsExactly(new DiscoveredItem("https://ex.com/posts/a", null));
    }

    @Test
    @DisplayName("only URLs matching url-include are returned")
    void onlyIncludedUrlsAreReturned() {
        stubXml("https://ex.com/sitemap.xml", urlset(
            urlEntry("https://ex.com/posts/a", null),
            urlEntry("https://ex.com/about", null)));

        List<DiscoveredItem> items = crawler.discover(source(List.of("/sitemap.xml"), List.of("/posts/**"), List.of()));

        assertThat(items).extracting(DiscoveredItem::url).containsExactly("https://ex.com/posts/a");
    }

    @Test
    @DisplayName("URLs disallowed by robots.txt are dropped from discovery")
    void robotsDisallowedUrlsAreDropped() {
        stubXml("https://ex.com/sitemap.xml", urlset(
            urlEntry("https://ex.com/posts/a", null),
            urlEntry("https://ex.com/private/secret", null)));
        RobotsPolicy denyPrivate = new RobotsPolicy() {
            @Override
            public boolean isAllowed(String url) {
                return !url.contains("/private/");
            }

            @Override
            public java.time.Duration crawlDelay(String host) {
                return java.time.Duration.ZERO;
            }
        };
        SitemapCrawler robotsAwareCrawler = new SitemapCrawler(builder, new UrlMatcher(), denyPrivate);

        List<DiscoveredItem> items = robotsAwareCrawler.discover(source(List.of("/sitemap.xml"), List.of("/**"), List.of()));

        assertThat(items).extracting(DiscoveredItem::url).containsExactly("https://ex.com/posts/a");
    }

    @Test
    @DisplayName("URLs matching url-exclude are dropped")
    void excludedUrlsAreDropped() {
        stubXml("https://ex.com/sitemap.xml", urlset(
            urlEntry("https://ex.com/posts/a", null),
            urlEntry("https://ex.com/posts/tag/ai", null)));

        List<DiscoveredItem> items = crawler.discover(
            source(List.of("/sitemap.xml"), List.of("/**"), List.of("/**/tag/**")));

        assertThat(items).extracting(DiscoveredItem::url).containsExactly("https://ex.com/posts/a");
    }

    @Test
    @DisplayName("an unreachable sitemap is skipped; other sitemaps still contribute")
    void unreachableSitemapDoesNotAbortDiscovery() {
        server.expect(manyTimes(), requestTo("https://ex.com/broken.xml")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        stubXml("https://ex.com/ok.xml", urlset(urlEntry("https://ex.com/posts/a", null)));

        List<DiscoveredItem> items = crawler.discover(
            source(List.of("/broken.xml", "/ok.xml"), List.of("/**"), List.of()));

        assertThat(items).extracting(DiscoveredItem::url).containsExactly("https://ex.com/posts/a");
    }

    @Test
    @DisplayName("a malformed sitemap contributes no URLs and does not throw")
    void malformedXmlContributesNothing() {
        stubXml("https://ex.com/sitemap.xml", "<<< this is not a valid sitemap >>>");

        List<DiscoveredItem> items = crawler.discover(source(List.of("/sitemap.xml"), List.of("/**"), List.of()));

        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("no sitemap triggers a bounded crawl that finds in-scope links and ignores other hosts")
    void noSitemapTriggersBoundedCrawl() {
        stubHtml("https://ex.com/",
            "<html><body>"
                + "<a href=\"/posts/a\">a</a>"
                + "<a href=\"/about\">about</a>"
                + "<a href=\"https://other.com/x\">external</a>"
                + "</body></html>");
        stubHtml("https://ex.com/posts/a", "<html><body>no links</body></html>");
        stubHtml("https://ex.com/about", "<html><body>no links</body></html>");

        List<DiscoveredItem> items = crawler.discover(
            source("https://ex.com/", List.of(), List.of("/posts/**"), List.of()));

        assertThat(items).extracting(DiscoveredItem::url).containsExactly("https://ex.com/posts/a");
    }

    @Test
    @DisplayName("the fallback crawl terminates on cyclic links")
    void fallbackCrawlTerminatesOnCycles() {
        stubHtml("https://ex.com/", "<html><body><a href=\"/page-b\">b</a></body></html>");
        stubHtml("https://ex.com/page-b", "<html><body><a href=\"/\">home</a></body></html>");

        List<DiscoveredItem> items = crawler.discover(
            source("https://ex.com/", List.of(), List.of("/**"), List.of()));

        // Terminates (no infinite loop) and discovers both cyclically-linked pages.
        assertThat(items).extracting(DiscoveredItem::url)
            .containsExactlyInAnyOrder("https://ex.com/page-b", "https://ex.com/");
    }
}
