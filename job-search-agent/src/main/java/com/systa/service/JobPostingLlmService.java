package com.systa.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.systa.config.ApplicationProperties;
import com.systa.config.ChatConfig;
import com.systa.exception.JobDescriptionUnavailableException;
import com.systa.model.JobPosting;
import lombok.extern.slf4j.Slf4j;
import com.systa.advisors.LlmCallContext;
import com.systa.model.LlmUsage.Operation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Has the LLM fetch one job posting's page and report what the role asks for. */
@Service
@Slf4j
public class JobPostingLlmService {

    // Backstops for the limits the prompt asks for, so one odd reply can't store an essay.
    static final int MAX_DESCRIPTION_LENGTH = 4000;
    static final int MAX_SKILLS = 25;
    static final int MAX_SKILL_LENGTH = 100;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final ResourceLoader resourceLoader;
    private final ApplicationProperties applicationProperties;

    @Value("${job-search.job-description-prompt-path:classpath:system_prompts/job_description_system_prompt.txt}")
    private String systemPromptPath;

    public JobPostingLlmService(@Qualifier(ChatConfig.JOB_POSTING_CHAT_CLIENT) final ChatClient chatClient,
                                 final ResourceLoader resourceLoader,
                                 final ApplicationProperties applicationProperties) {
        this.chatClient = chatClient;
        this.resourceLoader = resourceLoader;
        this.applicationProperties = applicationProperties;
    }

    /**
     * @throws JobDescriptionUnavailableException when the page can't be read as a posting for this job
     */
    public JobPosting readPosting(final String userId, final String companyName, final String jobTitle,
                                   final String url) {
        log.info("Reading job posting - userId={}, company={}, jobTitle={}, url={}", userId, companyName, jobTitle, url);

        final String userMessage = "Read the job posting at this URL: " + url + "\n"
                + "It is expected to be for the role \"" + jobTitle + "\" at " + companyName + ".";

        final String rawResponse;
        try {
            rawResponse = chatClient.prompt()
                    .advisors(LlmCallContext.of(userId, Operation.JOB_POSTING, null, companyName))
                    .system(loadSystemPrompt())
                    .user(userMessage)
                    .options(OpenAiChatOptions.builder()
                            .model(applicationProperties.model()))
                    .call()
                    .content();
        } catch (final RuntimeException e) {
            log.error("Job posting could not be read - userId={}, url={}", userId, url, e);
            throw new JobDescriptionUnavailableException(
                    "we couldn't read this job posting right now, please try again in a minute", e);
        }

        final PostingReply reply = parse(userId, url, rawResponse);
        if (reply == null || !reply.found() || reply.description() == null || reply.description().isBlank()) {
            final String reason = reply == null || reply.reason() == null ? "" : reply.reason();
            log.info("Job posting not found - userId={}, url={}, reason={}", userId, url, reason);
            throw new JobDescriptionUnavailableException(
                    "we couldn't find this job posting - it may have been removed or need a sign-in to view");
        }

        return new JobPosting(abbreviate(reply.description().strip(), MAX_DESCRIPTION_LENGTH),
                cleanSkills(reply.requiredSkills()));
    }

    private PostingReply parse(final String userId, final String url, final String rawResponse) {
        try {
            return OBJECT_MAPPER.readValue(stripMarkdownFences(rawResponse), PostingReply.class);
        } catch (final JsonProcessingException | IllegalArgumentException e) {
            log.error("Failed to parse job posting response from LLM - userId={}, url={}, response={}",
                    userId, url, rawResponse, e);
            throw new JobDescriptionUnavailableException(
                    "we couldn't read this job posting right now, please try again in a minute", e);
        }
    }

    private static List<String> cleanSkills(final List<String> skills) {
        if (skills == null) {
            return List.of();
        }
        return skills.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(skill -> !skill.isEmpty())
                .map(skill -> abbreviate(skill, MAX_SKILL_LENGTH))
                .distinct()
                .limit(MAX_SKILLS)
                .toList();
    }

    private static String abbreviate(final String text, final int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PostingReply(boolean found, String description, List<String> requiredSkills, String reason) {}
}
