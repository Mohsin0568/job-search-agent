package com.systa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.systa.config.ApplicationProperties;
import com.systa.model.CandidateProfile;
import com.systa.model.CompanySearchResult;
import com.systa.model.JobListing;
import com.systa.model.JobSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class JobSearchLlmService {

    private static final DateTimeFormatter PROMPT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final ResourceLoader resourceLoader;
    private final ApplicationProperties applicationProperties;

    @Value("${job-search.system-prompt-path:classpath:system_prompts/job_search_system_prompt.txt}")
    private String systemPromptPath;

    public JobSearchLlmService(final ChatClient chatClient, final ResourceLoader resourceLoader,
                                final ApplicationProperties applicationProperties) {
        this.chatClient = chatClient;
        this.resourceLoader = resourceLoader;
        this.applicationProperties = applicationProperties;
    }

    public CompanySearchResult searchJobsForCompanyBatch(final String userId, final CandidateProfile candidateProfile,
                                                           final String company, final List<String> sources) {
        final String sourcesPromptList = sources.stream()
                .map(source -> "- " + source)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        final String systemPrompt = loadSystemPromptTemplate().formatted(
                sourcesPromptList,
                LocalDate.now().format(PROMPT_DATE_FORMAT),
                candidateProfile.desiredRole(),
                String.join(", ", candidateProfile.skills()),
                candidateProfile.currentJobDescription());

        log.info("Starting job search - userId={}, company={}, sources={}, desiredRole={}, skills=[{}]",
                userId, company, sources, candidateProfile.desiredRole(), String.join(", ", candidateProfile.skills()));

        final String userMessage = "Find all current UK job openings at " + company
                + " across all the sources listed above."
                + " Search thoroughly and return every matching role from every source.";

        String rawResponse = callChatModelWithRateLimitRetry(userId, company, sources, systemPrompt, userMessage);

        rawResponse = stripMarkdownFences(rawResponse);

        log.info("Raw LLM job search response - userId={}, company={}, sources={}, response={}",
                userId, company, sources, rawResponse);

        final JobSearchResponse jobSearchResponse;
        try {
            jobSearchResponse = OBJECT_MAPPER.readValue(rawResponse, JobSearchResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse job search response from LLM - userId={}, company={}, sources={}, response={}",
                    userId, company, sources, rawResponse, e);
            throw new JobSearchParseException("Failed to parse job search response from LLM", e);
        }

        final List<JobListing> jobs = jobSearchResponse.companies().stream()
                .flatMap(companySearchResult -> companySearchResult.jobs().stream())
                .toList();

        return new CompanySearchResult(company, jobs);
    }

    private String callChatModelWithRateLimitRetry(final String userId, final String company, final List<String> sources,
                                                     final String systemPrompt, final String userMessage) {
        final int maxAttemptsPerBatch = applicationProperties.maxAttemptsPerBatch();
        final Duration rateLimitRetryDelay = applicationProperties.rateLimitRetryDelay();

        for (int attempt = 1; attempt <= maxAttemptsPerBatch; attempt++) {
            try {
                return chatClient.prompt()
                        .system(systemPrompt)
                        .user(userMessage)
                        .options(OpenAiChatOptions.builder()
                                .model(applicationProperties.model()))
                        .call()
                        .content();
            } catch (final RuntimeException e) {
                final boolean canRetry = attempt < maxAttemptsPerBatch && isRateLimitError(e);
                if (!canRetry) {
                    throw e;
                }
                log.warn("Rate limited by LLM provider, retrying in {} - userId={}, company={}, sources={}, attempt={}",
                        rateLimitRetryDelay, userId, company, sources, attempt, e);
                sleep(rateLimitRetryDelay);
            }
        }
        throw new IllegalStateException("Unreachable: retry loop must return or throw");
    }

    private boolean isRateLimitError(final Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof HttpClientErrorException.TooManyRequests) {
                return true;
            }
            final String message = current.getMessage();
            if (message != null
                    && (message.contains("429") || message.toLowerCase(Locale.ROOT).contains("rate limit"))) {
                return true;
            }
        }
        return false;
    }

    private void sleep(final Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry after a rate limit error", e);
        }
    }

    private String loadSystemPromptTemplate() {
        try {
            return new String(resourceLoader.getResource(systemPromptPath)
                    .getInputStream()
                    .readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load system prompt from: " + systemPromptPath, e);
        }
    }

    // gpt-4.1 sometimes wraps the JSON in ```json fences despite the prompt saying not to
    private String stripMarkdownFences(final String content) {
        return content.replaceAll("(?s)```json\\s*", "")
                      .replaceAll("(?s)```\\s*", "")
                      .trim();
    }
}
