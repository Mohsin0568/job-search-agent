package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * What a job posting asks for, read from the posting's page the first time it is needed (to tailor
 * a resume to the job) and kept so the page isn't read again.
 */
@Document(collection = "job_description")
public record JobDescription(
        // The UserJobSearchResult this describes - one description per saved job.
        @Id String jobResultId,
        String userId,
        // The posting page the description was read from.
        String sourceUrl,
        // A condensed account of the role: responsibilities, requirements, seniority.
        String description,
        List<String> requiredSkills,
        Instant fetchedAt
) {}
