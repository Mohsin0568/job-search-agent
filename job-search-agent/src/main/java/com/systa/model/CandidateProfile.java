package com.systa.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "candidate_profiles")
public record CandidateProfile(
        @Id String id,
        String userId,
        String desiredRole,
        List<String> skills,
        String currentJobDescription,
        List<String> companyPreferences
) {}
