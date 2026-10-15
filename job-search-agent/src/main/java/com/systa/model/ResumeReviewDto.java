package com.systa.model;

import com.systa.model.ResumeReview.RedFlag;

import java.time.Instant;
import java.util.List;

/** What the API shows of a resume review - everything but whose it is. */
public record ResumeReviewDto(
        String jobResultId,
        ResumeReview.Status status,
        int resumeVersion,
        Integer resumeAtsScore,
        List<String> missingKeywords,
        List<RedFlag> redFlags,
        String failureReason,
        Instant requestedAt,
        Instant reviewedAt
) {

    public static ResumeReviewDto from(final ResumeReview review) {
        return new ResumeReviewDto(review.jobResultId(), review.status(), review.resumeVersion(),
                review.resumeAtsScore(), review.missingKeywords(), review.redFlags(), review.failureReason(),
                review.requestedAt(), review.reviewedAt());
    }
}
