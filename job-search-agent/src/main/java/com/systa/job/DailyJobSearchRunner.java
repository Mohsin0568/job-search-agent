package com.systa.job;

import com.systa.JobSearchAgentApplication;
import com.systa.config.DailyJobSearchProperties;
import com.systa.model.CandidateProfile;
import com.systa.model.JobSearchOutcome;
import com.systa.model.UserJobSearchRun;
import com.systa.model.UserJobSearchRun.Status;
import com.systa.repository.CandidateProfileRepository;
import com.systa.repository.UserJobSearchRunRepository;
import com.systa.service.JobSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * The daily job search: started with the "job" profile, it searches for every user with a profile,
 * one at a time, and then the process exits (see {@link JobSearchAgentApplication}).
 *
 * <p>Each user's outcome is recorded, so running the job again on the same day only picks up the
 * users it didn't get to or whose search failed.
 */
@Component
@Profile(JobSearchAgentApplication.JOB_PROFILE)
@Slf4j
public class DailyJobSearchRunner implements ApplicationRunner, ExitCodeGenerator {

    // A stable order, so paging doesn't skip or repeat users while the job is running.
    private static final Sort BY_ID = Sort.by("id");

    private final CandidateProfileRepository candidateProfileRepository;
    private final UserJobSearchRunRepository userJobSearchRunRepository;
    private final JobSearchService jobSearchService;
    private final DailyJobSearchProperties properties;
    private final Clock clock;

    private int exitCode;

    @Autowired
    public DailyJobSearchRunner(final CandidateProfileRepository candidateProfileRepository,
                                final UserJobSearchRunRepository userJobSearchRunRepository,
                                final JobSearchService jobSearchService,
                                final DailyJobSearchProperties properties) {
        this(candidateProfileRepository, userJobSearchRunRepository, jobSearchService, properties, Clock.systemUTC());
    }

    DailyJobSearchRunner(final CandidateProfileRepository candidateProfileRepository,
                         final UserJobSearchRunRepository userJobSearchRunRepository,
                         final JobSearchService jobSearchService,
                         final DailyJobSearchProperties properties,
                         final Clock clock) {
        this.candidateProfileRepository = candidateProfileRepository;
        this.userJobSearchRunRepository = userJobSearchRunRepository;
        this.jobSearchService = jobSearchService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(final ApplicationArguments args) {
        final LocalDate runDate = LocalDate.now(clock);
        log.info("Daily job search started - runDate={}, force={}", runDate, properties.force());

        try {
            final Map<Status, Integer> counts = searchForAllUsers(runDate);
            final int failed = counts.getOrDefault(Status.FAILED, 0);
            exitCode = failed > 0 ? 1 : 0;
            log.info("Daily job search finished - runDate={}, succeeded={}, partial={}, failed={}, skipped={}",
                    runDate, counts.getOrDefault(Status.SUCCEEDED, 0), counts.getOrDefault(Status.PARTIAL, 0),
                    failed, counts.getOrDefault(Status.SKIPPED, 0));
        } catch (final Exception e) {
            // Caught rather than thrown so the process still exits through the normal path, with a
            // non-zero code for whatever launched it to see.
            exitCode = 1;
            log.error("Daily job search aborted - runDate={}", runDate, e);
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private Map<Status, Integer> searchForAllUsers(final LocalDate runDate) {
        final Map<Status, Integer> counts = new EnumMap<>(Status.class);
        boolean searchedSomeone = false;

        Pageable pageable = PageRequest.of(0, properties.pageSize(), BY_ID);
        Slice<CandidateProfile> profiles;
        do {
            profiles = candidateProfileRepository.findAll(pageable);
            for (final CandidateProfile profile : profiles) {
                if (!properties.force() && alreadyDone(profile.userId(), runDate)) {
                    log.info("Already searched today, skipping - userId={}", profile.userId());
                    counts.merge(Status.SKIPPED, 1, Integer::sum);
                    continue;
                }
                if (searchedSomeone) {
                    sleepBetweenUsers();
                }
                searchedSomeone = true;
                counts.merge(searchFor(profile, runDate), 1, Integer::sum);
            }
            pageable = profiles.nextPageable();
        } while (profiles.hasNext());

        return counts;
    }

    private boolean alreadyDone(final String userId, final LocalDate runDate) {
        return userJobSearchRunRepository.findById(userId)
                .map(lastRun -> lastRun.isDoneFor(runDate))
                .orElse(false);
    }

    // One user's failure - in the search or in recording it - must not stop the users after them.
    private Status searchFor(final CandidateProfile profile, final LocalDate runDate) {
        final String userId = profile.userId();
        final Instant startedAt = clock.instant();
        JobSearchOutcome outcome = new JobSearchOutcome(0, 0);
        Status status;
        try {
            outcome = jobSearchService.searchJobs(profile);
            status = statusOf(outcome);
        } catch (final Exception e) {
            status = Status.FAILED;
            log.error("Job search failed - userId={}", userId, e);
        }
        log.info("Job search for user finished - userId={}, status={}, batchesAttempted={}, batchesFailed={}",
                userId, status, outcome.batchesAttempted(), outcome.batchesFailed());

        try {
            userJobSearchRunRepository.save(new UserJobSearchRun(userId, runDate.toString(), startedAt, clock.instant(),
                    status, outcome.batchesAttempted(), outcome.batchesFailed()));
        } catch (final Exception e) {
            log.error("Could not record the job search run - userId={}, status={}", userId, status, e);
        }
        return status;
    }

    private static Status statusOf(final JobSearchOutcome outcome) {
        if (outcome.nothingToSearch()) {
            return Status.SKIPPED;
        }
        if (outcome.allBatchesFailed()) {
            return Status.FAILED;
        }
        return outcome.someBatchesFailed() ? Status.PARTIAL : Status.SUCCEEDED;
    }

    private void sleepBetweenUsers() {
        try {
            Thread.sleep(properties.delayBetweenUsers().toMillis());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting between users", e);
        }
    }
}
