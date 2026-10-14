package com.systa.model;

/** The body of a request to tailor a resume, all optional. */
public record TailoredResumeRequest(
        // Null keeps the job's current template, or the default if it has none yet.
        ResumeTemplate template
) {}
