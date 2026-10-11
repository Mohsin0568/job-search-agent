package com.systa.service;

import com.systa.model.CompanySearchResult;
import com.systa.model.FilteredOutJob;
import com.systa.model.JobFilterResult;
import com.systa.model.JobListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class JobSearchResultFilterService {

    // Used when a candidate profile hasn't configured its own recencyWindowDays.
    static final int DEFAULT_RECENCY_WINDOW_DAYS = 7;

    // English month names whatever the JVM's default locale: en_GB would expect "Sept", not "Sep".
    // A single "d" also reads a day the LLM wrote without its leading zero ("7 Aug 2026").
    private static final DateTimeFormatter DATE_POSTED_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static final Logger LOGGER = LoggerFactory.getLogger(JobSearchResultFilterService.class);

    private final Clock clock;

    @Autowired
    public JobSearchResultFilterService() {
        this(Clock.systemDefaultZone());
    }

    JobSearchResultFilterService(final Clock clock) {
        this.clock = clock;
    }

    // Backstop for the LLM's own recency rule: it doesn't reliably apply the recency
    // cutoff itself (e.g. it once included a job posted 27 days ago), so re-check it here in code.
    public JobFilterResult filterStaleJobs(final String userId, final Integer recencyWindowDays,
                                            final CompanySearchResult company) {
        final int effectiveRecencyWindowDays = effectiveRecencyWindowDays(recencyWindowDays);

        final LocalDate today = LocalDate.now(clock);
        final LocalDate earliestAllowedDate = today.minusDays(effectiveRecencyWindowDays);

        final List<JobListing> validJobs = new ArrayList<>();
        final List<FilteredOutJob> filteredOutJobs = new ArrayList<>();
        for (final JobListing job : company.jobs()) {
            final FilteredOutJob.Reason reasonToDrop = reasonToDrop(userId, job, earliestAllowedDate, today,
                    effectiveRecencyWindowDays);
            if (reasonToDrop == null) {
                validJobs.add(job);
            } else {
                filteredOutJobs.add(new FilteredOutJob(job, reasonToDrop));
            }
        }

        LOGGER.info("Job search result summary - userId={}, company={}, jobsFound={}",
                userId, company.companyName(), validJobs.size());

        return new JobFilterResult(List.copyOf(validJobs), List.copyOf(filteredOutJobs));
    }

    /** The profile's window, or the default when it hasn't set one. Shared with the LLM prompt. */
    static int effectiveRecencyWindowDays(final Integer recencyWindowDays) {
        return recencyWindowDays != null ? recencyWindowDays : DEFAULT_RECENCY_WINDOW_DAYS;
    }

    /** Why the job fails the recency check, or null when it passes. */
    private FilteredOutJob.Reason reasonToDrop(final String userId, final JobListing job,
                                               final LocalDate earliestAllowedDate, final LocalDate today,
                                               final int recencyWindowDays) {
        final LocalDate datePosted = parseDatePosted(job.datePosted());
        if (datePosted == null) {
            LOGGER.info("Dropping job with unparseable/unspecified datePosted - userId={}, jobTitle={}, "
                            + "datePosted={}, url={}",
                    userId, job.jobTitle(), job.datePosted(), job.url());
            return FilteredOutJob.Reason.DATE_POSTED_UNREADABLE;
        }

        final boolean withinWindow = !datePosted.isBefore(earliestAllowedDate) && !datePosted.isAfter(today);
        if (!withinWindow) {
            LOGGER.info("Dropping stale job outside {}-day recency window - userId={}, jobTitle={}, "
                            + "datePosted={}, url={}",
                    recencyWindowDays, userId, job.jobTitle(), job.datePosted(), job.url());
            return FilteredOutJob.Reason.OUTSIDE_RECENCY_WINDOW;
        }
        return null;
    }

    private LocalDate parseDatePosted(final String datePosted) {
        try {
            return LocalDate.parse(datePosted, DATE_POSTED_FORMAT);
        } catch (DateTimeParseException | NullPointerException _) {
            return null;
        }
    }
}
