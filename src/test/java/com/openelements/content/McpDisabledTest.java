package com.openelements.content;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Verifies that disabling the MCP server removes its beans, so the {@code /mcp} endpoint is not
 * exposed. The MCP server beans are {@code @ConditionalOnProperty(openelements.mcp.enabled=true)};
 * with the property set to {@code false} neither the {@link McpSyncServer} nor the
 * {@link WebMvcStreamableServerTransportProvider} that registers the route is created.
 *
 * <p>Covers behavior: "MCP disabled".
 */
@SpringBootTest(properties = "openelements.mcp.enabled=false")
@DisplayName("MCP disabled")
class McpDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("no MCP server or transport beans are created, so /mcp is not exposed")
    void noMcpServerBeansAreCreated() {
        assertThat(context.getBeansOfType(McpSyncServer.class)).isEmpty();
        assertThat(context.getBeansOfType(WebMvcStreamableServerTransportProvider.class)).isEmpty();
    }
}
