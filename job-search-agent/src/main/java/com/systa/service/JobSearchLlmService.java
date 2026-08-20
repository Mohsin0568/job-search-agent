package com.systa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.systa.exception.CandidateProfileNotFoundException;
import com.systa.exception.CompanyPreferencesNotSetException;
import com.systa.model.CandidateProfile;
import com.systa.model.JobSearchResponse;
import com.systa.repository.CandidateProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class JobSearchLlmService {

    private static final String JOB_SEARCH_MODEL = "gpt-4.1";

    private static final DateTimeFormatter PROMPT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final ResourceLoader resourceLoader;
    private final CandidateProfileRepository candidateProfileRepository;

    @Value("${job-search.system-prompt-path:classpath:system_prompts/job_search_system_prompt.txt}")
    private String systemPromptPath;

    public JobSearchLlmService(final ChatClient chatClient,
                                final ResourceLoader resourceLoader,
                                final CandidateProfileRepository candidateProfileRepository) {
        this.chatClient = chatClient;
        this.resourceLoader = resourceLoader;
        this.candidateProfileRepository = candidateProfileRepository;
    }

    public JobSearchResponse searchJobs(final String userId) {
        final CandidateProfile candidateProfile = candidateProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CandidateProfileNotFoundException(userId));

        if (candidateProfile.companyPreferences() == null || candidateProfile.companyPreferences().isEmpty()) {
            throw new CompanyPreferencesNotSetException(userId);
        }

        final String systemPrompt = loadSystemPromptTemplate().formatted(
                LocalDate.now().format(PROMPT_DATE_FORMAT),
                candidateProfile.desiredRole(),
                String.join(", ", candidateProfile.skills()),
                candidateProfile.currentJobDescription());

        final String companyList = String.join(", ", candidateProfile.companyPreferences());

        log.info("Starting job search - userId={}, companies=[{}], desiredRole={}, skills=[{}]",
                userId, companyList, candidateProfile.desiredRole(), String.join(", ", candidateProfile.skills()));

        String rawResponse = chatClient.prompt()
                .system(systemPrompt)
                .user("Find all current UK job openings at each of the following companies: " + companyList
                        + ". Search thoroughly across all available sources for every company and return "
                        + "every matching role, grouped by company.")
                .options(OpenAiChatOptions.builder()
                        .model(JOB_SEARCH_MODEL))
                .call()
                .content();

        rawResponse = stripMarkdownFences(rawResponse);

        log.info("Raw LLM job search response - userId={}, response={}", userId, rawResponse);

        try {
            return OBJECT_MAPPER.readValue(rawResponse, JobSearchResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse job search response from LLM - userId={}, response={}",
                    userId, rawResponse, e);
            throw new JobSearchParseException("Failed to parse job search response from LLM", e);
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
