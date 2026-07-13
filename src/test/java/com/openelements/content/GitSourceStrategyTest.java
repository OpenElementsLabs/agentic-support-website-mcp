package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests for {@link GitSourceStrategy} using {@link MockRestServiceServer} to stub the GitHub trees
 * and raw-content endpoints (no real network), plus direct unit tests of the pure mapping helpers.
 */
@DisplayName("Git source strategy")
class GitSourceStrategyTest {

    private static final String TREES_URL =
        "https://api.github.com/repos/OpenElements/website/git/trees/main?recursive=1";

    private MockRestServiceServer server;
    private GitSourceStrategy strategy;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        strategy = new GitSourceStrategy(builder);
    }

    private static ContentSource gitSource(String token, String... paths) {
        return new ContentSource("oe-md", SourceType.GIT, "https://open-elements.com",
            List.of(), List.of("/**"), List.of(), null, List.of(), true,
            new GitConfig("github", "OpenElements/website", "main", List.of(paths), token));
    }

    private static String rawUrl(String path) {
        return "https://raw.githubusercontent.com/OpenElements/website/main/" + path;
    }

    @Test
    @DisplayName("handles the GIT source type")
    void handlesGitType() {
        assertThat(strategy.type()).isEqualTo(SourceType.GIT);
    }

    @Test
    @DisplayName("discovery lists tree blobs filtered by the paths glob, with their SHAs")
    void discoveryFiltersByPaths() {
        server.expect(requestTo(TREES_URL)).andRespond(withSuccess("""
            {"tree":[
              {"path":"README.md","type":"blob","sha":"r1"},
              {"path":"content/posts/2026-03-12-slug.md","type":"blob","sha":"s1"},
              {"path":"content/posts/image.png","type":"blob","sha":"i1"},
              {"path":"content/posts","type":"tree","sha":"t1"}
            ]}""", MediaType.APPLICATION_JSON));

        List<DiscoveredItem> items = strategy.discover(gitSource(null, "content/posts/**/*.md"));

        assertThat(items).containsExactly(new DiscoveredItem("content/posts/2026-03-12-slug.md", "s1"));
    }

    @Test
    @DisplayName("a configured token is sent as a bearer credential")
    void tokenIsSentForPrivateRepo() {
        server.expect(requestTo(TREES_URL))
            .andExpect(header("Authorization", "Bearer secret-token"))
            .andRespond(withSuccess("{\"tree\":[]}", MediaType.APPLICATION_JSON));

        strategy.discover(gitSource("secret-token", "content/posts/**/*.md"));

        server.verify();
    }

    @Test
    @DisplayName("a discovery auth failure is surfaced so the source is isolated")
    void missingTokenFailsClearly() {
        server.expect(requestTo(TREES_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> strategy.discover(gitSource(null, "content/posts/**/*.md")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OpenElements/website");
    }

    @Test
    @DisplayName("fetch parses frontmatter, cleans shortcodes, maps the URL and derives the locale")
    void fetchExtractsFrontmatterAndBody() {
        String path = "content/posts/2026-03-12-slug.md";
        server.expect(requestTo(rawUrl(path))).andRespond(withSuccess("""
            ---
            title: "Agentic Wallets"
            date: "2026-03-12"
            author: "hendrik"
            categories: ["ai", "web3"]
            ---
            Body text about {{< figure src="x.png" >}} wallets.
            """, MediaType.TEXT_PLAIN));

        FetchOutcome outcome = strategy.fetch(gitSource(null, "content/posts/**/*.md"),
            new DiscoveredItem(path, "s1"));

        assertThat(outcome.result()).isEqualTo(FetchOutcome.Result.INDEX);
        ContentDocument document = outcome.document();
        assertThat(document.title()).isEqualTo("Agentic Wallets");
        assertThat(document.author()).isEqualTo("hendrik");
        assertThat(document.categories()).containsExactly("ai", "web3");
        assertThat(document.publishedDate()).isEqualTo("2026-03-12");
        assertThat(document.url()).isEqualTo("https://open-elements.com/posts/2026/03/12/slug");
        assertThat(document.locale()).isEqualTo("en");
        assertThat(document.lastmod()).isEqualTo("s1");
        assertThat(document.body()).contains("Body text about", "wallets.");
        assertThat(document.body()).doesNotContain("{{<", "title:");
    }

    @Test
    @DisplayName("a fetch failure skips the file without failing the batch")
    void fetchFailureIsSkipped() {
        String path = "content/posts/gone.md";
        server.expect(requestTo(rawUrl(path))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        FetchOutcome outcome = strategy.fetch(gitSource(null, "content/posts/**/*.md"),
            new DiscoveredItem(path, "s1"));

        assertThat(outcome.result()).isEqualTo(FetchOutcome.Result.SKIP);
    }

    // ---- pure mapping helpers ----

    @Test
    @DisplayName("a dated filename maps to the canonical /posts/YYYY/MM/DD/slug URL")
    void datedFilenameMapsToCanonicalUrl() {
        String url = strategy.mapToUrl(
            gitSource(null, "content/posts/**/*.md"), "content/posts/2026-03-12-my-post.md", Map.of());
        assertThat(url).isEqualTo("https://open-elements.com/posts/2026/03/12/my-post");
    }

    @Test
    @DisplayName("the locale is derived from a filename language suffix")
    void localeFromFilenameSuffix() {
        assertThat(strategy.localeFromPath("content/posts/2026-03-12-slug.de.md")).isEqualTo("de");
        assertThat(strategy.localeFromPath("content/posts/2026-03-12-slug.md")).isEqualTo("en");
    }

    @Test
    @DisplayName("a file without frontmatter yields the whole content as the body")
    void parsesBodyWithoutFrontmatter() {
        GitSourceStrategy.ParsedMarkdown parsed = strategy.parseMarkdown("Just body, no frontmatter.");
        assertThat(parsed.frontmatter()).isEmpty();
        assertThat(parsed.body()).isEqualTo("Just body, no frontmatter.");
    }
}
