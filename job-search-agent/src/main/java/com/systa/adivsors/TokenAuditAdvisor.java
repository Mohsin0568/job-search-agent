package com.systa.adivsors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;


@Component
public class TokenAuditAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(TokenAuditAdvisor.class);

    @Override
    public ChatClientResponse adviseCall(final ChatClientRequest chatClientRequest, final CallAdvisorChain callAdvisorChain) {
        final ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        final Usage usage = response.chatResponse().getMetadata().getUsage();
        logger.info("Token used: {}", usage);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(final ChatClientRequest chatClientRequest, final StreamAdvisorChain streamAdvisorChain) {
        return null;
    }

    @Override
    public String getName() {
        return "Token Audit Advisor";
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
