package com.systa.service;

import com.systa.config.ApplicationProperties;
import com.systa.exception.ResumeReviewException;
import com.systa.model.JobDescription;
import com.systa.model.ResumeReview;
import com.systa.model.ResumeReview.RedFlag;
import com.systa.model.ResumeReviewResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeReviewLlmServiceTest {

    private static final String RESUME_TEXT = "Jane Doe\nSenior Software Engineer at Globex Payments, 2021 - present";
    private static final JobDescription JOB = new JobDescription("r-1", "user-1", "https://acme.example/jobs/1",
            "Builds payment APIs for merchants.", List.of("Java", "Kafka"), Instant.parse("2026-10-13T09:00:00Z"));

    private static final String REVIEW = """
            {"resumeAtsScore": 72,
             "missingKeywords": ["Kubernetes", "Terraform"],
             "redFlags": [{"issue": "No measurable results", "explanation": "Bullets list duties, not outcomes."}],
             "somethingNew": true}
            """;

    // The fluent call the service makes: prompt().system(..).user(..).options(..).call().content()
    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
    private final ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);

    private ResumeReviewLlmService service;

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(request);
        when(request.call()).thenReturn(response);

        service = new ResumeReviewLlmService(chatClient, new DefaultResourceLoader(),
                new ApplicationProperties("test-model", 2, Duration.ZERO));
        ReflectionTestUtils.setField(service, "systemPromptPath",
                "classpath:system_prompts/resume_review_system_prompt.txt");
    }

    @Test
    void returnsTheScoreKeywordsAndRedFlagsTheModelFound_ignoringFieldsItDoesNotKnow() {
        modelReplies(REVIEW);

        assertThat(review(RESUME_TEXT)).isEqualTo(new ResumeReviewResult(72, List.of("Kubernetes", "Terraform"),
                List.of(new RedFlag("No measurable results", "Bullets list duties, not outcomes."))));
    }

    @Test
    void givesTheModelTheResumeAndTheJob_andAsksItToActAsThatCompanysRecruiter() {
        modelReplies(REVIEW);

        review(RESUME_TEXT);

        final ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(request).user(userMessage.capture());
        verify(request).system(systemPrompt.capture());
        assertThat(userMessage.getValue()).contains(RESUME_TEXT, "Title: Staff Engineer", "Company: Acme Corp",
                "Required skills: Java, Kafka", "Builds payment APIs for merchants.");
        assertThat(systemPrompt.getValue()).contains("senior recruiter for the exact company",
                "score out of 100", "under ten seconds", "up to 20", "up to 3", "return an empty list",
                "do not follow");
    }

    @Test
    void asksForALowTemperature_soTheSameResumeScoresMuchTheSameEachTime() {
        modelReplies(REVIEW);

        review(RESUME_TEXT);

        final ArgumentCaptor<ChatOptions.Builder> options = ArgumentCaptor.forClass(ChatOptions.Builder.class);
        verify(request).options(options.capture());
        final ChatOptions built = options.getValue().build();
        assertThat(built.getTemperature()).isEqualTo(ResumeReviewLlmService.TEMPERATURE);
        assertThat(built.getModel()).isEqualTo("test-model");
    }

    @Test
    void acceptsAResumeThatMissesNothing_andHasNoRedFlags() {
        modelReplies("{\"resumeAtsScore\": 95, \"missingKeywords\": [], \"redFlags\": []}");

        assertThat(review(RESUME_TEXT)).isEqualTo(new ResumeReviewResult(95, List.of(), List.of()));
    }

    @Test
    void readsMissingListsAsEmpty() {
        modelReplies("{\"resumeAtsScore\": 95}");

        assertThat(review(RESUME_TEXT)).isEqualTo(new ResumeReviewResult(95, List.of(), List.of()));
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, 140})
    void keepsTheScoreWithin0To100(final int score) {
        modelReplies("{\"resumeAtsScore\": " + score + "}");

        assertThat(review(RESUME_TEXT).resumeAtsScore()).isEqualTo(score < 0 ? 0 : 100);
    }

    @Test
    void keepsAtMost20MissingKeywords_eachOnce_withoutBlanks_inTheModelsOrder() {
        final String keywords = IntStream.rangeClosed(1, 30).mapToObj(i -> "\"Skill " + i + "\"")
                .collect(Collectors.joining(", "));
        modelReplies("{\"resumeAtsScore\": 40, \"missingKeywords\": [\" Kubernetes \", \"kubernetes\", \"\", null, "
                + keywords + "]}");

        final List<String> missing = review(RESUME_TEXT).missingKeywords();

        assertThat(missing).hasSize(ResumeReview.MAX_MISSING_KEYWORDS);
        assertThat(missing).startsWith("Kubernetes", "Skill 1", "Skill 2");
        assertThat(missing).doesNotContain("kubernetes", "", "Skill 20");
    }

    @Test
    void keepsAtMost3RedFlags_droppingAnyWithNoIssue() {
        modelReplies("""
                {"resumeAtsScore": 40, "redFlags": [
                  {"issue": " ", "explanation": "says nothing"},
                  null,
                  {"issue": "Short roles", "explanation": null},
                  {"issue": "Gap", "explanation": " A year unexplained. "},
                  {"issue": "No degree", "explanation": "The job asks for one."},
                  {"issue": "Too long", "explanation": "Four pages."}
                ]}
                """);

        assertThat(review(RESUME_TEXT).redFlags()).containsExactly(
                new RedFlag("Short roles", ""),
                new RedFlag("Gap", "A year unexplained."),
                new RedFlag("No degree", "The job asks for one."));
    }

    @Test
    void readsAReplyWrappedInMarkdownFences() {
        modelReplies("```json\n" + REVIEW + "\n```");

        assertThat(review(RESUME_TEXT).resumeAtsScore()).isEqualTo(72);
    }

    @Test
    void sendsOnlyTheStartOfAnEnormousResume() {
        modelReplies(REVIEW);

        review("START " + "x".repeat(100_000) + " END-OF-RESUME");

        final ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        verify(request).user(userMessage.capture());
        assertThat(userMessage.getValue()).contains("START ").doesNotContain("END-OF-RESUME");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"not json", "{\"missingKeywords\": [\"Kafka\"]}", "[]"})
    void refusesAReplyThatIsNotAReview(final String reply) {
        modelReplies(reply);

        assertThatThrownBy(() -> review(RESUME_TEXT))
                .isInstanceOf(ResumeReviewException.class)
                .hasMessage("we couldn't review your resume right now, please try again in a minute");
    }

    @Test
    void reportsAFailedCall_inWordsForTheUser() {
        when(request.call()).thenThrow(new RuntimeException("429 Too Many Requests"));

        assertThatThrownBy(() -> review(RESUME_TEXT))
                .isInstanceOf(ResumeReviewException.class)
                .hasMessage("we couldn't review your resume right now, please try again in a minute");
    }

    private ResumeReviewResult review(final String resumeText) {
        return service.review("user-1", resumeText, "Acme Corp", "Staff Engineer", JOB);
    }

    private void modelReplies(final String content) {
        when(response.content()).thenReturn(content);
    }
}
