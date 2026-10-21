package com.systa.advisors;

import com.systa.model.LlmUsage.Operation;
import org.springframework.ai.chat.client.ChatClient;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Who an LLM call is for and why, handed to {@link LlmUsageAdvisor} through the call's advisor params:
 * {@code chatClient.prompt().advisors(LlmCallContext.of(userId, Operation.RESUME_REVIEW))...}.
 * Params travel with the request, so this holds even when the call runs on another thread.
 */
public final class LlmCallContext {

    public static final String USER_ID = "llmUsage.userId";
    public static final String OPERATION = "llmUsage.operation";
    public static final String RUN_ID = "llmUsage.runId";
    public static final String COMPANY_NAME = "llmUsage.companyName";
    public static final String REQUEST_ID = "llmUsage.requestId";

    private LlmCallContext() {
    }

    public static Consumer<ChatClient.AdvisorSpec> of(final String userId, final Operation operation) {
        return of(userId, operation, null, null);
    }

    public static Consumer<ChatClient.AdvisorSpec> of(final String userId, final Operation operation,
                                                      final String runId, final String companyName) {
        return spec -> {
            // A fresh id each time the context is applied - that is, once per ChatClient call.
            spec.param(REQUEST_ID, UUID.randomUUID().toString());
            // Advisor params can't hold null, so a missing value is left out rather than set.
            putIfPresent(spec, USER_ID, userId);
            putIfPresent(spec, OPERATION, operation);
            putIfPresent(spec, RUN_ID, runId);
            putIfPresent(spec, COMPANY_NAME, companyName);
        };
    }

    private static void putIfPresent(final ChatClient.AdvisorSpec spec, final String key, final Object value) {
        if (value != null) {
            spec.param(key, value);
        }
    }
}
