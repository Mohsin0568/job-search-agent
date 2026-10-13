package com.systa.config;

import com.systa.adivsors.TokenAuditAdvisor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Arrays;
import java.util.List;

@Configuration
@AllArgsConstructor
@Slf4j
public class ChatConfig {

    /** Qualifier of the chat client that can only scrape a page - see {@link #jobPostingChatClient}. */
    public static final String JOB_POSTING_CHAT_CLIENT = "jobPostingChatClient";

    /** Qualifier of the chat client with no tools at all - see {@link #resumeTailoringChatClient}. */
    public static final String RESUME_TAILORING_CHAT_CLIENT = "resumeTailoringChatClient";

    private static final String SCRAPE_TOOL_NAME = "firecrawl_scrape";

    private final TokenAuditAdvisor tokenAuditAdvisor;

    @Bean
    @Primary
    public ChatClient getChatClient(final ChatClient.Builder chatClientBuilder,
                                    final ToolCallbackProvider toolCallbackProvider){

        return chatClientBuilder
                .defaultTools(toolCallbackProvider)
                .defaultAdvisors(List.of(new SimpleLoggerAdvisor(), tokenAuditAdvisor))
                .build();
    }

    // Reading one job posting needs one page fetched, and that page is untrusted text: with only the
    // scrape tool on offer, nothing written on it can talk the model into searching or crawling.
    @Bean(JOB_POSTING_CHAT_CLIENT)
    public ChatClient jobPostingChatClient(final ChatClient.Builder chatClientBuilder,
                                           final ToolCallbackProvider toolCallbackProvider) {
        return chatClientBuilder
                .defaultTools(scrapeToolOnly(toolCallbackProvider))
                .defaultAdvisors(List.of(new SimpleLoggerAdvisor(), tokenAuditAdvisor))
                .build();
    }

    // Tailoring a resume is rewriting text it is handed - a resume and a job description, both written
    // by other people - so it gets no tools: nothing in either can make the model fetch or search.
    @Bean(RESUME_TAILORING_CHAT_CLIENT)
    public ChatClient resumeTailoringChatClient(final ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
                .defaultAdvisors(List.of(new SimpleLoggerAdvisor(), tokenAuditAdvisor))
                .build();
    }

    // Resolved on each call, like the full provider: the MCP connection isn't up when beans are built.
    static ToolCallbackProvider scrapeToolOnly(final ToolCallbackProvider toolCallbackProvider) {
        return () -> {
            final ToolCallback[] all = toolCallbackProvider.getToolCallbacks();
            // endsWith, because Spring AI may prefix an MCP tool's name with its connection's.
            final ToolCallback[] scrapeOnly = Arrays.stream(all)
                    .filter(tool -> tool.getToolDefinition().name().endsWith(SCRAPE_TOOL_NAME))
                    .toArray(ToolCallback[]::new);
            if (scrapeOnly.length == 0) {
                // Better to read the posting with every tool than not at all if Firecrawl renames it.
                log.warn("No tool named *{} among the {} available - offering them all", SCRAPE_TOOL_NAME, all.length);
                return all;
            }
            return scrapeOnly;
        };
    }
}
