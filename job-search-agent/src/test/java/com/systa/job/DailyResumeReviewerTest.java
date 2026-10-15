package com.systa.job;

import com.systa.config.DailyJobSearchProperties;
import com.systa.model.ResumeReview;
import com.systa.model.ResumeReview.Status;
import com.systa.model.UserResume;
import com.systa.service.ResumeReviewService;
import com.systa.service.ResumeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyResumeReviewerTest {

    private static final String USER_ID = "ada";
    private static final Instant NOW = Instant.parse("2026-10-15T02:00:00Z");
    private static final UserResume RESUME = new UserResume("resume-2", USER_ID, 2, true,
            "resumes/ada/v2/id.pdf", "cv.pdf", "application/pdf", 2048, NOW, "resume text");

    @Mock
    private ResumeService resumeService;

    @Mock
    private ResumeReviewService resumeReviewService;

    private DailyResumeReviewer reviewer;

    @BeforeEach
    void setUp() {
        reviewer = reviewerWithCap(20);
        lenient().when(resumeService.findLatest(USER_ID)).thenReturn(Optional.of(RESUME));
        lenient().when(resumeReviewService.reviewNewJob(eq(USER_ID), anyString(), eq(RESUME)))
                .thenAnswer(call -> Optional.of(review(call.getArgument(1), Status.READY)));
    }

    @Test
    void reviewsEachNewJob_oneAfterAnother_inTheOrderTheyWereSaved() {
        reviewer.reviewNewJobs(USER_ID, List.of("job-1", "job-2", "job-3"));

        final InOrder inOrder = inOrder(resumeReviewService);
        inOrder.verify(resumeReviewService).reviewNewJob(USER_ID, "job-1", RESUME);
        inOrder.verify(resumeReviewService).reviewNewJob(USER_ID, "job-2", RESUME);
        inOrder.verify(resumeReviewService).reviewNewJob(USER_ID, "job-3", RESUME);
        // The resume is looked up once for all of them.
        verify(resumeService, times(1)).findLatest(USER_ID);
    }

    @Test
    void reviewsNoMoreThanTheCap_leavingTheRestForOnRequest() {
        reviewer = reviewerWithCap(2);

        reviewer.reviewNewJobs(USER_ID, List.of("job-1", "job-2", "job-3"));

        verify(resumeReviewService).reviewNewJob(USER_ID, "job-1", RESUME);
        verify(resumeReviewService).reviewNewJob(USER_ID, "job-2", RESUME);
        verify(resumeReviewService, never()).reviewNewJob(USER_ID, "job-3", RESUME);
    }

    @Test
    void skipsAUserWithNoResume() {
        when(resumeService.findLatest(USER_ID)).thenReturn(Optional.empty());

        reviewer.reviewNewJobs(USER_ID, List.of("job-1"));

        verifyNoInteractions(resumeReviewService);
    }

    @Test
    void doesNothing_whenTheSearchSavedNoJobs() {
        reviewer.reviewNewJobs(USER_ID, List.of());

        verifyNoInteractions(resumeService, resumeReviewService);
    }

    @Test
    void carriesOnWithTheNextJob_whenOneReviewFailsOrThrows() {
        when(resumeReviewService.reviewNewJob(USER_ID, "job-1", RESUME))
                .thenReturn(Optional.of(review("job-1", Status.FAILED)));
        when(resumeReviewService.reviewNewJob(USER_ID, "job-2", RESUME))
                .thenThrow(new IllegalStateException("Mongo unavailable"));

        reviewer.reviewNewJobs(USER_ID, List.of("job-1", "job-2", "job-3"));

        verify(resumeReviewService).reviewNewJob(USER_ID, "job-3", RESUME);
    }

    private DailyResumeReviewer reviewerWithCap(final int reviewsPerUser) {
        return new DailyResumeReviewer(resumeService, resumeReviewService,
                new DailyJobSearchProperties(50, Duration.ZERO, false, reviewsPerUser, Duration.ZERO));
    }

    private static ResumeReview review(final String jobResultId, final Status status) {
        return new ResumeReview(jobResultId, USER_ID, status, 2, null, null, null, null, NOW, NOW);
    }
}
