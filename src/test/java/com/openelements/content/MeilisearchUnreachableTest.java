package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Verifies that the application still starts and serves HTTP when Meilisearch is enabled but not
 * reachable at startup. The library's bootstrap runner logs a warning and skips indexing rather than
 * failing the context, so the HTTP listener (and {@code /mcp}) come up regardless.
 *
 * <p>{@code localhost:1} is a port nothing listens on, giving an immediate connection refusal.
 *
 * <p>Covers behavior: "Meilisearch unreachable at startup".
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "openelements.meilisearch.enabled=true",
        "openelements.meilisearch.host=http://localhost:1",
        "openelements.meilisearch.master-key=unused",
        "openelements.mcp.auth.api-key.enabled=false"
    })
class MeilisearchUnreachableTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Test
    void contextStartsDespiteUnreachableMeilisearch() {
        assertThat(context).isNotNull();
        assertThat(port).isGreaterThan(0);
    }

    @Test
    void httpListenerIsUp() {
        // A permit-all route provided by the library confirms the servlet container is serving.
        // It has no handler here, so a 404 (not a connection failure) proves the listener is up.
        ResponseEntity<String> response = restTemplate.getForEntity("/api/health/liveness", String.class);
        assertThat(response.getStatusCode()).isNotNull();
        assertThat(response.getStatusCode().value()).isBetween(HttpStatus.OK.value(), 499);
    }
}
