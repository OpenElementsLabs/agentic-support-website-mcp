package com.openelements.content;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Central configuration for the content-specific part of the application.
 *
 * <p>Enables {@link ContentSourceProperties} binding and is component-scanned together with the rest
 * of the {@code com.openelements.content} package (e.g. {@link UrlMatcher}). Later specs extend it
 * with further {@code @Bean} definitions (crawler, indexer, search service, MCP tools).
 */
@Configuration
@EnableConfigurationProperties(ContentSourceProperties.class)
public class ContentConfig {
}
