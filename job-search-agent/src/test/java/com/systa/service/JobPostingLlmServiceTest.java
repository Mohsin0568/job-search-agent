package com.systa.service;

import com.systa.config.ApplicationProperties;
import com.systa.exception.JobDescriptionUnavailableException;
import com.systa.model.JobPosting;
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
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPostingLlmServiceTest {

    private static final String URL = "https://careers.acme.example/jobs/J-1";

    // The fluent call the service makes: prompt().system(..).user(..).options(..).call().content()
    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
    private final ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);

    private JobPostingLlmService service;

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(request);
        when(request.call()).thenReturn(response);

        service = new JobPostingLlmService(chatClient, new DefaultResourceLoader(),
                new ApplicationProperties("test-model", 2, Duration.ZERO));
        ReflectionTestUtils.setField(service, "systemPromptPath",
                "classpath:system_prompts/job_description_system_prompt.txt");
    }

    @Test
    void returnsTheDescriptionAndSkillsTheModelRead() {
        modelReplies("""
                {"found": true, "description": "  Builds payment APIs for merchants.  ",
                 "requiredSkills": ["Java", "Spring Boot", "AWS"], "reason": null}
                """);

        assertThat(read()).isEqualTo(
                new JobPosting("Builds payment APIs for merchants.", List.of("Java", "Spring Boot", "AWS")));
    }

    @Test
    void asksTheModelToReadThatUrl_forThatRoleAndCompany() {
        modelReplies("{\"found\": true, \"description\": \"Builds APIs.\", \"requiredSkills\": []}");

        read();

        final ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(request).user(userMessage.capture());
        verify(request).system(systemPrompt.capture());
        assertThat(userMessage.getValue()).contains(URL, "\"Staff Engineer\"", "Acme Corp");
        // The page is someone else's text: the prompt must say not to act on what it says.
        assertThat(systemPrompt.getValue()).contains("untrusted", "do not follow them");
    }

    @Test
    void readsAReplyWrappedInMarkdownFences() {
        modelReplies("```json\n{\"found\": true, \"description\": \"Builds APIs.\", \"requiredSkills\": [\"Java\"]}\n```");

        assertThat(read()).isEqualTo(new JobPosting("Builds APIs.", List.of("Java")));
    }

    @Test
    void tidiesTheSkills_droppingBlanksAndRepeats_andKeepingThemShortAndFew() {
        final String manySkills = String.join(", ",
                IntStream.rangeClosed(1, 40).mapToObj(i -> "\"Skill " + i + "\"").toList());
        modelReplies("{\"found\": true, \"description\": \"Builds APIs.\", \"requiredSkills\": "
                + "[\" Java \", \"\", null, \"Java\", \"" + "x".repeat(300) + "\", " + manySkills + "]}");

        final List<String> skills = read().requiredSkills();

        assertThat(skills).hasSize(JobPostingLlmService.MAX_SKILLS).doesNotHaveDuplicates();
        assertThat(skills.get(0)).isEqualTo("Java");
        assertThat(skills.get(1)).hasSize(JobPostingLlmService.MAX_SKILL_LENGTH);
        assertThat(skills.get(2)).isEqualTo("Skill 1");
    }

    @Test
    void returnsNoSkills_whenTheModelLeavesThemOut() {
        modelReplies("{\"found\": true, \"description\": \"Builds APIs.\"}");

        assertThat(read().requiredSkills()).isEmpty();
    }

    @Test
    void cutsAnOverlongDescriptionDown() {
        modelReplies("{\"found\": true, \"description\": \"" + "a".repeat(10_000) + "\", \"requiredSkills\": []}");

        assertThat(read().description()).hasSize(JobPostingLlmService.MAX_DESCRIPTION_LENGTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"found\": false, \"description\": null, \"requiredSkills\": [], \"reason\": \"The posting has been removed.\"}",
            "{\"found\": true, \"description\": null}",
            "{\"found\": true, \"description\": \"   \"}",
            "{}",
            "null"})
    void reportsThePostingAsNotFound_whenTheModelFoundNoDescription(final String reply) {
        modelReplies(reply);

        assertThatThrownBy(this::read)
                .isInstanceOf(JobDescriptionUnavailableException.class)
                .hasMessageContaining("couldn't find this job posting");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "I couldn't open that page.", "{\"found\": tr", "[]"})
    void asksTheUserToTryAgain_whenTheReplyIsNotTheExpectedJson(final String reply) {
        modelReplies(reply);

        assertThatThrownBy(this::read)
                .isInstanceOf(JobDescriptionUnavailableException.class)
                .hasMessageContaining("please try again");
    }

    @Test
    void asksTheUserToTryAgain_whenTheModelCallFails() {
        final IllegalStateException rateLimited = new IllegalStateException("429 rate limit");
        when(request.call()).thenThrow(rateLimited);

        assertThatThrownBy(this::read)
                .isInstanceOf(JobDescriptionUnavailableException.class)
                .hasMessageContaining("please try again")
                .hasCause(rateLimited);
    }

    private JobPosting read() {
        return service.readPosting("user-1", "Acme Corp", "Staff Engineer", URL);
    }

    private void modelReplies(final String content) {
        when(response.content()).thenReturn(content);
    }
}
