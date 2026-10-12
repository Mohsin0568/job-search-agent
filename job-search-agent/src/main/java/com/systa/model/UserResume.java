package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One uploaded version of a user's resume. The file itself is in S3; every version is kept. */
@Document(collection = "user_resume")
// Two uploads racing for the same version number can't both be recorded.
@CompoundIndex(name = "user_version", def = "{'userId': 1, 'version': 1}", unique = true)
public record UserResume(
        @Id String id,
        String userId,
        // 1 for the user's first upload, one higher for each upload after it.
        int version,
        // True on the user's newest version only.
        boolean latest,
        String s3Key,
        String fileName,
        String contentType,
        long sizeBytes,
        Instant uploadedAt,
        // The resume's text, read once at upload so tailoring it to a job doesn't parse the file again.
        String extractedText
) {}
