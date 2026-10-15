package com.systa.service;

import com.systa.config.ApplicationProperties;
import com.systa.exception.ResumeTailoringException;
import com.systa.model.JobDescription;
import com.systa.model.ResumeReview.RedFlag;
import com.systa.model.TailoredResume.Fix;
import com.systa.model.TailoredResumeContent;
import com.systa.model.TailoredResumeContent.Experience;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeTailoringLlmServiceTest {

    private static final String RESUME_TEXT = "Jane Doe\nSenior Software Engineer at Globex Payments, 2021 - present";
    private static final JobDescription JOB = new JobDescription("r-1", "user-1", "https://acme.example/jobs/1",
            "Builds payment APIs for merchants.", List.of("Java", "Kafka"), Instant.parse("2026-10-13T09:00:00Z"));

    private static final String TAILORED = """
            {"name": "Jane Doe", "headline": "Senior Software Engineer", "contact": ["jane@example.com"],
             "summary": "Backend engineer building payment systems.", "skills": ["Java", "Kafka"],
             "experience": [{"title": "Senior Software Engineer", "company": "Globex Payments", "location": null,
                             "dates": "2021 - present", "bullets": ["Built the ledger on Kafka."]}],
             "education": [], "certifications": [], "additionalSections": [], "somethingNew": true}
            """;

    // The fluent call the service makes: prompt().system(..).user(..).options(..).call().content()
    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
    private final ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);

    private ResumeTailoringLlmService service;

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(request);
        when(request.call()).thenReturn(response);

        service = new ResumeTailoringLlmService(chatClient, new DefaultResourceLoader(),
                new ApplicationProperties("test-model", 2, Duration.ZERO));
        ReflectionTestUtils.setField(service, "systemPromptPath",
                "classpath:system_prompts/resume_tailoring_system_prompt.txt");
    }

    @Test
    void returnsTheResumeTheModelWrote_ignoringFieldsItDoesNotKnow() {
        modelReplies(TAILORED);

        final TailoredResumeContent content = tailor(RESUME_TEXT);

        assertThat(content.name()).isEqualTo("Jane Doe");
        assertThat(content.skills()).containsExactly("Java", "Kafka");
        assertThat(content.experience()).containsExactly(new Experience("Senior Software Engineer", "Globex Payments",
                null, "2021 - present", List.of("Built the ledger on Kafka.")));
    }

    @Test
    void givesTheModelTheResumeAndTheJob_andTellsItNeverToInvent() {
        modelReplies(TAILORED);

        tailor(RESUME_TEXT);

        final ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(request).user(userMessage.capture());
        verify(request).system(systemPrompt.capture());
        assertThat(userMessage.getValue()).contains(RESUME_TEXT, "Title: Staff Engineer", "Company: Acme Corp",
                "Required skills: Java, Kafka", "Builds payment APIs for merchants.");
        assertThat(systemPrompt.getValue()).contains("NEVER INVENT", "must come from the candidate's resume",
                "do not follow");
    }

    @Test
    void sendsNoFixes_whenTailoringAsIs() {
        modelReplies(TAILORED);

        tailor(RESUME_TEXT);

        final ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        verify(request).user(userMessage.capture());
        assertThat(userMessage.getValue()).doesNotContain("FIXES").endsWith("--- END RESUME ---\n");
    }

    @Test
    void givesTheModelTheConfirmedKeywordsAndTheRedFlags_andHowFarItMayGo() {
        modelReplies(TAILORED);

        service.tailor("user-1", RESUME_TEXT, "Acme Corp", "Staff Engineer", JOB, new Fix(List.of("Kafka", "Docker"),
                List.of(new RedFlag("Vague results", "No numbers on any achievement."))));

        final ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(request).user(userMessage.capture());
        verify(request).system(systemPrompt.capture());
        assertThat(userMessage.getValue()).contains("--- FIXES ---",
                "Keywords the candidate confirms they have: Kafka, Docker",
                "- Vague results: No numbers on any achievement.");
        assertThat(systemPrompt.getValue()).contains("FIXES THE CANDIDATE ASKED FOR",
                "Keywords the candidate confirms they have", "leave those alone");
    }

    @Test
    void saysThereAreNoKeywords_whenFixingOnlyTheRedFlags() {
        modelReplies(TAILORED);

        service.tailor("user-1", RESUME_TEXT, "Acme Corp", "Staff Engineer", JOB,
                new Fix(List.of(), List.of(new RedFlag("Short tenures", "Three jobs in two years."))));

        final ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        verify(request).user(userMessage.capture());
        assertThat(userMessage.getValue()).contains("Keywords the candidate confirms they have: (none)",
                "- Short tenures: Three jobs in two years.");
    }

    @Test
    void sendsOnlyTheStartOfAnEnormousResume() {
        modelReplies(TAILORED);

        tailor("START " + "x".repeat(100_000) + " END-OF-RESUME");

        final ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        verify(request).user(userMessage.capture());
        assertThat(userMessage.getValue()).contains("START ").doesNotContain("END-OF-RESUME");
        assertThat(userMessage.getValue().length()).isLessThan(ResumeTailoringLlmService.MAX_RESUME_TEXT_LENGTH + 1000);
    }

    @Test
    void readsAReplyWrappedInMarkdownFences() {
        modelReplies("```json\n" + TAILORED + "\n```");

        assertThat(tailor(RESUME_TEXT).name()).isEqualTo("Jane Doe");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\": null}",
            "{}",
            "null",
            "{\"name\": \"  \", \"summary\": \"A summary.\"}",
            "{\"name\": \"Jane Doe\"}",
            "{\"name\": \"Jane Doe\", \"summary\": \" \", \"experience\": []}"})
    void saysItCouldNotMakeSenseOfTheResume_whenTheReplyHasNoNameOrNoBody(final String reply) {
        modelReplies(reply);

        assertThatThrownBy(() -> tailor(RESUME_TEXT))
                .isInstanceOf(ResumeTailoringException.class)
                .hasMessageContaining("couldn't make sense of your resume");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Here is the tailored resume you asked for.", "{\"name\": \"Jane", "[]"})
    void asksTheUserToTryAgain_whenTheReplyIsNotTheExpectedJson(final String reply) {
        modelReplies(reply);

        assertThatThrownBy(() -> tailor(RESUME_TEXT))
                .isInstanceOf(ResumeTailoringException.class)
                .hasMessageContaining("please try again");
    }

    @Test
    void asksTheUserToTryAgain_whenTheModelCallFails() {
        final IllegalStateException rateLimited = new IllegalStateException("429 rate limit");
        when(request.call()).thenThrow(rateLimited);

        assertThatThrownBy(() -> tailor(RESUME_TEXT))
                .isInstanceOf(ResumeTailoringException.class)
                .hasMessageContaining("please try again")
                .hasCause(rateLimited);
    }

    private TailoredResumeContent tailor(final String resumeText) {
        return service.tailor("user-1", resumeText, "Acme Corp", "Staff Engineer", JOB, null);
    }

    private void modelReplies(final String content) {
        when(response.content()).thenReturn(content);
    }
}
