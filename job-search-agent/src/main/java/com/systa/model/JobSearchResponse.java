package com.systa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobSearchResponse(String companyName, List<JobListing> jobs) {}
