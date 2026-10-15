package com.systa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.systa.config.ApplicationProperties;
import com.systa.config.ChatConfig;
import com.systa.exception.ResumeReviewException;
import com.systa.model.JobDescription;
import com.systa.model.ResumeReview;
import com.systa.model.ResumeReview.RedFlag;
import com.systa.model.ResumeReviewResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Has the LLM review a resume against one job as that company's recruiter would: an ATS score, the
 * keywords the resume is missing and the red flags a hiring manager would spot.
 */
@Service
@Slf4j
public class ResumeReviewLlmService {

    // Far more than a resume holds; keeps one enormous PDF from becoming an enormous prompt.
    static final int MAX_RESUME_TEXT_LENGTH = 30_000;

    // Low, so the same resume and job score much the same each time they are reviewed.
    static final double TEMPERATURE = 0.2;

    private static final String TRY_AGAIN = "we couldn't review your resume right now, please try again in a minute";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final ResourceLoader resourceLoader;
    private final ApplicationProperties applicationProperties;

    @Value("${job-search.resume-review-prompt-path:classpath:system_prompts/resume_review_system_prompt.txt}")
    private String systemPromptPath;

    // The tool-free client: a review needs only the resume and the job, both already in hand.
    public ResumeReviewLlmService(@Qualifier(ChatConfig.RESUME_TAILORING_CHAT_CLIENT) final ChatClient chatClient,
                                   final ResourceLoader resourceLoader,
                                   final ApplicationProperties applicationProperties) {
        this.chatClient = chatClient;
        this.resourceLoader = resourceLoader;
        this.applicationProperties = applicationProperties;
    }

    /**
     * @return the review, with the score kept to 0-100 and each list trimmed to its limit
     * @throws ResumeReviewException when the model fails, or returns something that isn't a review
     */
    public ResumeReviewResult review(final String userId, final String resumeText, final String companyName,
                                     final String jobTitle, final JobDescription jobDescription) {
        log.info("Reviewing resume - userId={}, company={}, jobTitle={}", userId, companyName, jobTitle);

        final String userMessage = """
                Review the resume below against this job.

                --- JOB ---
                Title: %s
                Company: %s
                Required skills: %s
                Description:
                %s
                --- END JOB ---

                --- RESUME ---
                %s
                --- END RESUME ---
                """.formatted(jobTitle, companyName, String.join(", ", orEmpty(jobDescription.requiredSkills())),
                jobDescription.description(), abbreviate(resumeText));

        final String rawResponse;
        try {
            rawResponse = chatClient.prompt()
                    .system(loadSystemPrompt())
                    .user(userMessage)
                    .options(OpenAiChatOptions.builder()
                            .model(applicationProperties.model())
                            .temperature(TEMPERATURE))
                    .call()
                    .content();
        } catch (final RuntimeException e) {
            log.error("Resume review call failed - userId={}, company={}, jobTitle={}", userId, companyName,
                    jobTitle, e);
            throw new ResumeReviewException(TRY_AGAIN, e);
        }

        final ResumeReviewResult result = parse(userId, rawResponse);
        if (result == null || result.resumeAtsScore() == null) {
            log.warn("Resume review returned no score - userId={}", userId);
            throw new ResumeReviewException(TRY_AGAIN);
        }
        return new ResumeReviewResult(
                Math.clamp(result.resumeAtsScore(), 0, 100),
                keywords(result.missingKeywords()),
                redFlags(result.redFlags()));
    }

    // Trimmed, blanks dropped, and each keyword once whatever its case - in the model's order, most important first.
    private static List<String> keywords(final List<String> keywords) {
        final Map<String, String> distinct = new LinkedHashMap<>();
        orEmpty(keywords).stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(keyword -> !keyword.isEmpty())
                .forEach(keyword -> distinct.putIfAbsent(keyword.toLowerCase(Locale.ROOT), keyword));
        return distinct.values().stream().limit(ResumeReview.MAX_MISSING_KEYWORDS).toList();
    }

    // A red flag with no issue says nothing; one with no explanation still names the problem.
    private static List<RedFlag> redFlags(final List<RedFlag> redFlags) {
        return orEmpty(redFlags).stream()
                .filter(flag -> flag != null && flag.issue() != null && !flag.issue().isBlank())
                .map(flag -> new RedFlag(flag.issue().strip(),
                        flag.explanation() == null ? "" : flag.explanation().strip()))
                .limit(ResumeReview.MAX_RED_FLAGS)
                .toList();
    }

    private ResumeReviewResult parse(final String userId, final String rawResponse) {
        try {
            return OBJECT_MAPPER.readValue(stripMarkdownFences(rawResponse), ResumeReviewResult.class);
        } catch (final JsonProcessingException | IllegalArgumentException e) {
            log.error("Failed to parse resume review response from LLM - userId={}", userId, e);
            throw new ResumeReviewException(TRY_AGAIN, e);
        }
    }

    private static <T> List<T> orEmpty(final List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String abbreviate(final String text) {
        return text.length() <= MAX_RESUME_TEXT_LENGTH ? text : text.substring(0, MAX_RESUME_TEXT_LENGTH);
    }

    private String loadSystemPrompt() {
        try {
            return new String(resourceLoader.getResource(systemPromptPath)
                    .getInputStream()
                    .readAllBytes());
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to load system prompt from: " + systemPromptPath, e);
        }
    }

    // gpt-4.1 sometimes wraps the JSON in ```json fences despite the prompt saying not to
    private static String stripMarkdownFences(final String content) {
        if (content == null) {
            throw new IllegalArgumentException("The model returned no content");
        }
        return content.replaceAll("(?s)```json\\s*", "")
                      .replaceAll("(?s)```\\s*", "")
                      .trim();
    }
}
