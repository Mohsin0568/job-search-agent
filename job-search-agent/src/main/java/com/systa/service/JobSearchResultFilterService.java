package com.systa.service;

import com.systa.model.CompanySearchResult;
import com.systa.model.JobListing;
import com.systa.model.JobSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class JobSearchResultFilterService {

    private static final int RECENCY_WINDOW_DAYS = 7;

    private static final DateTimeFormatter DATE_POSTED_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final Logger LOGGER = LoggerFactory.getLogger(JobSearchResultFilterService.class);

    private final Clock clock;

    @Autowired
    public JobSearchResultFilterService() {
        this(Clock.systemDefaultZone());
    }

    JobSearchResultFilterService(final Clock clock) {
        this.clock = clock;
    }

    // Backstop for the LLM's own recency rule: it doesn't reliably apply the "last 7 days"
    // cutoff itself (e.g. it once included a job posted 27 days ago), so re-check it here in code.
    public JobSearchResponse filterStaleJobs(final String userId, final JobSearchResponse jobSearchResponse) {
        final LocalDate today = LocalDate.now(clock);
        final LocalDate earliestAllowedDate = today.minusDays(RECENCY_WINDOW_DAYS);

        final List<CompanySearchResult> filteredCompanies = jobSearchResponse.companies().stream()
                .map(company -> new CompanySearchResult(company.companyName(), company.jobs().stream()
                        .filter(job -> isWithinRecencyWindow(userId, job, earliestAllowedDate, today))
                        .toList()))
                .toList();

        final JobSearchResponse filteredResponse = new JobSearchResponse(filteredCompanies);

        for (final CompanySearchResult company : filteredResponse.companies()) {
            LOGGER.info("Job search result summary - userId={}, company={}, jobsFound={}",
                    userId, company.companyName(), company.jobs().size());
        }

        return filteredResponse;
    }

    private boolean isWithinRecencyWindow(final String userId, final JobListing job,
                                           final LocalDate earliestAllowedDate, final LocalDate today) {
        final LocalDate datePosted = parseDatePosted(job.datePosted());
        if (datePosted == null) {
            LOGGER.info("Dropping job with unparseable/unspecified datePosted - userId={}, jobTitle={}, "
                            + "datePosted={}, url={}",
                    userId, job.jobTitle(), job.datePosted(), job.url());
            return false;
        }

        final boolean withinWindow = !datePosted.isBefore(earliestAllowedDate) && !datePosted.isAfter(today);
        if (!withinWindow) {
            LOGGER.info("Dropping stale job outside {}-day recency window - userId={}, jobTitle={}, "
                            + "datePosted={}, url={}",
                    RECENCY_WINDOW_DAYS, userId, job.jobTitle(), job.datePosted(), job.url());
        }
        return withinWindow;
    }

    private LocalDate parseDatePosted(final String datePosted) {
        try {
            return LocalDate.parse(datePosted, DATE_POSTED_FORMAT);
        } catch (DateTimeParseException | NullPointerException _) {
            return null;
        }
    }
}
