package com.systa.model;

import java.time.Instant;

/** What the API shows of a tailored resume - not where it is stored. */
public record TailoredResumeDto(
        String jobResultId,
        TailoredResume.Status status,
        int resumeVersion,
        String failureReason,
        Instant requestedAt,
        Instant completedAt
) {

    public static TailoredResumeDto from(final TailoredResume tailoredResume) {
        return new TailoredResumeDto(tailoredResume.jobResultId(), tailoredResume.status(),
                tailoredResume.resumeVersion(), tailoredResume.failureReason(), tailoredResume.requestedAt(),
                tailoredResume.completedAt());
    }
}
