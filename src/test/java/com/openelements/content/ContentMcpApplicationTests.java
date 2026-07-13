package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.openelements.spring.base.mcp.McpProperties;
import com.openelements.spring.base.mcp.McpToolProvider;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/**
 * Verifies that the application context forms with the imported library building blocks and that the
 * skeleton exposes the expected beans.
 *
 * <p>Meilisearch is left enabled but unreachable (the default {@code localhost:7700}); the library's
 * bootstrap runner tolerates this, so the context still starts — see {@link MeilisearchUnreachableTest}.
 *
 * <p>Covers behaviors: "App boots with the imported library configs", "Scheduling is enabled",
 * "Content package is component-scanned", and "/mcp endpoint is exposed".
 *
 * <p>The {@code /mcp} exposure is asserted at the bean level: the library secures every route with
 * {@code anyRequest().authenticated()}, so an unauthenticated HTTP request returns 401 regardless of
 * whether the route exists — that cannot distinguish an exposed endpoint from a missing one. The
 * presence of the {@link WebMvcStreamableServerTransportProvider} (which registers the {@code /mcp}
 * route) and the {@link McpSyncServer} is the deterministic signal, mirrored by
 * {@link McpDisabledTest} for the disabled case.
 */
@SpringBootTest(properties = "openelements.mcp.auth.api-key.enabled=false")
@DisplayName("Content MCP application skeleton")
class ContentMcpApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("the Spring context initializes with the imported library configs")
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    @DisplayName("ContentConfig is component-scanned and registered as a bean")
    void contentConfigIsComponentScanned() {
        assertThat(context.getBean(ContentConfig.class)).isNotNull();
    }

    @Test
    @DisplayName("scheduling is enabled so later @Scheduled beans will run")
    void schedulingIsEnabled() {
        // @EnableScheduling registers this post-processor; its presence proves scheduling is active
        // so later @Scheduled beans (spec 010) will run.
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isNotEmpty();
    }

    @Test
    @DisplayName("the /mcp server is exposed and advertises the configured server name/version")
    void mcpEndpointIsExposed() {
        McpSyncServer mcpServer = context.getBean(McpSyncServer.class);
        assertThat(mcpServer).isNotNull();

        // The streamable-HTTP transport provider is what registers the /mcp route.
        assertThat(context.getBeansOfType(WebMvcStreamableServerTransportProvider.class)).isNotEmpty();

        McpProperties mcpProperties = context.getBean(McpProperties.class);
        assertThat(mcpProperties.serverName()).isEqualTo("Open Elements Content MCP");
        assertThat(mcpProperties.serverVersion()).isEqualTo("0.1.0");

        // Drift (spec 012): the original skeleton asserted an empty McpToolProvider catalog; the
        // content tools are now registered, so a ContentMcpToolProvider is present.
        assertThat(context.getBeansOfType(McpToolProvider.class))
            .containsKey("contentMcpToolProvider");
    }
}
