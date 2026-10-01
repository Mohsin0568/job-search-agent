package com.systa.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The user-editable part of a {@link CandidateProfile}; id and userId are never taken from the client. */
public record CandidateProfileDto(
        @Size(max = 200) String desiredRole,
        @Size(max = 50) List<@NotBlank @Size(max = 100) String> skills,
        @Size(max = 10_000) String currentJobDescription,
        // Each company costs several LLM + Firecrawl calls per search run, so keep the list bounded.
        @Size(max = 10) List<@NotBlank @Size(max = 200) String> companyPreferences,
        @Min(1) @Max(365) Integer recencyWindowDays
) {

    public static CandidateProfileDto from(final CandidateProfile profile) {
        return new CandidateProfileDto(
                profile.desiredRole(),
                profile.skills(),
                profile.currentJobDescription(),
                profile.companyPreferences(),
                profile.recencyWindowDays());
    }
}
