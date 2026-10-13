package com.systa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A resume rewritten for one job, as the LLM returns it: content only, in the order it is laid out.
 * Every fact in it is meant to come from the user's own resume.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TailoredResumeContent(
        String name,
        // The line under the name, e.g. "Senior Java Developer".
        String headline,
        // Email, phone, location, links - each as the user wrote it.
        List<String> contact,
        String summary,
        List<String> skills,
        List<Experience> experience,
        List<Education> education,
        List<String> certifications,
        // Anything else the resume has: projects, languages, publications, ...
        List<Section> additionalSections
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Experience(String title, String company, String location, String dates, List<String> bullets) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Education(String qualification, String institution, String dates, String details) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(String heading, List<String> items) {}
}
