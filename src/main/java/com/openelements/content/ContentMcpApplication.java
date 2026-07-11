package com.openelements.content;

import com.openelements.spring.base.mcp.McpConfiguration;
import com.openelements.spring.base.security.SecurityConfig;
import com.openelements.spring.base.services.apikey.ApiKeyConfig;
import com.openelements.spring.base.services.search.SearchConfig;
import com.openelements.spring.base.services.user.UserConfig;
import com.openelements.spring.base.tenant.TenantConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the Open Elements Content MCP.
 *
 * <p>The application is standalone and built on {@code spring-services} as a library. It imports the
 * building blocks it needs rather than the full platform configuration:
 *
 * <ul>
 *   <li>{@link McpConfiguration} — MCP server and the {@code /mcp} streamable-HTTP endpoint.
 *   <li>{@link SearchConfig} — the Meilisearch stack.
 *   <li>{@link SecurityConfig}, {@link TenantConfig}, {@link ApiKeyConfig}, {@link UserConfig} — the
 *       api-key/user stack that the MCP server transport structurally depends on
 *       ({@code McpServerConfig}/{@code McpSecurityConfig} require an {@code ApiKeyDataService},
 *       which in turn needs the JPA-backed api-key and user repositories).
 * </ul>
 *
 * <p>Because {@code spring-services} ships its JPA entities and repositories under
 * {@code com.openelements.spring.base}, entity and repository scanning is pointed there in addition
 * to this application's own {@code com.openelements.content} package.
 */
@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = {"com.openelements.spring.base", "com.openelements.content"})
@EnableJpaRepositories(basePackages = {"com.openelements.spring.base", "com.openelements.content"})
@Import({
    SecurityConfig.class,
    TenantConfig.class,
    ApiKeyConfig.class,
    UserConfig.class,
    SearchConfig.class,
    McpConfiguration.class
})
public class ContentMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentMcpApplication.class, args);
    }
}
