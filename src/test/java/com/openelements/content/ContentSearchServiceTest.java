package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openelements.spring.base.services.search.Highlighter;
import com.openelements.spring.base.services.search.MeilisearchClient;
import com.openelements.spring.base.services.search.MeilisearchProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ContentSearchService}: the pure filter-building and hit/facet parsing helpers,
 * and the request bodies + parsing of the public methods via a capturing {@link MeilisearchClient}
 * subclass (no mocks, no live Meilisearch).
 */
@DisplayName("Content search service")
class ContentSearchServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static MeilisearchProperties properties() {
        return new MeilisearchProperties("http://localhost:7700", "", "content_", Duration.ofSeconds(10));
    }

    private static JsonNode emptyResponse() {
        return parse("{\"results\":[{\"hits\":[],\"estimatedTotalHits\":0}]}");
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstQuery(Map<String, Object> body) {
        return ((List<Map<String, Object>>) body.get("queries")).get(0);
    }

    @Nested
    @DisplayName("filter building")
    class FilterBuilding {

        @Test
        @DisplayName("no filters produce no filter expression")
        void noneProducesNull() {
            assertThat(ContentSearchService.buildFilter(ContentFilters.none())).isNull();
        }

        @Test
        @DisplayName("locale filter")
        void locale() {
            assertThat(ContentSearchService.buildFilter(new ContentFilters(null, "de", null, null)))
                .isEqualTo("locale = \"de\"");
        }

        @Test
        @DisplayName("source filter")
        void source() {
            assertThat(ContentSearchService.buildFilter(new ContentFilters("hiero", null, null, null)))
                .isEqualTo("source = \"hiero\"");
        }

        @Test
        @DisplayName("category filter matches the array attribute")
        void category() {
            assertThat(ContentSearchService.buildFilter(new ContentFilters(null, null, "ai", null)))
                .isEqualTo("categories = \"ai\"");
        }

        @Test
        @DisplayName("since filter is a date lower bound")
        void since() {
            assertThat(ContentSearchService.buildFilter(new ContentFilters(null, null, null, "2026-01-01")))
                .isEqualTo("publishedDate >= \"2026-01-01\"");
        }

        @Test
        @DisplayName("multiple filters are AND-combined")
        void combined() {
            assertThat(ContentSearchService.buildFilter(new ContentFilters("open-elements", "en", "ai", null)))
                .isEqualTo("source = \"open-elements\" AND locale = \"en\" AND categories = \"ai\"");
        }
    }

    @Nested
    @DisplayName("request building")
    class RequestBuilding {

        @Test
        @DisplayName("search sets query, filter, date sort, highlight tags, paging and score")
        void searchBuildsRequest() {
            CapturingClient client = new CapturingClient(emptyResponse());
            ContentSearchService service = new ContentSearchService(client, properties());

            service.search("agentic wallets", new ContentFilters("open-elements", "en", null, null), 2, 10);

            Map<String, Object> query = firstQuery(client.capturedBody);
            assertThat(query).containsEntry("indexUid", "content_content");
            assertThat(query).containsEntry("q", "agentic wallets");
            assertThat(query).containsEntry("filter", "source = \"open-elements\" AND locale = \"en\"");
            assertThat(query).containsEntry("sort", List.of("publishedDate:desc"));
            assertThat(query).containsEntry("highlightPreTag", Highlighter.PRE_MARK);
            assertThat(query).containsEntry("highlightPostTag", Highlighter.POST_MARK);
            assertThat(query).containsEntry("showRankingScore", true);
            assertThat(query).containsEntry("limit", 10);
            assertThat(query).containsEntry("offset", 20);
        }

        @Test
        @DisplayName("listPosts uses an empty query sorted newest-first")
        void listPostsBuildsRequest() {
            CapturingClient client = new CapturingClient(emptyResponse());
            ContentSearchService service = new ContentSearchService(client, properties());

            service.listPosts(new ContentFilters("hiero", null, null, null), 0, 20);

            Map<String, Object> query = firstQuery(client.capturedBody);
            assertThat(query).containsEntry("q", "");
            assertThat(query).containsEntry("filter", "source = \"hiero\"");
            assertThat(query).containsEntry("sort", List.of("publishedDate:desc"));
            assertThat(query).containsEntry("limit", 20);
            assertThat(query).containsEntry("offset", 0);
        }

        @Test
        @DisplayName("getByUrlOrId filters by id when given one")
        void getByIdBuildsFilter() {
            CapturingClient client = new CapturingClient(emptyResponse());
            ContentSearchService service = new ContentSearchService(client, properties());

            service.getByUrlOrId(null, "oe_abc");

            assertThat(firstQuery(client.capturedBody)).containsEntry("filter", "id = \"oe_abc\"");
        }

        @Test
        @DisplayName("getByUrlOrId filters by url when no id is given")
        void getByUrlBuildsFilter() {
            CapturingClient client = new CapturingClient(emptyResponse());
            ContentSearchService service = new ContentSearchService(client, properties());

            service.getByUrlOrId("https://ex.com/a", null);

            assertThat(firstQuery(client.capturedBody)).containsEntry("filter", "url = \"https://ex.com/a\"");
        }

        @Test
        @DisplayName("categoryFacets requests the categories facet with the source filter")
        void facetsBuildRequest() {
            CapturingClient client = new CapturingClient(emptyResponse());
            ContentSearchService service = new ContentSearchService(client, properties());

            service.categoryFacets("open-elements", null);

            Map<String, Object> query = firstQuery(client.capturedBody);
            assertThat(query).containsEntry("facets", List.of("categories"));
            assertThat(query).containsEntry("filter", "source = \"open-elements\"");
            assertThat(query).containsEntry("limit", 0);
        }
    }

    @Nested
    @DisplayName("response parsing")
    class ResponseParsing {

        @Test
        @DisplayName("hits are parsed with title, url, date, snippet and score")
        void parsesHits() {
            JsonNode response = parse("""
                {"results":[{"estimatedTotalHits":2,"hits":[
                  {"title":"Agentic wallets","url":"https://ex.com/a","publishedDate":"2026-03-12",
                   "_rankingScore":0.9,"_formatted":{"body":"about wallets"}}
                ]}]}""");

            SearchHits hits = ContentSearchService.parseHits(response);

            assertThat(hits.estimatedTotal()).isEqualTo(2);
            assertThat(hits.hits()).hasSize(1);
            SearchHit hit = hits.hits().get(0);
            assertThat(hit.title()).isEqualTo("Agentic wallets");
            assertThat(hit.url()).isEqualTo("https://ex.com/a");
            assertThat(hit.publishedDate()).isEqualTo("2026-03-12");
            assertThat(hit.score()).isEqualTo(0.9);
            assertThat(hit.snippet()).isEqualTo("about wallets");
        }

        @Test
        @DisplayName("boundary markers become safe <em> tags and raw HTML is escaped")
        void highlightMarkersBecomeSafeEmTags() {
            ObjectNode response = MAPPER.createObjectNode();
            ObjectNode result = response.putArray("results").addObject();
            result.put("estimatedTotalHits", 1);
            ObjectNode hit = result.putArray("hits").addObject();
            hit.put("title", "T");
            hit.put("url", "u");
            hit.put("publishedDate", "2026-03-12");
            hit.putObject("_formatted")
                .put("body", Highlighter.PRE_MARK + "wallets" + Highlighter.POST_MARK + " <script>x</script>");

            SearchHit parsed = ContentSearchService.parseHits(response).hits().get(0);

            assertThat(parsed.snippet()).contains("<em>wallets</em>");
            assertThat(parsed.snippet()).doesNotContain("<script>");
        }

        @Test
        @DisplayName("an empty result set yields no hits and a zero total")
        void parsesEmpty() {
            SearchHits hits = ContentSearchService.parseHits(emptyResponse());
            assertThat(hits.hits()).isEmpty();
            assertThat(hits.estimatedTotal()).isZero();
        }

        @Test
        @DisplayName("facet distribution is parsed into category counts")
        void parsesFacets() {
            JsonNode response = parse(
                "{\"results\":[{\"facetDistribution\":{\"categories\":{\"ai\":3,\"web3\":2}}}]}");

            List<CategoryCount> counts = ContentSearchService.parseFacets(response);

            assertThat(counts).containsExactlyInAnyOrder(
                new CategoryCount("ai", 3), new CategoryCount("web3", 2));
        }
    }

    @Nested
    @DisplayName("get by url or id")
    class GetByUrlOrId {

        @Test
        @DisplayName("returns the full document when found")
        void returnsFullDocument() {
            JsonNode response = parse("""
                {"results":[{"hits":[
                  {"id":"oe_abc","source":"open-elements","locale":"en","url":"https://ex.com/a",
                   "title":"T","excerpt":"E","body":"full body","author":"h",
                   "categories":["ai","web3"],"publishedDate":"2026-03-12","lastmod":"2026-03-12","previewImage":null}
                ]}]}""");
            ContentSearchService service = new ContentSearchService(new CapturingClient(response), properties());

            Optional<ContentDocument> document = service.getByUrlOrId("https://ex.com/a", null);

            assertThat(document).isPresent();
            assertThat(document.get().id()).isEqualTo("oe_abc");
            assertThat(document.get().body()).isEqualTo("full body");
            assertThat(document.get().categories()).containsExactly("ai", "web3");
        }

        @Test
        @DisplayName("returns empty when nothing matches")
        void emptyWhenNotFound() {
            ContentSearchService service = new ContentSearchService(new CapturingClient(emptyResponse()), properties());

            assertThat(service.getByUrlOrId("https://ex.com/missing", null)).isEmpty();
        }

        @Test
        @DisplayName("returns empty without querying when both url and id are blank")
        void emptyWhenNoArguments() {
            CapturingClient client = new CapturingClient(emptyResponse());
            ContentSearchService service = new ContentSearchService(client, properties());

            assertThat(service.getByUrlOrId(null, null)).isEmpty();
            assertThat(client.capturedBody).isNull();
        }
    }

    /** Test double capturing the multi-search body and returning a canned response. */
    private static final class CapturingClient extends MeilisearchClient {
        private Map<String, Object> capturedBody;
        private final JsonNode response;

        CapturingClient(JsonNode response) {
            super(new MeilisearchProperties("http://localhost:7700", "", "content_", Duration.ofSeconds(10)),
                new ObjectMapper());
            this.response = response;
        }

        @Override
        public JsonNode multiSearch(Map<String, Object> body) {
            this.capturedBody = body;
            return response;
        }
    }
}
