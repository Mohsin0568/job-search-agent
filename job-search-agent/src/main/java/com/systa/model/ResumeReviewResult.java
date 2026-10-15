package com.systa.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.systa.model.ResumeReview.RedFlag;

import java.util.List;

/** What the LLM found when it reviewed a resume against a job, as it returns it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeReviewResult(Integer resumeAtsScore, List<String> missingKeywords, List<RedFlag> redFlags) {}
