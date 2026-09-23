package com.systa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class JobSearchLlmService {

    private static final String JOB_SEARCH_MODEL = "gpt-4.1";

    private static final DateTimeFormatter PROMPT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final List<String> JOB_SOURCES = List.of(
            "The company's official careers/jobs portal",
            "LinkedIn (linkedin.com/jobs)",
            "Indeed UK (uk.indeed.com)",
            "Glassdoor UK (glassdoor.co.uk)",
            "TotalJobs (totaljobs.com)",
            "Reed (reed.co.uk)");

    // Batching keeps each call's scraped-content volume under gpt-4.1's org TPM limit -
    // a single call covering all 6 sources was measured at ~36.8k tokens against a 30k/min cap.
    private static final int SOURCES_PER_BATCH = 3;

    private static final List<List<String>> JOB_SOURCE_BATCHES = batchSources(JOB_SOURCES, SOURCES_PER_BATCH);

    private final ChatClient chatClient;
    private final ResourceLoader resourceLoader;

    @Value("${job-search.system-prompt-path:classpath:system_prompts/job_search_system_prompt.txt}")
    private String systemPromptPath;

    public JobSearchLlmService(final ChatClient chatClient, final ResourceLoader resourceLoader) {
        this.chatClient = chatClient;
        this.resourceLoader = resourceLoader;
    }

    public CompanySearchResult searchJobsForCompany(final String userId, final CandidateProfile candidateProfile,
                                                      final String company) {
        final List<JobListing> jobs = new ArrayList<>();
        for (final List<String> sourceBatch : JOB_SOURCE_BATCHES) {
            try {
                jobs.addAll(searchJobsForCompanyAndSources(userId, candidateProfile, company, sourceBatch));
            } catch (final Exception e) {
                // Isolate failures per (company, batch) so one bad/rate-limited call
                // doesn't lose results already gathered from the other batches.
                log.error("Job search failed for a source batch - userId={}, company={}, sources={}",
                        userId, company, sourceBatch, e);
            }
        }
        return new CompanySearchResult(company, jobs);
    }

    private List<JobListing> searchJobsForCompanyAndSources(final String userId, final CandidateProfile candidateProfile,
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

        String rawResponse = chatClient.prompt()
                .system(systemPrompt)
                .user("Find all current UK job openings at " + company + " across all the sources listed above."
                        + " Search thoroughly and return every matching role from every source.")
                .options(OpenAiChatOptions.builder()
                        .model(JOB_SEARCH_MODEL))
                .call()
                .content();

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

        return jobSearchResponse.companies().stream()
                .flatMap(companySearchResult -> companySearchResult.jobs().stream())
                .toList();
    }

    private static List<List<String>> batchSources(final List<String> sources, final int batchSize) {
        final List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < sources.size(); i += batchSize) {
            batches.add(sources.subList(i, Math.min(i + batchSize, sources.size())));
        }
        return batches;
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
