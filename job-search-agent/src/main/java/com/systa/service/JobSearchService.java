package com.systa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.systa.model.JobSearchResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class JobSearchService {

    private static final String JOB_SEARCH_MODEL = "gpt-4o-search-preview";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    private final ResourceLoader resourceLoader;

    @Value("${job-search.resume-path:classpath:resume.pdf}")
    private String resumePath;

    @Value("${job-search.system-prompt-path:classpath:system_prompts/job_search_system_prompt.txt}")
    private String systemPromptPath;

    public JobSearchService(final ChatClient chatClient, final ResourceLoader resourceLoader) {
        this.chatClient = chatClient;
        this.resourceLoader = resourceLoader;
    }

    public JobSearchResponse searchJobs(final String companyName) {
        final String resumeContent = loadResumeText();
        final String systemPrompt = loadSystemPromptTemplate().formatted(resumeContent);

        String rawResponse = chatClient.prompt()
                .system(systemPrompt)
                .user("Find all current UK job openings at " + companyName
                        + ". Search thoroughly across all available sources and return every matching role.")
                .options(OpenAiChatOptions.builder()
                        .model(JOB_SEARCH_MODEL))
                .call()
                .content();

        rawResponse = stripMarkdownFences(rawResponse);

        try {
            return OBJECT_MAPPER.readValue(rawResponse, JobSearchResponse.class);
        } catch (JsonProcessingException e) {
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

    private String loadResumeText() {
        try {
            final byte[] pdfBytes = resourceLoader.getResource(resumePath)
                    .getInputStream()
                    .readAllBytes();
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                return new PDFTextStripper().getText(document);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load resume from: " + resumePath, e);
        }
    }

    private String stripMarkdownFences(final String content) {
        return content.replaceAll("(?s)```json\\s*", "")
                      .replaceAll("(?s)```\\s*", "")
                      .trim();
    }
}
