package com.systa.model;

/** A place the job search looks for a company's openings. */
public enum JobSource {
    COMPANY_PORTAL("Company careers portal", "The company's official careers/jobs portal", null),
    LINKEDIN("LinkedIn", "LinkedIn (linkedin.com/jobs)", "linkedin");
//    INDEED("Indeed UK", "Indeed UK (uk.indeed.com)", "indeed"),
//    GLASSDOOR("Glassdoor UK", "Glassdoor UK (glassdoor.co.uk)", "glassdoor"),
//    TOTALJOBS("TotalJobs", "TotalJobs (totaljobs.com)", "totaljobs"),
//    REED("Reed", "Reed (reed.co.uk)", "reed");

    private final String displayName;
    private final String promptLabel;
    private final String keyword;

    JobSource(final String displayName, final String promptLabel, final String keyword) {
        this.displayName = displayName;
        this.promptLabel = promptLabel;
        this.keyword = keyword;
    }

    /** The short name the audit records this source under. */
    public String displayName() {
        return displayName;
    }

    /** How the source is described to the LLM. */
    public String promptLabel() {
        return promptLabel;
    }

    /**
     * Lower-case text that picks this job board out of a listing's source name or URL host, or null
     * for the company's own portal, which has no fixed name or domain.
     */
    public String keyword() {
        return keyword;
    }
}
