package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

/**
 * Tests for {@link WebsiteSourceStrategy} using its real collaborators ({@link SitemapCrawler},
 * {@link PageFetcher}, {@link ContentExtractor}) wired to {@link MockRestServiceServer}, so the
 * fetch-outcome mapping is exercised end-to-end without mocks or real network.
 */
@DisplayName("Website source strategy")
class WebsiteSourceStrategyTest {

    private static final String PAGE_URL = "https://ex.com/posts/a";

    private MockRestServiceServer fetchServer;
    private MockRestServiceServer crawlServer;
    private WebsiteSourceStrategy strategy;

    @BeforeEach
    void setUp() {
        ContentSourceProperties properties = new ContentSourceProperties(
            true, null, "OpenElementsContentBot/1.0", 2.0, Duration.ofSeconds(10), DataSize.ofMegabytes(5), List.of());

        RestClient.Builder fetchBuilder = RestClient.builder();
        fetchServer = MockRestServiceServer.bindTo(fetchBuilder).ignoreExpectOrder(true).build();
        PageFetcher fetcher = new PageFetcher(fetchBuilder.build(), properties,
            new HostRateLimiter(0, () -> 0L, millis -> { }), RobotsPolicy.ALLOW_ALL, millis -> { });

        RestClient.Builder crawlBuilder = RestClient.builder();
        crawlServer = MockRestServiceServer.bindTo(crawlBuilder).ignoreExpectOrder(true).build();
        SitemapCrawler crawler = new SitemapCrawler(crawlBuilder, new UrlMatcher(), RobotsPolicy.ALLOW_ALL);

        ContentExtractor extractor = new ContentExtractor(new ContentLocaleResolver(), new ObjectMapper());
        strategy = new WebsiteSourceStrategy(crawler, fetcher, extractor);
    }

    private static ContentSource source(List<String> sitemaps) {
        return new ContentSource(
            "open-elements", SourceType.WEBSITE, "https://ex.com",
            sitemaps, List.of("/**"), List.of(), "article", List.of(), true, null);
    }

    private static DiscoveredItem item(String lastmod) {
        return new DiscoveredItem(PAGE_URL, lastmod);
    }

    @Test
    @DisplayName("the strategy handles the WEBSITE source type")
    void handlesWebsiteType() {
        assertThat(strategy.type()).isEqualTo(SourceType.WEBSITE);
    }

    @Test
    @DisplayName("discover delegates to the sitemap crawler")
    void discoverDelegatesToCrawler() {
        crawlServer.expect(requestTo("https://ex.com/sitemap.xml")).andRespond(withSuccess(
            "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"
                + "<url><loc>" + PAGE_URL + "</loc></url></urlset>",
            MediaType.APPLICATION_XML));

        List<DiscoveredItem> items = strategy.discover(source(List.of("/sitemap.xml")));

        assertThat(items).extracting(DiscoveredItem::url).containsExactly(PAGE_URL);
    }

    @Test
    @DisplayName("a successful fetch with content produces an INDEX outcome with a populated document")
    void successfulFetchProducesIndex() {
        fetchServer.expect(requestTo(PAGE_URL)).andRespond(withSuccess(
            "<html><head><meta property=\"og:title\" content=\"The Title\"></head>"
                + "<body><article><p>Article body text.</p></article></body></html>",
            MediaType.TEXT_HTML));

        FetchOutcome outcome = strategy.fetch(source(List.of()), item("2026-03-01"));

        assertThat(outcome.result()).isEqualTo(FetchOutcome.Result.INDEX);
        ContentDocument document = outcome.document();
        assertThat(document).isNotNull();
        assertThat(document.id()).isEqualTo(ContentDocument.id("open-elements", PAGE_URL));
        assertThat(document.source()).isEqualTo("open-elements");
        assertThat(document.url()).isEqualTo(PAGE_URL);
        assertThat(document.title()).isEqualTo("The Title");
        assertThat(document.body()).contains("Article body text.");
        assertThat(document.locale()).isEqualTo("en");
        assertThat(document.lastmod()).isEqualTo("2026-03-01");
    }

    @Test
    @DisplayName("a 304 produces an UNCHANGED outcome")
    void notModifiedProducesUnchanged() {
        fetchServer.expect(requestTo(PAGE_URL)).andRespond(withStatus(HttpStatus.NOT_MODIFIED));

        FetchOutcome outcome = strategy.fetch(source(List.of()), item(null));

        assertThat(outcome.result()).isEqualTo(FetchOutcome.Result.UNCHANGED);
        assertThat(outcome.document()).isNull();
    }

    @Test
    @DisplayName("a 404 produces a DELETE outcome")
    void notFoundProducesDelete() {
        fetchServer.expect(requestTo(PAGE_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        FetchOutcome outcome = strategy.fetch(source(List.of()), item(null));

        assertThat(outcome.result()).isEqualTo(FetchOutcome.Result.DELETE);
    }

    @Test
    @DisplayName("a persistent fetch error produces a SKIP outcome")
    void fetchErrorProducesSkip() {
        fetchServer.expect(manyTimes(), requestTo(PAGE_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        FetchOutcome outcome = strategy.fetch(source(List.of()), item(null));

        assertThat(outcome.result()).isEqualTo(FetchOutcome.Result.SKIP);
    }

    @Test
    @DisplayName("a page with no extractable content produces a SKIP outcome")
    void emptyContentProducesSkip() {
        fetchServer.expect(requestTo(PAGE_URL)).andRespond(withSuccess(
            "<html><body><article></article></body></html>", MediaType.TEXT_HTML));

        FetchOutcome outcome = strategy.fetch(source(List.of()), item(null));

        assertThat(outcome.result()).isEqualTo(FetchOutcome.Result.SKIP);
    }
}
