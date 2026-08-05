package com.systa.config;

import com.systa.adivsors.TokenAuditAdvisor;
import lombok.AllArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
@AllArgsConstructor
public class ChatConfig {

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
}
