package com.systa.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatConfigTest {

    @Test
    void offersOnlyTheScrapeTool_whateverPrefixItsNameCarries() {
        final ToolCallback scrape = tool("firecrawl_scrape");
        final ToolCallback prefixedScrape = tool("fc_firecrawl_scrape");
        final ToolCallbackProvider all = () -> new ToolCallback[] {
                tool("firecrawl_search"), scrape, tool("firecrawl_crawl"), prefixedScrape, tool("firecrawl_scrape_status")};

        assertThat(ChatConfig.scrapeToolOnly(all).getToolCallbacks()).containsExactly(scrape, prefixedScrape);
    }

    @Test
    void offersEveryTool_whenNoneIsNamedLikeTheScrapeTool() {
        final ToolCallback[] renamed = {tool("firecrawl_fetch_page"), tool("firecrawl_search")};

        assertThat(ChatConfig.scrapeToolOnly(() -> renamed).getToolCallbacks()).containsExactly(renamed);
    }

    @Test
    void looksTheToolsUpOnEachCall_notWhenItIsBuilt() {
        final AtomicInteger lookups = new AtomicInteger();
        final ToolCallbackProvider counting = () -> {
            lookups.incrementAndGet();
            return new ToolCallback[] {tool("firecrawl_scrape")};
        };

        final ToolCallbackProvider scrapeOnly = ChatConfig.scrapeToolOnly(counting);
        assertThat(lookups).hasValue(0);

        scrapeOnly.getToolCallbacks();
        scrapeOnly.getToolCallbacks();
        assertThat(lookups).hasValue(2);
    }

    private static ToolCallback tool(final String name) {
        final ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn(name);
        final ToolCallback tool = mock(ToolCallback.class);
        when(tool.getToolDefinition()).thenReturn(definition);
        return tool;
    }
}
