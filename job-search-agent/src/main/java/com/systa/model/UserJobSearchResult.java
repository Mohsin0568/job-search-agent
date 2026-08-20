package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "user_job_search_result")
public record UserJobSearchResult(
        @Id String id,
        String userId,
        LocalDateTime jobRunDateTime,
        String companyName,
        JobListing job
) {}