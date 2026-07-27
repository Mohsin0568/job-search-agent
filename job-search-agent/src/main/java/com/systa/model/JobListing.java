package com.systa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobListing(
        String jobTitle,
        String url,
        String location,
        String datePosted,
        String lastDateForSubmission,
        String salaryRange,
        String source,
        Integer atsScore
) {}
