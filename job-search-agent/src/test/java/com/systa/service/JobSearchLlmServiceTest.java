package com.systa.service;

import com.systa.config.ApplicationProperties;
import com.systa.model.CandidateProfile;
import com.systa.model.CompanySearchResult;
import com.systa.model.JobListing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobSearchLlmServiceTest {

    private static final String USER_ID = "user-1";
    private static final String COMPANY = "Acme Corp";
    private static final List<String> SOURCES = List.of("LinkedIn (linkedin.com/jobs)", "Reed (reed.co.uk)");
    private static final int MAX_ATTEMPTS = 3;

    private static final CandidateProfile PROFILE = new CandidateProfile("profile-1", USER_ID, "ada@example.com",
            "Senior Java Developer", List.of("Java", "Spring Boot"), "Builds payment APIs", List.of(COMPANY), 7);

    private static final String ONE_JOB = """
            {"companies": [{"companyName": "Acme Corp", "jobs": [{
              "jobId": "J-1", "jobTitle": "Staff Engineer", "url": "https://example.com/J-1",
              "location": "London, UK", "datePosted": "17 Aug 2026", "lastDateForSubmission": "Not specified",
              "salaryRange": "£90,000", "source": "LinkedIn", "atsScore": 88}]}]}
            """;

    // The fluent call the service makes: prompt().system(..).user(..).options(..).call().content()
    private final ChatClient chatClient = mock(ChatClient.class);
    private final ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
    private final ChatClient.CallResponseSpec response = mock(ChatClient.CallResponseSpec.class);

    private JobSearchLlmService service;

    @BeforeEach
    void setUp() {
        when(chatClient.prompt()).thenReturn(request);
        when(request.call()).thenReturn(response);

        // No wait between rate-limit retries, so the tests don't sleep.
        service = new JobSearchLlmService(chatClient, new DefaultResourceLoader(),
                new ApplicationProperties("test-model", MAX_ATTEMPTS, Duration.ZERO));
        ReflectionTestUtils.setField(service, "systemPromptPath",
                "classpath:system_prompts/job_search_system_prompt.txt");
    }

    @Test
    void returnsTheJobsTheModelFound() {
        modelReplies(ONE_JOB);

        final CompanySearchResult result = search();

        assertThat(result.companyName()).isEqualTo(COMPANY);
        assertThat(result.jobs()).containsExactly(new JobListing("J-1", "Staff Engineer", "https://example.com/J-1",
                "London, UK", "17 Aug 2026", "Not specified", "£90,000", "LinkedIn", 88));
    }

    @Test
    void filesEveryJobUnderTheCompanySearchedFor_howeverTheModelNamedIt() {
        modelReplies("""
                {"companies": [
                  {"companyName": "ACME Corporation Ltd", "jobs": [{"jobId": "J-1"}, {"jobId": "J-2"}]},
                  {"companyName": "Acme", "jobs": [{"jobId": "J-3"}]}]}
                """);

        final CompanySearchResult result = search();

        assertThat(result.companyName()).isEqualTo(COMPANY);
        assertThat(result.jobs()).extracting(JobListing::jobId).containsExactly("J-1", "J-2", "J-3");
    }

    @Test
    void returnsNoJobs_whenTheModelFoundNone() {
        modelReplies("{\"companies\": [{\"companyName\": \"Acme Corp\", \"jobs\": []}]}");
        assertThat(search().jobs()).isEmpty();

        modelReplies("{\"companies\": []}");
        assertThat(search().jobs()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "null",
            "{\"companies\": null}",
            "{\"companies\": [{\"companyName\": \"Acme Corp\"}]}",
            "{\"companies\": [{\"companyName\": \"Acme Corp\", \"jobs\": null}, null]}"})
    void returnsNoJobs_whenTheModelLeavesOutTheListsItFoundNothingFor(final String reply) {
        modelReplies(reply);

        final CompanySearchResult result = search();

        assertThat(result.companyName()).isEqualTo(COMPANY);
        assertThat(result.jobs()).isEmpty();
    }

    @Test
    void keepsTheJobsOfOtherCompanies_whenOneCompanyHasNoJobsList() {
        modelReplies("""
                {"companies": [{"companyName": "Acme Corp"}, {"companyName": "Acme", "jobs": [{"jobId": "J-1"}, null]}]}
                """);

        assertThat(search().jobs()).extracting(JobListing::jobId).containsExactly("J-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"```json\n%s\n```", "```\n%s\n```", "  %s  \n", "```json %s```"})
    void readsAReplyWrappedInMarkdownFences(final String wrapper) {
        modelReplies(wrapper.formatted(ONE_JOB));

        assertThat(search().jobs()).extracting(JobListing::jobId).containsExactly("J-1");
    }

    @Test
    void ignoresFieldsItDoesNotKnow_andLeavesMissingOnesNull() {
        modelReplies("""
                {"searchedAt": "today", "companies": [{"companyName": "Acme Corp", "careersPage": "https://acme.example",
                  "jobs": [{"jobId": "J-1", "remote": true}]}]}
                """);

        assertThat(search().jobs())
                .containsExactly(new JobListing("J-1", null, null, null, null, null, null, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"I couldn't find any roles at Acme Corp.", "", "{\"companies\": [", "[]"})
    void failsWithAParseError_whenTheReplyIsNotTheExpectedJson(final String reply) {
        modelReplies(reply);

        assertThatThrownBy(this::search)
                .isInstanceOf(JobSearchParseException.class)
                .hasMessage("Failed to parse job search response from LLM");
    }

    @Test
    void tellsTheModelWhatToSearchFor() {
        modelReplies(ONE_JOB);

        search();

        final String systemPrompt = systemPromptSent();
        assertThat(systemPrompt)
                .contains("- LinkedIn (linkedin.com/jobs)\n- Reed (reed.co.uk)")
                .contains("Desired Role: Senior Java Developer")
                .contains("Key Skills: Java, Spring Boot")
                .contains("Current Job Description: Builds payment APIs")
                .contains("TODAY'S DATE: "
                        + LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)))
                // Every placeholder in the template was filled in.
                .doesNotContain("%");
        final ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        verify(request).user(userMessage.capture());
        assertThat(userMessage.getValue()).contains("UK job openings at Acme Corp");
    }

    @Test
    void asksTheModelForTheUsersRecencyWindow_notAFixedOne() {
        modelReplies(ONE_JOB);

        service.searchJobsForCompanyBatch(USER_ID, profileWithWindow(30), COMPANY, SOURCES);

        assertThat(systemPromptSent())
                .contains("posted within the last 30 days of TODAY'S DATE")
                .contains("Discard any posting older than 30 days.")
                .doesNotContain("7 days");
    }

    @Test
    void asksTheModelForSevenDays_whenTheProfileHasNoWindow() {
        modelReplies(ONE_JOB);

        service.searchJobsForCompanyBatch(USER_ID, profileWithWindow(null), COMPANY, SOURCES);

        assertThat(systemPromptSent())
                .contains("posted within the last 7 days of TODAY'S DATE")
                .contains("Discard any posting older than 7 days.");
    }

    @Test
    void retriesAfterARateLimit_andReturnsTheLaterAnswer() {
        when(response.content())
                .thenThrow(new RuntimeException("HTTP 429 Too Many Requests"))
                .thenThrow(new RuntimeException("Rate limit reached for gpt-4.1"))
                .thenReturn(ONE_JOB);

        assertThat(search().jobs()).hasSize(1);

        verifyAttempts(3);
    }

    @Test
    void recognisesARateLimit_fromTheHttpStatusOrAnywhereInTheCauseChain() {
        final HttpClientErrorException tooManyRequests = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);
        when(response.content())
                .thenThrow(tooManyRequests)
                .thenThrow(new IllegalStateException("Tool call failed", new RuntimeException("upstream said 429")))
                .thenReturn(ONE_JOB);

        assertThat(search().jobs()).hasSize(1);

        verifyAttempts(3);
    }

    @Test
    void givesUpAfterTheConfiguredNumberOfAttempts_whenStillRateLimited() {
        final RuntimeException rateLimited = new RuntimeException("HTTP 429 Too Many Requests");
        when(response.content()).thenThrow(rateLimited);

        assertThatThrownBy(this::search).isSameAs(rateLimited);

        verifyAttempts(MAX_ATTEMPTS);
    }

    @Test
    void doesNotRetryOtherFailures() {
        final RuntimeException serverError = new RuntimeException("HTTP 500 Internal Server Error");
        when(response.content()).thenThrow(serverError);

        assertThatThrownBy(this::search).isSameAs(serverError);

        verifyAttempts(1);
    }

    @Test
    void failsClearly_whenTheSystemPromptIsMissing() {
        ReflectionTestUtils.setField(service, "systemPromptPath", "classpath:system_prompts/no_such_prompt.txt");

        assertThatThrownBy(this::search)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("classpath:system_prompts/no_such_prompt.txt");
    }

    private static CandidateProfile profileWithWindow(final Integer recencyWindowDays) {
        return new CandidateProfile(PROFILE.id(), USER_ID, PROFILE.email(), PROFILE.desiredRole(), PROFILE.skills(),
                PROFILE.currentJobDescription(), PROFILE.companyPreferences(), recencyWindowDays);
    }

    private CompanySearchResult search() {
        return service.searchJobsForCompanyBatch(USER_ID, PROFILE, COMPANY, SOURCES);
    }

    private void modelReplies(final String content) {
        when(response.content()).thenReturn(content);
    }

    private void verifyAttempts(final int attempts) {
        verify(response, times(attempts)).content();
    }

    private String systemPromptSent() {
        final ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(request).system(systemPrompt.capture());
        return systemPrompt.getValue();
    }
}
