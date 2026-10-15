package com.systa.service;

import com.systa.exception.JobDescriptionUnavailableException;
import com.systa.exception.ResumeRequiredException;
import com.systa.exception.ResumeReviewRequiredException;
import com.systa.exception.ResumeStorageException;
import com.systa.exception.ResumeTailoringException;
import com.systa.model.JobDescription;
import com.systa.model.JobListing;
import com.systa.model.ResumeDownload;
import com.systa.model.ResumeReview;
import com.systa.model.ResumeReview.RedFlag;
import com.systa.model.ResumeTemplate;
import com.systa.model.TailoredResume;
import com.systa.model.TailoredResume.Fix;
import com.systa.model.TailoredResume.Status;
import com.systa.model.TailoredResumeContent;
import com.systa.model.TailoringFix;
import com.systa.model.UserJobSearchResult;
import com.systa.model.UserResume;
import com.systa.repository.ResumeReviewRepository;
import com.systa.repository.TailoredResumeRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TailoredResumeServiceTest {

    private static final String USER_ID = "user-1";
    private static final String JOB_RESULT_ID = "r-1";
    private static final Instant NOW = Instant.parse("2026-10-13T09:00:00Z");
    private static final byte[] PDF = "%PDF-tailored".getBytes();
    private static final String S3_KEY = "tailored/user-1/r-1/v3-classic.pdf";

    private static final UserJobSearchResult JOB_RESULT = new UserJobSearchResult(JOB_RESULT_ID, USER_ID,
            LocalDateTime.of(2026, 10, 12, 9, 0), "Acme Corp", new JobListing("J-1", "Staff Engineer",
            "https://acme.example/jobs/1", "London, UK", "10 Oct 2026", "Not specified", "Not specified", "LinkedIn", 88));
    private static final JobDescription JOB_DESCRIPTION = new JobDescription(JOB_RESULT_ID, USER_ID,
            "https://acme.example/jobs/1", "Builds payment APIs.", List.of("Java"), NOW);
    private static final RedFlag SHORT_TENURES = new RedFlag("Short tenures", "Three jobs in two years.");
    private static final RedFlag VAGUE_RESULTS = new RedFlag("Vague results", "No numbers on any achievement.");
    // Just tailored as is, then tailored fixing Kafka and the red flags - the review's spelling, not the user's.
    private static final Fix KAFKA_AND_RED_FLAGS = new Fix(List.of("Kafka"), List.of(SHORT_TENURES, VAGUE_RESULTS));

    private static final TailoredResumeContent CONTENT = new TailoredResumeContent("Jane Doe", "Engineer", List.of(),
            "A summary.", List.of("Java"), List.of(), List.of(), List.of(), List.of());

    @Mock
    private UserJobSearchResultRepository userJobSearchResultRepository;

    @Mock
    private TailoredResumeRepository tailoredResumeRepository;

    @Mock
    private ResumeReviewRepository resumeReviewRepository;

    @Mock
    private ResumeService resumeService;

    @Mock
    private JobDescriptionService jobDescriptionService;

    @Mock
    private ResumeTailoringLlmService resumeTailoringLlmService;

    @Mock
    private TailoredResumePdfRenderer tailoredResumePdfRenderer;

    @Mock
    private ResumeStorageService resumeStorageService;

    // Background work the service handed over and that hasn't been run yet.
    private final List<Runnable> queued = new ArrayList<>();

    // The tailored_resume collection, as far as the service can tell.
    private TailoredResume stored;

    private TailoredResumeService service;

    @BeforeEach
    void setUp() {
        final TaskExecutor queueing = queued::add;
        service = new TailoredResumeService(userJobSearchResultRepository, tailoredResumeRepository,
                resumeReviewRepository, resumeService, jobDescriptionService, resumeTailoringLlmService,
                tailoredResumePdfRenderer, resumeStorageService,
                queueing, Clock.fixed(NOW, ZoneOffset.UTC));

        lenient().when(userJobSearchResultRepository.findByIdAndUserId(JOB_RESULT_ID, USER_ID))
                .thenReturn(Optional.of(JOB_RESULT));
        lenient().when(resumeService.findLatest(USER_ID)).thenReturn(Optional.of(resume(3)));
        lenient().when(tailoredResumeRepository.findById(JOB_RESULT_ID)).thenAnswer(call -> Optional.ofNullable(stored));
        lenient().when(tailoredResumeRepository.save(any())).thenAnswer(call -> stored = call.getArgument(0));
        lenient().when(tailoredResumeRepository.insert(any(TailoredResume.class)))
                .thenAnswer(call -> stored = call.getArgument(0));

        lenient().when(jobDescriptionService.getOrFetch(USER_ID, JOB_RESULT_ID)).thenReturn(Optional.of(JOB_DESCRIPTION));
        lenient().when(resumeTailoringLlmService.tailor(USER_ID, "resume text v3", "Acme Corp", "Staff Engineer",
                JOB_DESCRIPTION, null)).thenReturn(CONTENT);
        lenient().when(tailoredResumePdfRenderer.render(eq(CONTENT), any())).thenReturn(PDF);
    }

    @Test
    void answersStraightAwayAsPending_andTailorsInTheBackground() {
        final Optional<TailoredResume> requested = service.request(USER_ID, JOB_RESULT_ID, null, null);

        final TailoredResume pending = new TailoredResume(JOB_RESULT_ID, USER_ID, Status.PENDING, 3,
                ResumeTemplate.CLASSIC, null, null, null, NOW, null, null, null);
        assertThat(requested).contains(pending);
        assertThat(stored).isEqualTo(pending);
        // Nothing slow has happened yet: it is all waiting in the background queue.
        assertThat(queued).hasSize(1);
        verifyNoInteractions(jobDescriptionService, resumeTailoringLlmService, resumeStorageService);
    }

    @Test
    void tailorsTheLatestResumeToTheJob_storesThePdf_andMarksItReady() {
        service.request(USER_ID, JOB_RESULT_ID, null, null);
        runBackgroundWork();

        verify(resumeStorageService).store(S3_KEY, PDF, "application/pdf");
        verify(tailoredResumePdfRenderer).render(CONTENT, ResumeTemplate.CLASSIC);
        // The content is kept with it, so it can be laid out in another template later.
        assertThat(stored).isEqualTo(new TailoredResume(JOB_RESULT_ID, USER_ID, Status.READY, 3,
                ResumeTemplate.CLASSIC, S3_KEY, "Jane-Doe-Acme-Corp-resume.pdf", null, NOW, NOW, CONTENT, null));
    }

    @Test
    void marksItReady_whenTheClockIsFinerThanMongoKeepsDates() {
        // The system clock ticks in microseconds or finer; MongoDB keeps only milliseconds.
        service = new TailoredResumeService(userJobSearchResultRepository, tailoredResumeRepository,
                resumeReviewRepository, resumeService, jobDescriptionService, resumeTailoringLlmService,
                tailoredResumePdfRenderer, resumeStorageService,
                queued::add, Clock.fixed(NOW.plusNanos(123_456_789), ZoneOffset.UTC));
        when(tailoredResumeRepository.insert(any(TailoredResume.class)))
                .thenAnswer(call -> stored = asMongoKeepsIt(call.getArgument(0)));
        when(tailoredResumeRepository.save(any())).thenAnswer(call -> stored = asMongoKeepsIt(call.getArgument(0)));

        service.request(USER_ID, JOB_RESULT_ID, null, null);
        runBackgroundWork();

        assertThat(stored.status()).isEqualTo(Status.READY);
        assertThat(stored.requestedAt()).isEqualTo(NOW.plusMillis(123));
    }

    @Test
    void returnsTheReadyResume_withoutTailoringAgain() {
        stored = ready(3);

        assertThat(service.request(USER_ID, JOB_RESULT_ID, null, null)).contains(ready(3));

        assertThat(queued).isEmpty();
        verify(tailoredResumeRepository, never()).save(any());
    }

    @Test
    void returnsTheOneAlreadyUnderWay_withoutStartingAnother() {
        stored = pendingSince(NOW.minusSeconds(60), 3);

        assertThat(service.request(USER_ID, JOB_RESULT_ID, null, null)).contains(stored);

        assertThat(queued).isEmpty();
    }

    @Test
    void startsAgain_whenTheOneUnderWayHasBeenPendingTooLong() {
        stored = pendingSince(NOW.minus(TailoredResumeService.PENDING_TIMEOUT).minusSeconds(1), 3);

        final Optional<TailoredResume> requested = service.request(USER_ID, JOB_RESULT_ID, null, null);

        assertThat(requested).map(TailoredResume::requestedAt).contains(NOW);
        assertThat(queued).hasSize(1);
    }

    @Test
    void tailorsAgain_whenTheUserHasUploadedANewerResume() {
        stored = ready(2);
        when(resumeTailoringLlmService.tailor(any(), any(), any(), any(), any(), any())).thenReturn(CONTENT);

        final Optional<TailoredResume> requested = service.request(USER_ID, JOB_RESULT_ID, null, null);
        assertThat(requested).map(TailoredResume::status).contains(Status.PENDING);
        assertThat(requested).map(TailoredResume::resumeVersion).contains(3);

        runBackgroundWork();
        assertThat(stored.status()).isEqualTo(Status.READY);
        assertThat(stored.s3Key()).isEqualTo(S3_KEY);
    }

    @Test
    void tailorsAgain_afterAFailure() {
        stored = pendingSince(NOW.minusSeconds(60), 3).failed("we couldn't find this job posting", NOW.minusSeconds(30));

        assertThat(service.request(USER_ID, JOB_RESULT_ID, null, null)).map(TailoredResume::status)
                .contains(Status.PENDING);
        assertThat(queued).hasSize(1);
    }

    @Test
    void tailorsInTheTemplateAskedFor() {
        final Optional<TailoredResume> requested =
                service.request(USER_ID, JOB_RESULT_ID, ResumeTemplate.MODERN, null);
        assertThat(requested).map(TailoredResume::template).contains(ResumeTemplate.MODERN);

        runBackgroundWork();

        verify(tailoredResumePdfRenderer).render(CONTENT, ResumeTemplate.MODERN);
        verify(resumeStorageService).store("tailored/user-1/r-1/v3-modern.pdf", PDF, "application/pdf");
        assertThat(stored.status()).isEqualTo(Status.READY);
        assertThat(stored.template()).isEqualTo(ResumeTemplate.MODERN);
    }

    @Test
    void laysAReadyResumeOutInAnotherTemplate_straightAway_withoutTailoringItAgain() {
        stored = ready(3);

        final Optional<TailoredResume> requested =
                service.request(USER_ID, JOB_RESULT_ID, ResumeTemplate.COMPACT, null);

        final TailoredResume relaidOut = ready(3).relaidOut(ResumeTemplate.COMPACT,
                "tailored/user-1/r-1/v3-compact.pdf", NOW);
        assertThat(requested).contains(relaidOut);
        assertThat(stored).isEqualTo(relaidOut);
        verify(tailoredResumePdfRenderer).render(CONTENT, ResumeTemplate.COMPACT);
        verify(resumeStorageService).store("tailored/user-1/r-1/v3-compact.pdf", PDF, "application/pdf");
        assertThat(queued).isEmpty();
        verifyNoInteractions(jobDescriptionService, resumeTailoringLlmService);
    }

    @Test
    void keepsTheJobsTemplate_whenNoneIsAskedFor() {
        stored = ready(3).relaidOut(ResumeTemplate.MODERN, "tailored/user-1/r-1/v3-modern.pdf", NOW);
        assertThat(service.request(USER_ID, JOB_RESULT_ID, null, null)).contains(stored);
        verifyNoInteractions(tailoredResumePdfRenderer);

        // Trying again after a failure, too.
        stored = stored.failed("we couldn't find this job posting", NOW);
        assertThat(service.request(USER_ID, JOB_RESULT_ID, null, null)).map(TailoredResume::template)
                .contains(ResumeTemplate.MODERN);
    }

    @Test
    void tailorsAgain_toChangeTheTemplateOfOneTailoredBeforeItsContentWasKept() {
        stored = new TailoredResume(JOB_RESULT_ID, USER_ID, Status.READY, 3, null, "tailored/user-1/r-1/v3.pdf",
                "Jane-Doe-Acme-Corp-resume.pdf", null, NOW.minusSeconds(120), NOW.minusSeconds(60), null, null);

        // Asking for the template it already shows as changes nothing.
        assertThat(service.request(USER_ID, JOB_RESULT_ID, ResumeTemplate.CLASSIC, null)).contains(stored);
        assertThat(queued).isEmpty();

        final Optional<TailoredResume> requested =
                service.request(USER_ID, JOB_RESULT_ID, ResumeTemplate.MODERN, null);

        assertThat(requested).map(TailoredResume::status).contains(Status.PENDING);
        assertThat(requested).map(TailoredResume::template).contains(ResumeTemplate.MODERN);
        assertThat(queued).hasSize(1);
    }

    @Test
    void leavesOneUnderWay_inTheTemplateItWasAskedFor() {
        stored = pendingSince(NOW.minusSeconds(60), 3);

        assertThat(service.request(USER_ID, JOB_RESULT_ID, ResumeTemplate.MODERN, null)).contains(stored);

        assertThat(queued).isEmpty();
        verifyNoInteractions(tailoredResumePdfRenderer);
    }

    @Test
    void fixesOnlyTheTickedKeywordsTheReviewListed_inItsSpelling_andItsRedFlags() {
        reviewed(3);
        final TailoredResumeContent fixed = new TailoredResumeContent("Jane Doe", "Engineer", List.of(), "Fixed.",
                List.of("Java", "Kafka"), List.of(), List.of(), List.of(), List.of());
        when(resumeTailoringLlmService.tailor(USER_ID, "resume text v3", "Acme Corp", "Staff Engineer",
                JOB_DESCRIPTION, KAFKA_AND_RED_FLAGS)).thenReturn(fixed);
        when(tailoredResumePdfRenderer.render(fixed, ResumeTemplate.CLASSIC)).thenReturn(PDF);

        // Rust isn't among the review's missing keywords, so it can't be added this way.
        final Optional<TailoredResume> requested = service.request(USER_ID, JOB_RESULT_ID, null,
                new TailoringFix(List.of(" kafka ", "Rust"), true));
        assertThat(requested).map(TailoredResume::fix).contains(KAFKA_AND_RED_FLAGS);

        runBackgroundWork();

        assertThat(stored.status()).isEqualTo(Status.READY);
        assertThat(stored.content()).isEqualTo(fixed);
        assertThat(stored.fix()).isEqualTo(KAFKA_AND_RED_FLAGS);
    }

    @Test
    void fixesTheKeywordsAlone_whenNotAskedToFixTheRedFlags() {
        reviewed(3);

        assertThat(service.request(USER_ID, JOB_RESULT_ID, null, new TailoringFix(List.of("Docker"), false)))
                .map(TailoredResume::fix).contains(new Fix(List.of("Docker"), List.of()));
    }

    @Test
    void tailorsAsIs_whenTheFixLeavesNothingToFix() {
        assertThat(service.request(USER_ID, JOB_RESULT_ID, null, TailoringFix.AS_IS).orElseThrow().fix()).isNull();
        // Nothing to fix, so there was no need to look for a review.
        verifyNoInteractions(resumeReviewRepository);

        runBackgroundWork();
        verify(resumeTailoringLlmService).tailor(USER_ID, "resume text v3", "Acme Corp", "Staff Engineer",
                JOB_DESCRIPTION, null);
    }

    @Test
    void tailorsAsIs_whenNoneOfTheTickedKeywordsWereInTheReview() {
        reviewed(3);

        assertThat(service.request(USER_ID, JOB_RESULT_ID, null, new TailoringFix(List.of("Rust"), false))
                .orElseThrow().fix()).isNull();
    }

    @Test
    void refusesToFix_withoutAFinishedReviewOfTheLatestResume() {
        final TailoringFix fix = new TailoringFix(List.of("Kafka"), true);

        // Never reviewed.
        assertThatThrownBy(() -> service.request(USER_ID, JOB_RESULT_ID, null, fix))
                .isInstanceOf(ResumeReviewRequiredException.class)
                .hasMessage("review your latest resume against this job before fixing what the review finds");

        // Reviewed, but from the resume before the latest.
        reviewed(2);
        assertThatThrownBy(() -> service.request(USER_ID, JOB_RESULT_ID, null, fix))
                .isInstanceOf(ResumeReviewRequiredException.class);

        // Still being reviewed.
        when(resumeReviewRepository.findById(JOB_RESULT_ID)).thenReturn(Optional.of(new ResumeReview(JOB_RESULT_ID,
                USER_ID, ResumeReview.Status.PENDING, 3, null, null, null, null, NOW, null)));
        assertThatThrownBy(() -> service.request(USER_ID, JOB_RESULT_ID, null, fix))
                .isInstanceOf(ResumeReviewRequiredException.class);

        assertThat(queued).isEmpty();
        assertThat(stored).isNull();
    }

    @Test
    void tailorsAgain_whenAskedToFixSomethingElse() {
        reviewed(3);
        stored = ready(3);

        final Optional<TailoredResume> fixing = service.request(USER_ID, JOB_RESULT_ID, null,
                new TailoringFix(List.of("Kafka"), true));
        assertThat(fixing).map(TailoredResume::status).contains(Status.PENDING);
        assertThat(fixing).map(TailoredResume::fix).contains(KAFKA_AND_RED_FLAGS);
        runBackgroundWork();

        // And back to as is.
        final Optional<TailoredResume> asIs = service.request(USER_ID, JOB_RESULT_ID, null, TailoringFix.AS_IS);
        assertThat(asIs).map(TailoredResume::status).contains(Status.PENDING);
        assertThat(asIs.orElseThrow().fix()).isNull();
        assertThat(queued).hasSize(1);
    }

    @Test
    void leavesAReadyResumeAlone_whenAskedForTheSameFixes() {
        reviewed(3);
        stored = fixedReady();

        assertThat(service.request(USER_ID, JOB_RESULT_ID, null, new TailoringFix(List.of("KAFKA"), true)))
                .contains(fixedReady());
        assertThat(queued).isEmpty();
    }

    @Test
    void keepsTheFixes_whenOnlyTheTemplateChanges() {
        stored = fixedReady();

        final Optional<TailoredResume> requested =
                service.request(USER_ID, JOB_RESULT_ID, ResumeTemplate.MODERN, null);

        assertThat(requested).map(TailoredResume::status).contains(Status.READY);
        assertThat(requested).map(TailoredResume::template).contains(ResumeTemplate.MODERN);
        assertThat(requested).map(TailoredResume::fix).contains(KAFKA_AND_RED_FLAGS);
        assertThat(queued).isEmpty();
        verifyNoInteractions(resumeTailoringLlmService, resumeReviewRepository);
    }

    @Test
    void keepsTheFixes_whenTryingAgainAfterAFailure() {
        stored = fixedReady().failed("we couldn't find this job posting", NOW.minusSeconds(30));

        final Optional<TailoredResume> requested = service.request(USER_ID, JOB_RESULT_ID, null, null);

        assertThat(requested).map(TailoredResume::status).contains(Status.PENDING);
        assertThat(requested).map(TailoredResume::fix).contains(KAFKA_AND_RED_FLAGS);
        verifyNoInteractions(resumeReviewRepository);
    }

    @Test
    void refusesAUserWhoHasNotUploadedAResume() {
        when(resumeService.findLatest(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(USER_ID, JOB_RESULT_ID, null, null))
                .isInstanceOf(ResumeRequiredException.class)
                .hasMessage("upload your resume before tailoring it to a job");

        assertThat(queued).isEmpty();
        verifyNoInteractions(tailoredResumeRepository);
    }

    @Test
    void doesNothing_forAJobTheUserDoesNotHave() {
        when(userJobSearchResultRepository.findByIdAndUserId("someone-elses", USER_ID)).thenReturn(Optional.empty());

        assertThat(service.request(USER_ID, "someone-elses", null, null)).isEmpty();

        assertThat(queued).isEmpty();
        verifyNoInteractions(tailoredResumeRepository, resumeService);
    }

    @Test
    void leavesTheWorkToTheOtherRequest_whenTwoArriveAtOnce() {
        final TailoredResume theirs = pendingSince(NOW.minusMillis(5), 3);
        when(tailoredResumeRepository.insert(any(TailoredResume.class))).thenAnswer(call -> {
            stored = theirs;
            throw new DuplicateKeyException("tailored_resume _id");
        });

        assertThat(service.request(USER_ID, JOB_RESULT_ID, null, null)).contains(theirs);

        assertThat(queued).isEmpty();
    }

    @Test
    void recordsWhyItFailed_whenTheJobPostingCannotBeRead() {
        when(jobDescriptionService.getOrFetch(USER_ID, JOB_RESULT_ID)).thenThrow(new JobDescriptionUnavailableException(
                "we couldn't find this job posting - it may have been removed or need a sign-in to view"));

        service.request(USER_ID, JOB_RESULT_ID, null, null);
        runBackgroundWork();

        assertThat(stored).isEqualTo(new TailoredResume(JOB_RESULT_ID, USER_ID, Status.FAILED, 3,
                ResumeTemplate.CLASSIC, null, null,
                "we couldn't find this job posting - it may have been removed or need a sign-in to view", NOW, NOW,
                null, null));
        verifyNoInteractions(resumeTailoringLlmService, resumeStorageService);
    }

    @Test
    void recordsWhyItFailed_whenTheResumeCannotBeTailored() {
        when(resumeTailoringLlmService.tailor(any(), any(), any(), any(), any(), any()))
                .thenThrow(new ResumeTailoringException("we couldn't make sense of your resume"));

        service.request(USER_ID, JOB_RESULT_ID, null, null);
        runBackgroundWork();

        assertThat(stored.status()).isEqualTo(Status.FAILED);
        assertThat(stored.failureReason()).isEqualTo("we couldn't make sense of your resume");
    }

    @Test
    void recordsAGeneralFailure_withoutLeakingDetails_whenSomethingUnexpectedBreaks() {
        doThrow(new ResumeStorageException("Could not store the resume in S3 - key=tailored/user-1/r-1/v3.pdf", null))
                .when(resumeStorageService).store(anyString(), any(), anyString());

        service.request(USER_ID, JOB_RESULT_ID, null, null);
        runBackgroundWork();

        assertThat(stored.status()).isEqualTo(Status.FAILED);
        assertThat(stored.failureReason())
                .isEqualTo("something went wrong while tailoring your resume, please try again");
        assertThat(stored.s3Key()).isNull();
    }

    @Test
    void discardsAResult_whenTheUserHasAskedAgainSinceItStarted() {
        service.request(USER_ID, JOB_RESULT_ID, null, null);
        // While that one runs, a newer request takes over the record.
        final TailoredResume newer = pendingSince(NOW.plusSeconds(700), 4);
        stored = newer;

        runBackgroundWork();

        assertThat(stored).isEqualTo(newer);
    }

    @Test
    void findsTheTailoredResumeOnlyForTheUserItBelongsTo() {
        stored = ready(3);

        assertThat(service.find(USER_ID, JOB_RESULT_ID)).contains(ready(3));
        assertThat(service.find("user-2", JOB_RESULT_ID)).isEmpty();
    }

    @Test
    void listsEveryTailoredResumeTheUserHas() {
        when(tailoredResumeRepository.findByUserId(USER_ID)).thenReturn(List.of(ready(3)));

        assertThat(service.findAll(USER_ID)).containsExactly(ready(3));
    }

    @Test
    void givesADownloadLinkOnceItIsReady() {
        final ResumeDownload link = new ResumeDownload("https://s3.example/tailored", NOW.plusSeconds(300));
        stored = ready(3);
        when(resumeStorageService.downloadLink(S3_KEY, "Jane-Doe-Acme-Corp-resume.pdf", "application/pdf"))
                .thenReturn(link);

        assertThat(service.downloadLink(USER_ID, JOB_RESULT_ID)).contains(link);
        assertThat(service.downloadLink("user-2", JOB_RESULT_ID)).isEmpty();
    }

    @Test
    void givesNoDownloadLinkWhileItIsNotReady() {
        stored = pendingSince(NOW, 3);
        assertThat(service.downloadLink(USER_ID, JOB_RESULT_ID)).isEmpty();

        stored = stored.failed("we couldn't find this job posting", NOW);
        assertThat(service.downloadLink(USER_ID, JOB_RESULT_ID)).isEmpty();

        stored = null;
        assertThat(service.downloadLink(USER_ID, JOB_RESULT_ID)).isEmpty();
        verifyNoInteractions(resumeStorageService);
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

    private void reviewed(final int resumeVersion) {
        lenient().when(resumeReviewRepository.findById(JOB_RESULT_ID)).thenReturn(Optional.of(new ResumeReview(
                JOB_RESULT_ID, USER_ID, ResumeReview.Status.READY, resumeVersion, 64, List.of("Kafka", "Docker"),
                List.of(SHORT_TENURES, VAGUE_RESULTS), null, NOW.minusSeconds(600), NOW.minusSeconds(590))));
    }

    private static TailoredResume fixedReady() {
        return new TailoredResume(JOB_RESULT_ID, USER_ID, Status.PENDING, 3, ResumeTemplate.CLASSIC, null, null,
                null, NOW.minusSeconds(120), null, null, KAFKA_AND_RED_FLAGS)
                .ready(S3_KEY, "Jane-Doe-Acme-Corp-resume.pdf", CONTENT, NOW.minusSeconds(60));
    }

    private static TailoredResume pendingSince(final Instant requestedAt, final int resumeVersion) {
        return new TailoredResume(JOB_RESULT_ID, USER_ID, Status.PENDING, resumeVersion, ResumeTemplate.CLASSIC, null,
                null, null, requestedAt, null, null, null);
    }

    // What a save and a read back hand you: MongoDB drops anything finer than a millisecond.
    private static TailoredResume asMongoKeepsIt(final TailoredResume saved) {
        return new TailoredResume(saved.jobResultId(), saved.userId(), saved.status(), saved.resumeVersion(),
                saved.template(), saved.s3Key(), saved.fileName(), saved.failureReason(),
                saved.requestedAt().truncatedTo(ChronoUnit.MILLIS),
                saved.completedAt() == null ? null : saved.completedAt().truncatedTo(ChronoUnit.MILLIS),
                saved.content(), saved.fix());
    }

    private static TailoredResume ready(final int resumeVersion) {
        return pendingSince(NOW.minusSeconds(120), resumeVersion)
                .ready("tailored/user-1/r-1/v" + resumeVersion + "-classic.pdf", "Jane-Doe-Acme-Corp-resume.pdf", CONTENT,
                        NOW.minusSeconds(60));
    }
}
