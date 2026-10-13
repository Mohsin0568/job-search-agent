package com.systa.model;

import java.time.Instant;
import java.util.List;

public record JobDescriptionDto(
        String jobResultId,
        String description,
        List<String> requiredSkills,
        Instant fetchedAt
) {

    public static JobDescriptionDto from(final JobDescription jobDescription) {
        return new JobDescriptionDto(jobDescription.jobResultId(), jobDescription.description(),
                jobDescription.requiredSkills(), jobDescription.fetchedAt());
    }
}
