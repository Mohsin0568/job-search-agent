package com.systa.model;

import java.util.List;

/** What the LLM read off a job posting's page. */
public record JobPosting(String description, List<String> requiredSkills) {}
