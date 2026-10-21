package com.systa.advisors;

import com.systa.model.LlmUsage;
import com.systa.model.LlmUsage.Operation;
import com.systa.model.LlmUsage.Status;
import com.systa.service.LlmUsageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Records the tokens each request to the model used, and for whom, in the llm_usage collection.
 * <p>
 * Its order puts it inside the ChatClient's tool-calling loop (that advisor sits at
 * HIGHEST_PRECEDENCE + 300), so it runs once per round-trip to the model, seeing that round-trip's usage.
 * Keep the order above that, or a call that runs tools would be recorded as its last round-trip only.
 */
@Component
@Slf4j
public class LlmUsageAdvisor implements CallAdvisor, StreamAdvisor {

    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    private final LlmUsageService llmUsageService;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    @Autowired
    public LlmUsageAdvisor(final LlmUsageService llmUsageService) {
        this(llmUsageService, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    LlmUsageAdvisor(final LlmUsageService llmUsageService, final Clock clock, final Supplier<String> idGenerator) {
        this.llmUsageService = llmUsageService;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    public ChatClientResponse adviseCall(final ChatClientRequest request, final CallAdvisorChain chain) {
        final Map<String, Object> context = request.context();
        final Instant start = clock.instant();
        final ChatClientResponse response;
        try {
            response = chain.nextCall(request);
        } catch (final RuntimeException e) {
            llmUsageService.record(usage(context, null, null, Status.FAILED, abbreviate(e.toString()), start));
            throw e;
        }

        final ChatResponse chatResponse = response.chatResponse();
        final ChatResponseMetadata metadata = chatResponse != null ? chatResponse.getMetadata() : null;
        final Usage usage = metadata != null ? metadata.getUsage() : null;
        log.info("Tokens used - userId={}, operation={}, usage={}", context.get(LlmCallContext.USER_ID),
                operation(context), usage);
        llmUsageService.record(usage(context, metadata != null ? metadata.getModel() : null, usage,
                Status.SUCCEEDED, null, start));
        return response;
    }

    // Nothing streams yet, so a stream is passed through unrecorded rather than broken.
    @Override
    public Flux<ChatClientResponse> adviseStream(final ChatClientRequest request, final StreamAdvisorChain chain) {
        return chain.nextStream(request);
    }

    @Override
    public String getName() {
        return "LLM Usage Advisor";
    }

    @Override
    public int getOrder() {
        return 1;
    }

    private LlmUsage usage(final Map<String, Object> context, final String model, final Usage usage,
                           final Status status, final String failureReason, final Instant start) {
        final Instant end = clock.instant();
        final Operation operation = operation(context);
        if (context.get(LlmCallContext.USER_ID) == null && operation != Operation.DEMO_CHAT) {
            log.warn("LLM call made without a userId - operation={}", operation);
        }
        return new LlmUsage(idGenerator.get(),
                (String) context.get(LlmCallContext.REQUEST_ID),
                (String) context.get(LlmCallContext.USER_ID),
                operation,
                model,
                usage != null ? orZero(usage.getPromptTokens()) : 0,
                usage != null ? orZero(usage.getCompletionTokens()) : 0,
                usage != null ? orZero(usage.getTotalTokens()) : 0,
                usage != null ? orZero(usage.getCacheReadInputTokens()) : 0,
                (String) context.get(LlmCallContext.RUN_ID),
                (String) context.get(LlmCallContext.COMPANY_NAME),
                status,
                failureReason,
                start,
                Duration.between(start, end).toMillis());
    }

    private static Operation operation(final Map<String, Object> context) {
        return context.get(LlmCallContext.OPERATION) instanceof Operation operation ? operation : Operation.UNKNOWN;
    }

    private static int orZero(final Number tokens) {
        return tokens != null ? tokens.intValue() : 0;
    }

    private static String abbreviate(final String text) {
        return text.length() <= MAX_FAILURE_REASON_LENGTH ? text : text.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
