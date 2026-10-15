package com.systa.service;

import com.systa.exception.JobDescriptionUnavailableException;
import com.systa.exception.ResumeRequiredException;
import com.systa.exception.ResumeReviewException;
import com.systa.model.JobDescription;
import com.systa.model.JobListing;
import com.systa.model.ResumeReview;
import com.systa.model.ResumeReview.RedFlag;
import com.systa.model.ResumeReview.Status;
import com.systa.model.ResumeReviewResult;
import com.systa.model.UserJobSearchResult;
import com.systa.model.UserResume;
import com.systa.repository.ResumeReviewRepository;
import com.systa.repository.UserJobSearchResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
class ResumeReviewServiceTest {

    private static final String USER_ID = "user-1";
    private static final String JOB_RESULT_ID = "r-1";
    private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");

    private static final UserJobSearchResult JOB_RESULT = new UserJobSearchResult(JOB_RESULT_ID, USER_ID,
            LocalDateTime.of(2026, 10, 14, 9, 0), "Acme Corp", new JobListing("J-1", "Staff Engineer",
            "https://acme.example/jobs/1", "London, UK", "10 Oct 2026", "Not specified", "Not specified", "LinkedIn", 88));
    private static final JobDescription JOB_DESCRIPTION = new JobDescription(JOB_RESULT_ID, USER_ID,
            "https://acme.example/jobs/1", "Builds payment APIs.", List.of("Java"), NOW);
    private static final ResumeReviewResult RESULT = new ResumeReviewResult(64, List.of("Kubernetes", "Terraform"),
            List.of(new RedFlag("No measurable results", "Bullets list duties, not outcomes.")));

    @Mock
    private UserJobSearchResultRepository userJobSearchResultRepository;

    @Mock
    private ResumeReviewRepository resumeReviewRepository;

    @Mock
    private ResumeService resumeService;

    @Mock
    private JobDescriptionService jobDescriptionService;

    @Mock
    private ResumeReviewLlmService resumeReviewLlmService;

    // Background work the service handed over and that hasn't been run yet.
    private final List<Runnable> queued = new ArrayList<>();

    // The job_resume_review collection, as far as the service can tell.
    private ResumeReview stored;

    private ResumeReviewService service;

    @BeforeEach
    void setUp() {
        service = serviceWithClock(Clock.fixed(NOW, ZoneOffset.UTC));

        lenient().when(userJobSearchResultRepository.findByIdAndUserId(JOB_RESULT_ID, USER_ID))
                .thenReturn(Optional.of(JOB_RESULT));
        lenient().when(resumeService.findLatest(USER_ID)).thenReturn(Optional.of(resume(3)));
        lenient().when(resumeReviewRepository.findById(JOB_RESULT_ID)).thenAnswer(call -> Optional.ofNullable(stored));
        lenient().when(resumeReviewRepository.save(any())).thenAnswer(call -> stored = call.getArgument(0));
        lenient().when(resumeReviewRepository.insert(any(ResumeReview.class)))
                .thenAnswer(call -> stored = call.getArgument(0));

        lenient().when(jobDescriptionService.getOrFetch(USER_ID, JOB_RESULT_ID)).thenReturn(Optional.of(JOB_DESCRIPTION));
        lenient().when(resumeReviewLlmService.review(USER_ID, "resume text v3", "Acme Corp", "Staff Engineer",
                JOB_DESCRIPTION)).thenReturn(RESULT);
    }

    @Test
    void answersStraightAwayAsPending_andReviewsInTheBackground() {
        final Optional<ResumeReview> requested = service.request(USER_ID, JOB_RESULT_ID);

        final ResumeReview pending = pendingSince(NOW, 3);
        assertThat(requested).contains(pending);
        assertThat(stored).isEqualTo(pending);
        // Nothing slow has happened yet: it is all waiting in the background queue.
        assertThat(queued).hasSize(1);
        verifyNoInteractions(jobDescriptionService, resumeReviewLlmService);
    }

    @Test
    void reviewsTheLatestOriginalResumeAgainstTheJob_andRecordsWhatItFound() {
        service.request(USER_ID, JOB_RESULT_ID);
        runBackgroundWork();

        assertThat(stored).isEqualTo(new ResumeReview(JOB_RESULT_ID, USER_ID, Status.READY, 3, 64,
                List.of("Kubernetes", "Terraform"),
                List.of(new RedFlag("No measurable results", "Bullets list duties, not outcomes.")), null, NOW, NOW));
    }

    @Test
    void recordsAResumeWithNothingMissingAndNoRedFlags() {
        when(resumeReviewLlmService.review(any(), any(), any(), any(), any()))
                .thenReturn(new ResumeReviewResult(96, List.of(), List.of()));

        service.request(USER_ID, JOB_RESULT_ID);
        runBackgroundWork();

        assertThat(stored.status()).isEqualTo(Status.READY);
        assertThat(stored.missingKeywords()).isEmpty();
        assertThat(stored.redFlags()).isEmpty();
    }

    @Test
    void returnsTheReadyReview_withoutReviewingAgain() {
        stored = ready(3);

        assertThat(service.request(USER_ID, JOB_RESULT_ID)).contains(ready(3));

        assertThat(queued).isEmpty();
        verify(resumeReviewRepository, never()).save(any());
    }

    @Test
    void returnsTheOneAlreadyUnderWay_withoutStartingAnother() {
        stored = pendingSince(NOW.minusSeconds(60), 3);

        assertThat(service.request(USER_ID, JOB_RESULT_ID)).contains(stored);

        assertThat(queued).isEmpty();
    }

    @Test
    void startsAgain_whenTheOneUnderWayHasBeenPendingTooLong() {
        stored = pendingSince(NOW.minus(ResumeReviewService.PENDING_TIMEOUT).minusSeconds(1), 3);

        assertThat(service.request(USER_ID, JOB_RESULT_ID)).map(ResumeReview::requestedAt).contains(NOW);
        assertThat(queued).hasSize(1);
    }

    @Test
    void reviewsAgain_whenTheUserHasUploadedANewerResume() {
        stored = ready(2);

        final Optional<ResumeReview> requested = service.request(USER_ID, JOB_RESULT_ID);
        assertThat(requested).map(ResumeReview::status).contains(Status.PENDING);
        assertThat(requested).map(ResumeReview::resumeVersion).contains(3);

        runBackgroundWork();
        assertThat(stored.status()).isEqualTo(Status.READY);
        assertThat(stored.resumeVersion()).isEqualTo(3);
    }

    @Test
    void reviewsAgain_afterAFailure() {
        stored = pendingSince(NOW.minusSeconds(60), 3).failed("we couldn't find this job posting", NOW.minusSeconds(30));

        assertThat(service.request(USER_ID, JOB_RESULT_ID)).map(ResumeReview::status).contains(Status.PENDING);
        assertThat(queued).hasSize(1);
    }

    @Test
    void refusesAUserWhoHasNotUploadedAResume() {
        when(resumeService.findLatest(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(USER_ID, JOB_RESULT_ID))
                .isInstanceOf(ResumeRequiredException.class)
                .hasMessage("upload your resume before it can be reviewed against a job");

        assertThat(queued).isEmpty();
        verifyNoInteractions(resumeReviewRepository);
    }

    @Test
    void doesNothing_forAJobTheUserDoesNotHave() {
        when(userJobSearchResultRepository.findByIdAndUserId("someone-elses", USER_ID)).thenReturn(Optional.empty());

        assertThat(service.request(USER_ID, "someone-elses")).isEmpty();

        assertThat(queued).isEmpty();
        verifyNoInteractions(resumeReviewRepository, resumeService);
    }

    @Test
    void leavesTheWorkToTheOtherRequest_whenTwoArriveAtOnce() {
        final ResumeReview theirs = pendingSince(NOW.minusMillis(5), 3);
        when(resumeReviewRepository.insert(any(ResumeReview.class))).thenAnswer(call -> {
            stored = theirs;
            throw new DuplicateKeyException("job_resume_review _id");
        });

        assertThat(service.request(USER_ID, JOB_RESULT_ID)).contains(theirs);

        assertThat(queued).isEmpty();
    }

    @Test
    void recordsWhyItFailed_whenTheJobPostingCannotBeRead() {
        when(jobDescriptionService.getOrFetch(USER_ID, JOB_RESULT_ID)).thenThrow(new JobDescriptionUnavailableException(
                "we couldn't find this job posting - it may have been removed or need a sign-in to view"));

        service.request(USER_ID, JOB_RESULT_ID);
        runBackgroundWork();

        assertThat(stored).isEqualTo(pendingSince(NOW, 3).failed(
                "we couldn't find this job posting - it may have been removed or need a sign-in to view", NOW));
        verifyNoInteractions(resumeReviewLlmService);
    }

    @Test
    void recordsWhyItFailed_whenTheModelCannotReviewIt() {
        when(resumeReviewLlmService.review(any(), any(), any(), any(), any()))
                .thenThrow(new ResumeReviewException("we couldn't review your resume right now, please try again in a minute"));

        service.request(USER_ID, JOB_RESULT_ID);
        runBackgroundWork();

        assertThat(stored.status()).isEqualTo(Status.FAILED);
        assertThat(stored.failureReason())
                .isEqualTo("we couldn't review your resume right now, please try again in a minute");
    }

    @Test
    void recordsAGeneralFailure_withoutLeakingDetails_whenSomethingUnexpectedBreaks() {
        when(resumeReviewLlmService.review(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("connection pool exhausted at 10.0.0.7"));

        service.request(USER_ID, JOB_RESULT_ID);
        runBackgroundWork();

        assertThat(stored.status()).isEqualTo(Status.FAILED);
        assertThat(stored.failureReason()).isEqualTo("something went wrong while reviewing your resume, please try again");
    }

    @Test
    void discardsAResult_whenTheUserHasAskedAgainSinceItStarted() {
        service.request(USER_ID, JOB_RESULT_ID);
        // While that one runs, a newer request takes over the record.
        final ResumeReview newer = pendingSince(NOW.plusSeconds(700), 4);
        stored = newer;

        runBackgroundWork();

        assertThat(stored).isEqualTo(newer);
    }

    @Test
    void recordsTheResult_whenTheClockIsFinerThanMongoKeepsDates() {
        // The system clock ticks in microseconds or finer; MongoDB keeps only milliseconds.
        service = serviceWithClock(Clock.fixed(NOW.plusNanos(123_456_789), ZoneOffset.UTC));
        when(resumeReviewRepository.insert(any(ResumeReview.class)))
                .thenAnswer(call -> stored = asMongoKeepsIt(call.getArgument(0)));
        when(resumeReviewRepository.save(any())).thenAnswer(call -> stored = asMongoKeepsIt(call.getArgument(0)));

        service.request(USER_ID, JOB_RESULT_ID);
        runBackgroundWork();

        assertThat(stored.status()).isEqualTo(Status.READY);
    }

    @Test
    void reviewsANewJobForTheDailyJob_waitingForTheResult() {
        final Optional<ResumeReview> review = service.reviewNewJob(USER_ID, JOB_RESULT_ID, resume(3));

        final ResumeReview expected = pendingSince(NOW, 3).ready(RESULT, NOW);
        assertThat(review).contains(expected);
        assertThat(stored).isEqualTo(expected);
        // Done there and then, not handed to the background.
        assertThat(queued).isEmpty();
    }

    @Test
    void recordsAndReturnsTheFailure_whenANewJobCannotBeReviewed() {
        when(jobDescriptionService.getOrFetch(USER_ID, JOB_RESULT_ID)).thenReturn(Optional.empty());

        final Optional<ResumeReview> review = service.reviewNewJob(USER_ID, JOB_RESULT_ID, resume(3));

        assertThat(review).map(ResumeReview::status).contains(Status.FAILED);
        assertThat(stored.status()).isEqualTo(Status.FAILED);
    }

    @Test
    void leavesANewJobAlone_whenItAlreadyHasAReview() {
        when(resumeReviewRepository.insert(any(ResumeReview.class)))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key"));

        assertThat(service.reviewNewJob(USER_ID, JOB_RESULT_ID, resume(3))).isEmpty();
        verifyNoInteractions(jobDescriptionService, resumeReviewLlmService);
    }

    @Test
    void reviewsNothing_whenTheNewJobIsNotTheUsers() {
        when(userJobSearchResultRepository.findByIdAndUserId(JOB_RESULT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThat(service.reviewNewJob(USER_ID, JOB_RESULT_ID, resume(3))).isEmpty();
        verify(resumeReviewRepository, never()).insert(any(ResumeReview.class));
    }

    @Test
    void listsEveryReviewTheUserHas() {
        when(resumeReviewRepository.findByUserId(USER_ID)).thenReturn(List.of(ready(3)));

        assertThat(service.findAll(USER_ID)).containsExactly(ready(3));
    }

    private ResumeReviewService serviceWithClock(final Clock clock) {
        final TaskExecutor queueing = queued::add;
        return new ResumeReviewService(userJobSearchResultRepository, resumeReviewRepository, resumeService,
                jobDescriptionService, resumeReviewLlmService, queueing, clock);
    }

    private void runBackgroundWork() {
        final List<Runnable> work = List.copyOf(queued);
        queued.clear();
        work.forEach(Runnable::run);
    }

    private static UserResume resume(final int version) {
        return new UserResume("resume-" + version, USER_ID, version, true, "resumes/user-1/v" + version + "/id.pdf",
                "cv.pdf", "application/pdf", 2048, NOW, "resume text v" + version);
    }

    private static ResumeReview pendingSince(final Instant requestedAt, final int resumeVersion) {
        return new ResumeReview(JOB_RESULT_ID, USER_ID, Status.PENDING, resumeVersion, null, null, null, null,
                requestedAt, null);
    }

    private static ResumeReview ready(final int resumeVersion) {
        return pendingSince(NOW.minusSeconds(120), resumeVersion).ready(RESULT, NOW.minusSeconds(60));
    }

    // What a save and a read back hand you: MongoDB drops anything finer than a millisecond.
    private static ResumeReview asMongoKeepsIt(final ResumeReview saved) {
        return new ResumeReview(saved.jobResultId(), saved.userId(), saved.status(), saved.resumeVersion(),
                saved.resumeAtsScore(), saved.missingKeywords(), saved.redFlags(), saved.failureReason(),
                saved.requestedAt().truncatedTo(ChronoUnit.MILLIS),
                saved.reviewedAt() == null ? null : saved.reviewedAt().truncatedTo(ChronoUnit.MILLIS));
    }
}
