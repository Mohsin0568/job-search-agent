package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * The tokens one request to the model used, and for whom. A ChatClient call that runs tools asks the
 * model several times - once per tool round-trip - and each of those is a document, sharing a requestId.
 * Each retry of a rate-limited call is a ChatClient call of its own, with its own requestId.
 */
@Document(collection = "llm_usage")
@CompoundIndexes({
        @CompoundIndex(name = "user_calledAt", def = "{'userId': 1, 'calledAt': -1}")
})
public record LlmUsage(
        @Id String id,
        // Shared by every round-trip of one ChatClient call; null when the call was made without a LlmCallContext.
        @Indexed String requestId,
        // Null when the call was made for no signed-in user.
        String userId,
        Operation operation,
        // The model that answered, as the provider reports it. Null when the call failed.
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        // Prompt tokens the provider served from its cache - already counted in promptTokens.
        int cachedPromptTokens,
        // The JobSearchRun the call was part of; null outside a job search.
        @Indexed String runId,
        // The company the call was about, when there is one.
        String companyName,
        Status status,
        // Why the call failed; null when it succeeded.
        String failureReason,
        Instant calledAt,
        long durationMs
) {

    public enum Operation {
        JOB_SEARCH,
        JOB_POSTING,
        RESUME_REVIEW,
        RESUME_TAILORING,
        DEMO_CHAT,
        /** A call made without saying what it was for. */
        UNKNOWN
    }

    public enum Status {
        SUCCEEDED,
        /** The call threw - no tokens are known, so they are recorded as 0. */
        FAILED
    }
}
