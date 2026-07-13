package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests for {@link HttpRobotsPolicy} using {@link MockRestServiceServer} to stub {@code robots.txt}
 * (no real network).
 */
@DisplayName("HTTP robots policy")
class HttpRobotsPolicyTest {

    private static final String ROBOTS_URL = "https://ex.com/robots.txt";

    private MockRestServiceServer server;
    private HttpRobotsPolicy policy;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        ContentSourceProperties properties = new ContentSourceProperties(
            true, null, "OpenElementsContentBot/1.0", 2.0, Duration.ofSeconds(10), null, List.of());
        policy = new HttpRobotsPolicy(builder, properties);
    }

    private void stubRobots(String body) {
        server.expect(requestTo(ROBOTS_URL)).andRespond(withSuccess(body, MediaType.TEXT_PLAIN));
    }

    @Test
    @DisplayName("a disallowed path is rejected and other paths allowed")
    void disallowedPathIsRejected() {
        stubRobots("User-agent: *\nDisallow: /private/\n");

        assertThat(policy.isAllowed("https://ex.com/private/page")).isFalse();
        assertThat(policy.isAllowed("https://ex.com/posts/a")).isTrue();
    }

    @Test
    @DisplayName("an allow rule overrides a broader disallow")
    void allowOverridesDisallow() {
        stubRobots("User-agent: *\nDisallow: /private/\nAllow: /private/public\n");

        assertThat(policy.isAllowed("https://ex.com/private/public/page")).isTrue();
        assertThat(policy.isAllowed("https://ex.com/private/secret")).isFalse();
    }

    @Test
    @DisplayName("a group naming our user agent takes precedence over the wildcard group")
    void agentSpecificGroupWins() {
        stubRobots("User-agent: *\nDisallow: /\n\nUser-agent: OpenElementsContentBot\nDisallow: /private/\n");

        assertThat(policy.isAllowed("https://ex.com/posts/a")).isTrue(); // our group only blocks /private/
        assertThat(policy.isAllowed("https://ex.com/private/x")).isFalse();
    }

    @Test
    @DisplayName("robots.txt is fetched once per host and cached")
    void robotsIsCachedPerHost() {
        server.expect(once(), requestTo(ROBOTS_URL))
            .andRespond(withSuccess("User-agent: *\nDisallow: /private/\n", MediaType.TEXT_PLAIN));

        policy.isAllowed("https://ex.com/a");
        policy.isAllowed("https://ex.com/b");
        policy.isAllowed("https://ex.com/private/c");

        server.verify(); // exactly one robots.txt request
    }

    @Test
    @DisplayName("a missing robots.txt allows everything")
    void missingRobotsAllowsAll() {
        server.expect(requestTo(ROBOTS_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(policy.isAllowed("https://ex.com/anything")).isTrue();
    }

    @Test
    @DisplayName("Crawl-delay is parsed for the applicable group")
    void crawlDelayIsParsed() {
        stubRobots("User-agent: *\nCrawl-delay: 5\n");

        assertThat(policy.crawlDelay("ex.com")).isEqualTo(Duration.ofSeconds(5));
    }
}
