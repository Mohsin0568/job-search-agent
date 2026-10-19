package com.systa.repository;

import com.systa.MongoTestContainer;
import com.systa.model.FilteredOutJob;
import com.systa.model.JobListing;
import com.systa.model.JobSearchAudit;
import com.systa.model.JobSearchAudit.SourceAudit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(MongoTestContainer.class)
class JobSearchAuditRepositoryTest {

    @Autowired
    private JobSearchAuditRepository repository;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void savesAndReadsBackAnAudit_withItsSourcesFilteredOutJobsAndDuplicates() {
        final JobListing stale = new JobListing("J-1", "Backend Engineer", "https://linkedin.com/jobs/1", "London, UK",
                "01 Aug 2026", "Not specified", "Not specified", "LinkedIn", 85);
        final JobListing duplicate = new JobListing("J-2", "Platform Engineer", "https://linkedin.com/jobs/2",
                "London, UK", "17 Aug 2026", "Not specified", "Not specified", "LinkedIn", 80);
        final JobSearchAudit audit = new JobSearchAudit("audit-1", "user-1", "run-1", "Acme Corp",
                Instant.parse("2026-08-18T12:00:00Z"), 1, 1, List.of(
                        new SourceAudit("LinkedIn", SourceAudit.Status.SCANNED, null, 3, 1,
                                List.of(new FilteredOutJob(stale, FilteredOutJob.Reason.OUTSIDE_RECENCY_WINDOW)), 2, 1, 1,
                                List.of(duplicate)),
                        SourceAudit.failed("Reed", "429 rate limit")));

        repository.save(audit);

        assertThat(repository.findById("audit-1")).contains(audit);
    }

    @Test
    void readsTheCountsOfTheUsersAuditsFromThoseRuns_leavingTheJobListsBehind() {
        final SourceAudit linkedIn = new SourceAudit("LinkedIn", SourceAudit.Status.SCANNED, null, 3, 1,
                List.of(new FilteredOutJob(job("J-1"), FilteredOutJob.Reason.OUTSIDE_RECENCY_WINDOW)), 2, 1, 1,
                List.of(job("J-2")));
        repository.saveAll(List.of(
                audit("in-run-1", "user-1", "run-1", linkedIn),
                audit("in-run-2", "user-1", "run-2", linkedIn),
                audit("other-run", "user-1", "run-3", linkedIn),
                audit("before-runs-were-kept", "user-1", null, linkedIn),
                audit("other-users", "user-2", "run-1", linkedIn)));

        final List<JobSearchAudit> found = repository.findCountsByUserIdAndRunIdIn("user-1", List.of("run-1", "run-2"));

        assertThat(found).extracting(JobSearchAudit::id).containsExactlyInAnyOrder("in-run-1", "in-run-2");
        assertThat(found).flatExtracting(JobSearchAudit::sources).allSatisfy(source -> {
            assertThat(source.jobsRetrieved()).isEqualTo(3);
            assertThat(source.jobsFilteredOut()).isEqualTo(1);
            assertThat(source.jobsSaved()).isEqualTo(1);
            assertThat(source.duplicateJobsSkipped()).isEqualTo(1);
            assertThat(source.filteredOutJobs()).isNull();
            assertThat(source.duplicateJobs()).isNull();
        });
    }

    private static JobSearchAudit audit(final String id, final String userId, final String runId,
                                        final SourceAudit source) {
        return new JobSearchAudit(id, userId, runId, "Acme Corp", Instant.parse("2026-08-18T12:00:00Z"), 1, 0,
                List.of(source));
    }

    private static JobListing job(final String jobId) {
        return new JobListing(jobId, "Backend Engineer", "https://linkedin.com/jobs/" + jobId, "London, UK",
                "17 Aug 2026", "Not specified", "Not specified", "LinkedIn", 80);
    }
}
