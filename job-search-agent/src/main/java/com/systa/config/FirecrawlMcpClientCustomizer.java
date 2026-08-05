package com.systa.config;

import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;

/**
 * Firecrawl's remote MCP server (https://mcp.firecrawl.dev) has no headers property
 * exposed via Spring AI's declarative {@code spring.ai.mcp.client.streamable-http}
 * config, so the Authorization bearer token is attached here instead, applied only to
 * the "firecrawl" named connection.
 */
@Component
public class FirecrawlMcpClientCustomizer implements McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> {

    private static final String FIRECRAWL_CONNECTION_NAME = "firecrawl";

    @Value("${firecrawl.api-key}")
    private String firecrawlApiKey;

    @Override
    public void customize(final String connectionName, final HttpClientStreamableHttpTransport.Builder transportBuilder) {
        if (FIRECRAWL_CONNECTION_NAME.equals(connectionName)) {
            transportBuilder.requestBuilder(HttpRequest.newBuilder()
                    .header("Authorization", "Bearer " + firecrawlApiKey));
        }
    }
}
