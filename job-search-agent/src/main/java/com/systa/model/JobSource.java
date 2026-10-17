package com.systa.model;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/** A place the job search looks for a company's openings. */
public enum JobSource {
    COMPANY_PORTAL("Company careers portal", "The company's official careers/jobs portal", null),
    LINKEDIN("LinkedIn", "LinkedIn (linkedin.com/jobs)", "linkedin"),
    INDEED("Indeed UK", "Indeed UK (uk.indeed.com)", "indeed");
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

    /**
     * Which of the searched sources the job came from, or null when it can't be tied to any of them.
     */
    // The LLM names each job's source in its own words ("LinkedIn", "company portal", ...), so match
    // on the job board's name, then on where the link points, and otherwise assume the company's own
    // portal - the only source without a fixed name or domain.
    public static JobSource of(final JobListing job, final List<JobSource> searched) {
        final String sourceName = lowerCase(job.source());
        final String urlHost = urlHost(job.url());

        for (final JobSource source : searched) {
            if (source.keyword() != null && sourceName.contains(source.keyword())) {
                return source;
            }
        }
        for (final JobSource source : searched) {
            if (source.keyword() != null && urlHost.contains(source.keyword())) {
                return source;
            }
        }
        return searched.contains(COMPANY_PORTAL) ? COMPANY_PORTAL : null;
    }

    private static String urlHost(final String url) {
        try {
            return lowerCase(URI.create(url.trim()).getHost());
        } catch (final IllegalArgumentException | NullPointerException _) {
            return "";
        }
    }

    private static String lowerCase(final String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
