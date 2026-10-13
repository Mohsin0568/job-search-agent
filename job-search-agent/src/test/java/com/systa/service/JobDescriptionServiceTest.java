package com.systa.service;

import com.systa.exception.JobDescriptionUnavailableException;
import com.systa.model.JobDescription;
import com.systa.model.JobListing;
import com.systa.model.JobPosting;
import com.systa.model.UserJobSearchResult;
import com.systa.repository.JobDescriptionRepository;
import com.systa.repository.UserJobSearchResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobDescriptionServiceTest {

    private static final String USER_ID = "user-1";
    private static final String JOB_RESULT_ID = "r-1";
    private static final String URL = "https://careers.acme.example/jobs/J-1";
    private static final Instant NOW = Instant.parse("2026-10-13T09:00:00Z");
    private static final JobPosting POSTING = new JobPosting("Builds payment APIs.", List.of("Java", "Spring Boot"));

    @Mock
    private UserJobSearchResultRepository userJobSearchResultRepository;

    @Mock
    private JobDescriptionRepository jobDescriptionRepository;

    @Mock
    private JobPostingLlmService jobPostingLlmService;

    private JobDescriptionService service;

    @BeforeEach
    void setUp() {
        service = new JobDescriptionService(userJobSearchResultRepository, jobDescriptionRepository,
                jobPostingLlmService, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(jobDescriptionRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(jobDescriptionRepository.findById(JOB_RESULT_ID)).thenReturn(Optional.empty());
    }

    @Test
    void readsThePostingTheFirstTime_andKeepsWhatItSaid() {
        userHasJobWithUrl(URL);
        when(jobPostingLlmService.readPosting(USER_ID, "Acme Corp", "Staff Engineer", URL)).thenReturn(POSTING);

        final Optional<JobDescription> description = service.getOrFetch(USER_ID, JOB_RESULT_ID);

        final JobDescription expected = new JobDescription(JOB_RESULT_ID, USER_ID, URL, "Builds payment APIs.",
                List.of("Java", "Spring Boot"), NOW);
        assertThat(description).contains(expected);
        verify(jobDescriptionRepository).save(expected);
    }

    @Test
    void returnsTheKeptDescription_withoutReadingThePostingAgain() {
        final JobDescription kept = new JobDescription(JOB_RESULT_ID, USER_ID, URL, "Builds payment APIs.",
                List.of("Java"), NOW.minusSeconds(3600));
        userHasJobWithUrl(URL);
        when(jobDescriptionRepository.findById(JOB_RESULT_ID)).thenReturn(Optional.of(kept));

        assertThat(service.getOrFetch(USER_ID, JOB_RESULT_ID)).contains(kept);

        verifyNoInteractions(jobPostingLlmService);
        verify(jobDescriptionRepository, never()).save(any());
    }

    @Test
    void returnsNothing_andReadsNothing_forAJobTheUserDoesNotHave() {
        when(userJobSearchResultRepository.findByIdAndUserId("someone-elses", USER_ID)).thenReturn(Optional.empty());

        assertThat(service.getOrFetch(USER_ID, "someone-elses")).isEmpty();

        verifyNoInteractions(jobPostingLlmService, jobDescriptionRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Not specified", "javascript:alert(1)", "file:///etc/passwd", "ftp://acme.example/job",
            "careers.acme.example/jobs/J-1", "https://", "http:// not a url"})
    void refusesAJobWhoseLinkIsNotAWebAddress_withoutCallingTheModel(final String url) {
        userHasJobWithUrl(url);

        assertThatThrownBy(() -> service.getOrFetch(USER_ID, JOB_RESULT_ID))
                .isInstanceOf(JobDescriptionUnavailableException.class)
                .hasMessageContaining("no link to its posting");

        verifyNoInteractions(jobPostingLlmService);
    }

    @Test
    void passesTheLinkOnTrimmed() {
        userHasJobWithUrl("  " + URL + " ");
        when(jobPostingLlmService.readPosting(USER_ID, "Acme Corp", "Staff Engineer", URL)).thenReturn(POSTING);

        assertThat(service.getOrFetch(USER_ID, JOB_RESULT_ID)).map(JobDescription::sourceUrl).contains(URL);
    }

    @Test
    void keepsNothing_whenThePostingCannotBeRead() {
        userHasJobWithUrl(URL);
        when(jobPostingLlmService.readPosting(any(), any(), any(), any()))
                .thenThrow(new JobDescriptionUnavailableException("we couldn't find this job posting"));

        assertThatThrownBy(() -> service.getOrFetch(USER_ID, JOB_RESULT_ID))
                .isInstanceOf(JobDescriptionUnavailableException.class);

        verify(jobDescriptionRepository, never()).save(any());
    }

    private void userHasJobWithUrl(final String url) {
        when(userJobSearchResultRepository.findByIdAndUserId(JOB_RESULT_ID, USER_ID)).thenReturn(Optional.of(
                new UserJobSearchResult(JOB_RESULT_ID, USER_ID, LocalDateTime.of(2026, 10, 12, 9, 0), "Acme Corp",
                        new JobListing("J-1", "Staff Engineer", url, "London, UK", "10 Oct 2026", "Not specified",
                                "Not specified", "LinkedIn", 88))));
    }
}
