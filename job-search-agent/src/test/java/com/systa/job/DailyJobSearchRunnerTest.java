package com.systa.job;

import com.systa.config.DailyJobSearchProperties;
import com.systa.model.CandidateProfile;
import com.systa.model.JobSearchOutcome;
import com.systa.model.UserJobSearchRun;
import com.systa.model.UserJobSearchRun.Status;
import com.systa.repository.CandidateProfileRepository;
import com.systa.repository.UserJobSearchRunRepository;
import com.systa.service.JobSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyJobSearchRunnerTest {

    private static final Instant NOW = Instant.parse("2026-10-08T02:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    private static final JobSearchOutcome ALL_SEARCHED = new JobSearchOutcome(3, 0, List.of());

    @Mock
    private CandidateProfileRepository candidateProfileRepository;

    @Mock
    private UserJobSearchRunRepository userJobSearchRunRepository;

    @Mock
    private JobSearchService jobSearchService;

    @Mock
    private DailyResumeReviewer dailyResumeReviewer;

    private DailyJobSearchRunner runner;

    @BeforeEach
    void setUp() {
        runner = runner(false);
        // By default nobody has been searched before and every search goes through.
        lenient().when(userJobSearchRunRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(jobSearchService.searchJobs(any())).thenReturn(ALL_SEARCHED);
    }

    @Test
    void searchesForEveryUser_onePageOfProfilesAtATime() {
        final CandidateProfile ada = profile("ada");
        final CandidateProfile bob = profile("bob");
        final CandidateProfile cy = profile("cy");
        // Page size is 2 (see runner()), so three users span two pages.
        when(candidateProfileRepository.findAll(any(Pageable.class))).thenAnswer(call -> {
            final Pageable pageable = call.getArgument(0);
            assertThat(pageable.getPageSize()).isEqualTo(2);
            assertThat(pageable.getSort().getOrderFor("id")).isNotNull();
            final List<CandidateProfile> content = pageable.getPageNumber() == 0 ? List.of(ada, bob) : List.of(cy);
            return new PageImpl<>(content, pageable, 3);
        });

        runner.run(null);

        final InOrder inOrder = inOrder(jobSearchService);
        inOrder.verify(jobSearchService).searchJobs(ada);
        inOrder.verify(jobSearchService).searchJobs(bob);
        inOrder.verify(jobSearchService).searchJobs(cy);
        assertThat(runner.getExitCode()).isZero();
    }

    @Test
    void recordsEachUsersRun() {
        usersWithProfiles(profile("ada"));
        when(jobSearchService.searchJobs(any())).thenReturn(new JobSearchOutcome(6, 0, List.of()));

        runner.run(null);

        assertThat(savedRuns()).containsExactly(
                new UserJobSearchRun("ada", "2026-10-08", NOW, NOW, Status.SUCCEEDED, 6, 0));
    }

    @Test
    void recordsTheStatusThatMatchesHowTheSearchWent() {
        final CandidateProfile clean = profile("clean");
        final CandidateProfile patchy = profile("patchy");
        final CandidateProfile rateLimited = profile("rate-limited");
        final CandidateProfile noCompanies = profile("no-companies");
        usersWithProfiles(clean, patchy, rateLimited, noCompanies);
        when(jobSearchService.searchJobs(clean)).thenReturn(new JobSearchOutcome(3, 0, List.of()));
        when(jobSearchService.searchJobs(patchy)).thenReturn(new JobSearchOutcome(3, 1, List.of()));
        when(jobSearchService.searchJobs(rateLimited)).thenReturn(new JobSearchOutcome(3, 3, List.of()));
        when(jobSearchService.searchJobs(noCompanies)).thenReturn(new JobSearchOutcome(0, 0, List.of()));

        runner.run(null);

        assertThat(savedRuns()).extracting(UserJobSearchRun::status)
                .containsExactly(Status.SUCCEEDED, Status.PARTIAL, Status.FAILED, Status.SKIPPED);
    }

    @Test
    void carriesOnWithTheNextUser_whenOneUsersSearchThrows() {
        final CandidateProfile ada = profile("ada");
        final CandidateProfile bob = profile("bob");
        usersWithProfiles(ada, bob);
        when(jobSearchService.searchJobs(ada)).thenThrow(new IllegalStateException("boom"));

        runner.run(null);

        verify(jobSearchService).searchJobs(bob);
        assertThat(savedRuns()).extracting(UserJobSearchRun::userId, UserJobSearchRun::status)
                .containsExactly(tuple("ada", Status.FAILED),
                        tuple("bob", Status.SUCCEEDED));
    }

    @Test
    void carriesOnWithTheNextUser_whenTheRunCannotBeRecorded() {
        final CandidateProfile ada = profile("ada");
        final CandidateProfile bob = profile("bob");
        usersWithProfiles(ada, bob);
        when(userJobSearchRunRepository.save(any())).thenThrow(new IllegalStateException("Mongo unavailable"));

        runner.run(null);

        verify(jobSearchService).searchJobs(bob);
        assertThat(runner.getExitCode()).isZero();
    }

    @Test
    void exitsNonZero_whenAUsersSearchFailed() {
        final CandidateProfile ada = profile("ada");
        usersWithProfiles(ada, profile("bob"));
        when(jobSearchService.searchJobs(ada)).thenReturn(new JobSearchOutcome(3, 3, List.of()));

        runner.run(null);

        assertThat(runner.getExitCode()).isEqualTo(1);
    }

    @Test
    void exitsZero_whenSomeBatchesFailedButEveryUserGotResults() {
        usersWithProfiles(profile("ada"));
        when(jobSearchService.searchJobs(any())).thenReturn(new JobSearchOutcome(3, 1, List.of()));

        runner.run(null);

        assertThat(runner.getExitCode()).isZero();
    }

    @Test
    void exitsNonZero_withoutThrowing_whenTheProfilesCannotBeLoaded() {
        when(candidateProfileRepository.findAll(any(Pageable.class)))
                .thenThrow(new IllegalStateException("Mongo unavailable"));

        runner.run(null);

        assertThat(runner.getExitCode()).isEqualTo(1);
    }

    @Test
    void onARerun_skipsUsersAlreadySearchedToday_butRetriesTheOnesThatFailed() {
        final CandidateProfile done = profile("done");
        final CandidateProfile partlyDone = profile("partly-done");
        final CandidateProfile failed = profile("failed");
        final CandidateProfile notReached = profile("not-reached");
        usersWithProfiles(done, partlyDone, failed, notReached);
        lastRunWas("done", TODAY, Status.SUCCEEDED);
        lastRunWas("partly-done", TODAY, Status.PARTIAL);
        lastRunWas("failed", TODAY, Status.FAILED);

        runner.run(null);

        verify(jobSearchService, never()).searchJobs(done);
        verify(jobSearchService, never()).searchJobs(partlyDone);
        verify(jobSearchService).searchJobs(failed);
        verify(jobSearchService).searchJobs(notReached);
        assertThat(savedRuns()).extracting(UserJobSearchRun::userId).containsExactly("failed", "not-reached");
    }

    @Test
    void searchesAgain_forUsersLastSearchedOnAnEarlierDay() {
        final CandidateProfile ada = profile("ada");
        usersWithProfiles(ada);
        lastRunWas("ada", TODAY.minusDays(1), Status.SUCCEEDED);

        runner.run(null);

        verify(jobSearchService).searchJobs(ada);
    }

    @Test
    void whenForced_searchesAgainForUsersAlreadySearchedToday() {
        runner = runner(true);
        final CandidateProfile ada = profile("ada");
        usersWithProfiles(ada);
        lenient().when(userJobSearchRunRepository.findById("ada"))
                .thenReturn(Optional.of(run("ada", TODAY, Status.SUCCEEDED)));

        runner.run(null);

        verify(jobSearchService).searchJobs(ada);
    }

    @Test
    void reviewsTheJobsEachUsersSearchSaved_afterRecordingTheirRun() {
        final CandidateProfile ada = profile("ada");
        final CandidateProfile bob = profile("bob");
        usersWithProfiles(ada, bob);
        when(jobSearchService.searchJobs(ada)).thenReturn(new JobSearchOutcome(3, 0, List.of("job-1", "job-2")));
        when(jobSearchService.searchJobs(bob)).thenReturn(new JobSearchOutcome(3, 0, List.of()));

        runner.run(null);

        final InOrder inOrder = inOrder(jobSearchService, userJobSearchRunRepository, dailyResumeReviewer);
        inOrder.verify(jobSearchService).searchJobs(ada);
        inOrder.verify(userJobSearchRunRepository).save(any());
        inOrder.verify(dailyResumeReviewer).reviewNewJobs("ada", List.of("job-1", "job-2"));
        inOrder.verify(jobSearchService).searchJobs(bob);
        inOrder.verify(dailyResumeReviewer).reviewNewJobs("bob", List.of());
    }

    @Test
    void aReviewFailure_leavesTheSearchOutcomeAndTheNextUserAlone() {
        final CandidateProfile ada = profile("ada");
        final CandidateProfile bob = profile("bob");
        usersWithProfiles(ada, bob);
        when(jobSearchService.searchJobs(ada)).thenReturn(new JobSearchOutcome(3, 0, List.of("job-1")));
        doThrow(new IllegalStateException("Mongo unavailable"))
                .when(dailyResumeReviewer).reviewNewJobs(eq("ada"), any());

        runner.run(null);

        verify(jobSearchService).searchJobs(bob);
        assertThat(savedRuns()).extracting(UserJobSearchRun::userId, UserJobSearchRun::status)
                .containsExactly(tuple("ada", Status.SUCCEEDED), tuple("bob", Status.SUCCEEDED));
        assertThat(runner.getExitCode()).isZero();
    }

    @Test
    void reviewsNothing_whenTheSearchThrew() {
        final CandidateProfile ada = profile("ada");
        usersWithProfiles(ada);
        when(jobSearchService.searchJobs(ada)).thenThrow(new IllegalStateException("boom"));

        runner.run(null);

        verify(dailyResumeReviewer).reviewNewJobs("ada", List.of());
    }

    private DailyJobSearchRunner runner(final boolean force) {
        return new DailyJobSearchRunner(candidateProfileRepository, userJobSearchRunRepository, jobSearchService,
                dailyResumeReviewer, new DailyJobSearchProperties(2, Duration.ZERO, force, 20, Duration.ZERO),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void usersWithProfiles(final CandidateProfile... profiles) {
        when(candidateProfileRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Arrays.asList(profiles)));
    }

    private void lastRunWas(final String userId, final LocalDate runDate, final Status status) {
        when(userJobSearchRunRepository.findById(userId)).thenReturn(Optional.of(run(userId, runDate, status)));
    }

    private List<UserJobSearchRun> savedRuns() {
        final ArgumentCaptor<UserJobSearchRun> runs = ArgumentCaptor.forClass(UserJobSearchRun.class);
        verify(userJobSearchRunRepository, atLeast(0)).save(runs.capture());
        return runs.getAllValues();
    }

    private static UserJobSearchRun run(final String userId, final LocalDate runDate, final Status status) {
        return new UserJobSearchRun(userId, runDate.toString(), NOW.minusSeconds(60), NOW, status, 3, 0);
    }

    private static CandidateProfile profile(final String userId) {
        return new CandidateProfile("profile-" + userId, userId, userId + "@example.com", "Senior Java Developer",
                List.of("Java"), "Builds APIs", List.of("Acme Corp"), 7);
    }
}
