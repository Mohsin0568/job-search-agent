package com.systa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.systa.config.ApplicationProperties;
import com.systa.config.ChatConfig;
import com.systa.exception.ResumeTailoringException;
import com.systa.model.JobDescription;
import com.systa.model.ResumeReview.RedFlag;
import com.systa.model.TailoredResume.Fix;
import com.systa.model.TailoredResumeContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.stream.Collectors;

/** Has the LLM rewrite a resume for one job, without adding anything the resume doesn't say. */
@Service
@Slf4j
public class ResumeTailoringLlmService {

    // Far more than a resume holds; keeps one enormous PDF from becoming an enormous prompt.
    static final int MAX_RESUME_TEXT_LENGTH = 30_000;

    private static final String TRY_AGAIN = "we couldn't tailor your resume right now, please try again in a minute";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final ResourceLoader resourceLoader;
    private final ApplicationProperties applicationProperties;

    @Value("${job-search.resume-tailoring-prompt-path:classpath:system_prompts/resume_tailoring_system_prompt.txt}")
    private String systemPromptPath;

    public ResumeTailoringLlmService(@Qualifier(ChatConfig.RESUME_TAILORING_CHAT_CLIENT) final ChatClient chatClient,
                                      final ResourceLoader resourceLoader,
                                      final ApplicationProperties applicationProperties) {
        this.chatClient = chatClient;
        this.resourceLoader = resourceLoader;
        this.applicationProperties = applicationProperties;
    }

    /**
     * @param fix what to fix from the job's resume review, or null to tailor it as is
     * @throws ResumeTailoringException when the model fails, or returns something that isn't a resume
     */
    public TailoredResumeContent tailor(final String userId, final String resumeText, final String companyName,
                                         final String jobTitle, final JobDescription jobDescription, final Fix fix) {
        log.info("Tailoring resume - userId={}, company={}, jobTitle={}, fixing={}", userId, companyName, jobTitle,
                fix != null);

        final String userMessage = """
                Tailor the resume below to this job.

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
                """.formatted(jobTitle, companyName, String.join(", ", jobDescription.requiredSkills()),
                jobDescription.description(), abbreviate(resumeText)) + fixes(fix);

        final String rawResponse;
        try {
            rawResponse = chatClient.prompt()
                    .system(loadSystemPrompt())
                    .user(userMessage)
                    .options(OpenAiChatOptions.builder()
                            .model(applicationProperties.model()))
                    .call()
                    .content();
        } catch (final RuntimeException e) {
            log.error("Resume tailoring call failed - userId={}, company={}, jobTitle={}", userId, companyName,
                    jobTitle, e);
            throw new ResumeTailoringException(TRY_AGAIN, e);
        }

        final TailoredResumeContent content = parse(userId, rawResponse);
        final boolean hasBody = content != null && (isPresent(content.summary())
                || (content.experience() != null && !content.experience().isEmpty()));
        if (content == null || !isPresent(content.name()) || !hasBody) {
            // The resume's own text never goes in the log, only the fact that nothing came of it.
            log.warn("Resume tailoring returned no usable resume - userId={}", userId);
            throw new ResumeTailoringException(
                    "we couldn't make sense of your resume - check the PDF you uploaded is your resume");
        }
        return content;
    }

    // Without a fix the message is exactly what it was before fixes existed.
    private static String fixes(final Fix fix) {
        if (fix == null) {
            return "";
        }
        final String keywords = fix.keywords().isEmpty() ? "(none)" : String.join(", ", fix.keywords());
        final String redFlags = fix.redFlags().isEmpty() ? "(none)" : fix.redFlags().stream()
                .map(ResumeTailoringLlmService::redFlag)
                .collect(Collectors.joining("\n"));
        return """

                --- FIXES ---
                Keywords the candidate confirms they have: %s
                Red flags a recruiter raised:
                %s
                --- END FIXES ---
                """.formatted(keywords, redFlags);
    }

    private TailoredResumeContent parse(final String userId, final String rawResponse) {
        try {
            return OBJECT_MAPPER.readValue(stripMarkdownFences(rawResponse), TailoredResumeContent.class);
        } catch (final JsonProcessingException | IllegalArgumentException e) {
            log.error("Failed to parse resume tailoring response from LLM - userId={}", userId, e);
            throw new ResumeTailoringException(TRY_AGAIN, e);
        }
    }

    private static String redFlag(final RedFlag redFlag) {
        return "- " + redFlag.issue() + ": " + redFlag.explanation();
    }

    private static boolean isPresent(final String value) {
        return value != null && !value.isBlank();
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
