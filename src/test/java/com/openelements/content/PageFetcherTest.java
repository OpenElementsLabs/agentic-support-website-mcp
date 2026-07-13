package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

/**
 * Tests for {@link PageFetcher} using {@link MockRestServiceServer} to stub responses (no real
 * network) and a no-op rate limiter and backoff sleeper (no real waiting).
 */
@DisplayName("Page fetcher")
class PageFetcherTest {

    private static final String USER_AGENT = "OpenElementsContentBot/1.0 (+https://open-elements.com)";
    private static final String URL = "https://ex.com/posts/a";

    private RestClient client;
    private MockRestServiceServer server;
    private List<Long> backoffs;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
        backoffs = new ArrayList<>();
    }

    private PageFetcher fetcher(long maxBytes) {
        ContentSourceProperties properties = new ContentSourceProperties(
            true, null, USER_AGENT, 2.0, Duration.ofSeconds(10), DataSize.ofBytes(maxBytes), List.of());
        HostRateLimiter noThrottle = new HostRateLimiter(0, () -> 0L, millis -> { });
        return new PageFetcher(client, properties, noThrottle, RobotsPolicy.ALLOW_ALL, backoffs::add);
    }

    @Test
    @DisplayName("a 200 response returns OK with the body and captured validators")
    void success200ReturnsBodyAndValidators() {
        server.expect(requestTo(URL)).andRespond(withSuccess("<html>hi</html>", MediaType.TEXT_HTML)
            .header(HttpHeaders.ETAG, "\"abc\"")
            .header(HttpHeaders.LAST_MODIFIED, "Wed, 01 Jan 2026 00:00:00 GMT"));

        FetchResult result = fetcher(1_000_000).fetch(URL, null, null);

        assertThat(result.status()).isEqualTo(FetchResult.Status.OK);
        assertThat(result.html()).isEqualTo("<html>hi</html>");
        assertThat(result.etag()).isEqualTo("\"abc\"");
        assertThat(result.lastModified()).isEqualTo("Wed, 01 Jan 2026 00:00:00 GMT");
    }

    @Test
    @DisplayName("every request carries the configured bot User-Agent")
    void botUserAgentIsSent() {
        server.expect(requestTo(URL))
            .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
            .andRespond(withSuccess("<html>hi</html>", MediaType.TEXT_HTML));

        FetchResult result = fetcher(1_000_000).fetch(URL, null, null);

        assertThat(result.status()).isEqualTo(FetchResult.Status.OK);
        server.verify();
    }

    @Test
    @DisplayName("a prior validator triggers a conditional request and 304 yields NOT_MODIFIED")
    void conditionalRequestYieldsNotModified() {
        server.expect(requestTo(URL))
            .andExpect(header(HttpHeaders.IF_NONE_MATCH, "\"abc\""))
            .andExpect(header(HttpHeaders.IF_MODIFIED_SINCE, "Wed, 01 Jan 2026 00:00:00 GMT"))
            .andRespond(withStatus(HttpStatus.NOT_MODIFIED));

        FetchResult result = fetcher(1_000_000).fetch(URL, "\"abc\"", "Wed, 01 Jan 2026 00:00:00 GMT");

        assertThat(result.status()).isEqualTo(FetchResult.Status.NOT_MODIFIED);
        assertThat(result.html()).isNull();
    }

    @Test
    @DisplayName("an oversized body is aborted rather than loaded into memory")
    void oversizedBodyIsAborted() {
        server.expect(requestTo(URL))
            .andRespond(withSuccess("0123456789ABCDEF", MediaType.TEXT_HTML)); // 16 bytes

        FetchResult result = fetcher(10).fetch(URL, null, null); // cap 10 bytes

        assertThat(result.status()).isEqualTo(FetchResult.Status.ERROR);
        assertThat(result.html()).isNull();
    }

    @Test
    @DisplayName("a 404 is returned as NOT_FOUND without retrying")
    void notFoundIsNotRetried() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        FetchResult result = fetcher(1_000_000).fetch(URL, null, null);

        assertThat(result.status()).isEqualTo(FetchResult.Status.NOT_FOUND);
        assertThat(backoffs).isEmpty();
        server.verify(); // exactly one request was made
    }

    @Test
    @DisplayName("a persistent 5xx is retried with backoff and finally returns ERROR")
    void persistent5xxIsRetriedThenFails() {
        server.expect(manyTimes(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        FetchResult result = fetcher(1_000_000).fetch(URL, null, null);

        assertThat(result.status()).isEqualTo(FetchResult.Status.ERROR);
        assertThat(backoffs).hasSize(2); // 3 attempts -> 2 backoffs between them
    }

    @Test
    @DisplayName("a transient network error is retried")
    void transientNetworkErrorIsRetried() {
        server.expect(manyTimes(), requestTo(URL)).andRespond(withException(new IOException("timeout")));

        FetchResult result = fetcher(1_000_000).fetch(URL, null, null);

        assertThat(result.status()).isEqualTo(FetchResult.Status.ERROR);
        assertThat(backoffs).hasSize(2);
    }

    @Test
    @DisplayName("a 503 followed by a 200 recovers to OK")
    void recoversAfterTransientFailure() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withSuccess("<html>ok</html>", MediaType.TEXT_HTML));

        FetchResult result = fetcher(1_000_000).fetch(URL, null, null);

        assertThat(result.status()).isEqualTo(FetchResult.Status.OK);
        assertThat(result.html()).isEqualTo("<html>ok</html>");
        assertThat(backoffs).hasSize(1);
    }
}
