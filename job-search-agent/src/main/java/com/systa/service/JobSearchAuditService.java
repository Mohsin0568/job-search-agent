package com.systa.service;

import com.systa.model.FilteredOutJob;
import com.systa.model.JobFilterResult;
import com.systa.model.JobListing;
import com.systa.model.JobPersistResult;
import com.systa.model.JobSearchAudit;
import com.systa.model.JobSearchAudit.SourceAudit;
import com.systa.model.JobSource;
import com.systa.repository.JobSearchAuditRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Records, per user and company, what each job source contributed to a search. */
@Service
@Slf4j
public class JobSearchAuditService {

    // Where a job is counted when it can't be tied to any source its batch searched.
    static final String UNATTRIBUTED = "Unattributed";

    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    private final JobSearchAuditRepository jobSearchAuditRepository;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    @Autowired
    public JobSearchAuditService(final JobSearchAuditRepository jobSearchAuditRepository) {
        this(jobSearchAuditRepository, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    JobSearchAuditService(final JobSearchAuditRepository jobSearchAuditRepository, final Clock clock,
                           final Supplier<String> idGenerator) {
        this.jobSearchAuditRepository = jobSearchAuditRepository;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /** Splits one batch's jobs between the sources that batch searched. */
    public List<SourceAudit> auditScannedBatch(final List<JobSource> sources, final List<JobListing> retrievedJobs,
                                                final JobFilterResult filterResult, final JobPersistResult persistResult) {
        final Map<String, Tally> tallies = new LinkedHashMap<>();
        sources.forEach(source -> tallies.put(source.displayName(), new Tally()));

        retrievedJobs.forEach(job -> tallyFor(tallies, sources, job).retrieved++);
        filterResult.filteredOutJobs().forEach(filteredOut ->
                tallyFor(tallies, sources, filteredOut.job()).filteredOut.add(filteredOut));
        filterResult.validJobs().forEach(job -> tallyFor(tallies, sources, job).valid++);
        persistResult.savedJobs().forEach(job -> tallyFor(tallies, sources, job).saved++);
        persistResult.duplicateJobs().forEach(job -> tallyFor(tallies, sources, job).duplicates.add(job));

        return tallies.entrySet().stream()
                .map(entry -> entry.getValue().toSourceAudit(entry.getKey()))
                .toList();
    }

    public List<SourceAudit> auditFailedBatch(final List<JobSource> sources, final Exception failure) {
        final String failureReason = abbreviate(failure.toString());
        return sources.stream()
                .map(source -> SourceAudit.failed(source.displayName(), failureReason))
                .toList();
    }

    // The audit is a record of the search, not part of it: failing to save it must not fail the search.
    public void record(final String userId, final String companyName, final List<SourceAudit> sourceAudits) {
        try {
            final int sourcesFailed = (int) sourceAudits.stream()
                    .filter(sourceAudit -> sourceAudit.status() == SourceAudit.Status.FAILED)
                    .count();
            final int sourcesScanned = (int) sourceAudits.stream()
                    .filter(sourceAudit -> sourceAudit.status() == SourceAudit.Status.SCANNED)
                    .filter(sourceAudit -> !UNATTRIBUTED.equals(sourceAudit.source()))
                    .count();

            jobSearchAuditRepository.save(new JobSearchAudit(idGenerator.get(), userId, companyName, clock.instant(),
                    sourcesScanned, sourcesFailed, sourceAudits));
        } catch (final Exception e) {
            log.error("Could not record the job search audit - userId={}, company={}", userId, companyName, e);
        }
    }

    private static Tally tallyFor(final Map<String, Tally> tallies, final List<JobSource> sources,
                                   final JobListing job) {
        return tallies.computeIfAbsent(attribute(sources, job), _ -> new Tally());
    }

    private static String attribute(final List<JobSource> sources, final JobListing job) {
        final JobSource source = JobSource.of(job, sources);
        return source != null ? source.displayName() : UNATTRIBUTED;
    }

    private static String abbreviate(final String text) {
        return text.length() <= MAX_FAILURE_REASON_LENGTH ? text : text.substring(0, MAX_FAILURE_REASON_LENGTH);
    }

    private static final class Tally {
        private int retrieved;
        private final List<FilteredOutJob> filteredOut = new ArrayList<>();
        private int valid;
        private int saved;
        private final List<JobListing> duplicates = new ArrayList<>();

        private SourceAudit toSourceAudit(final String source) {
            return new SourceAudit(source, SourceAudit.Status.SCANNED, null, retrieved, filteredOut.size(),
                    List.copyOf(filteredOut), valid, saved, duplicates.size(), List.copyOf(duplicates));
        }
    }
}
