package com.systa.model;

import java.time.Instant;

/** What the API shows of a resume version - not where it is stored, nor its text. */
public record ResumeDto(
        int version,
        boolean latest,
        String fileName,
        long sizeBytes,
        Instant uploadedAt
) {

    public static ResumeDto from(final UserResume resume) {
        return new ResumeDto(resume.version(), resume.latest(), resume.fileName(), resume.sizeBytes(),
                resume.uploadedAt());
    }
}
