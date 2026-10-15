package com.systa.service;

import com.systa.model.FilteredOutJob;
import com.systa.model.JobFilterResult;
import com.systa.model.JobListing;
import com.systa.model.JobPersistResult;
import com.systa.model.JobSearchAudit;
import com.systa.model.JobSearchAudit.SourceAudit;
import com.systa.model.JobSource;
import com.systa.repository.JobSearchAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSearchAuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final JobFilterResult NOTHING_FILTERED = new JobFilterResult(List.of(), List.of());
    private static final JobPersistResult NOTHING_SAVED = new JobPersistResult(List.of(), List.of(), List.of());

    @Mock
    private JobSearchAuditRepository jobSearchAuditRepository;

    private JobSearchAuditService service;

    @BeforeEach
    void setUp() {
        service = new JobSearchAuditService(jobSearchAuditRepository, Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "audit-1");
    }

    @Test
    void attributesAJobToTheBoardItsSourceNames_whateverTheCase() {
        final List<SourceAudit> audits = service.auditScannedBatch(
                List.of(JobSource.COMPANY_PORTAL, JobSource.LINKEDIN),
                List.of(job("linkedin.com", "https://example.com/1"), job("LINKEDIN Jobs", "https://example.com/2")),
                NOTHING_FILTERED, NOTHING_SAVED);

        assertThat(audits).extracting(SourceAudit::source, SourceAudit::jobsRetrieved)
                .containsExactly(tuple("Company careers portal", 0), tuple("LinkedIn", 2));
    }

    @Test
    void fallsBackToWhereTheLinkPoints_whenTheSourceNameMatchesNoBoard() {
        final List<SourceAudit> audits = service.auditScannedBatch(
                List.of(JobSource.COMPANY_PORTAL, JobSource.LINKEDIN),
                List.of(job("Job board", "https://uk.linkedin.com/jobs/view/123"),
                        job(null, "https://www.linkedin.com/jobs/view/9")),
                NOTHING_FILTERED, NOTHING_SAVED);

        assertThat(audits).extracting(SourceAudit::source, SourceAudit::jobsRetrieved)
                .containsExactly(tuple("Company careers portal", 0), tuple("LinkedIn", 2));
    }

    @Test
    void countsAJobNamingNoBoardAgainstTheCompanyPortal_whenTheBatchSearchedIt() {
        final List<SourceAudit> audits = service.auditScannedBatch(
                List.of(JobSource.COMPANY_PORTAL, JobSource.LINKEDIN),
                List.of(job("Acme careers", "https://careers.acme.com/1"), job("LinkedIn", "https://careers.acme.com/2"),
                        job(null, null)),
                NOTHING_FILTERED, NOTHING_SAVED);

        assertThat(audits).extracting(SourceAudit::source, SourceAudit::jobsRetrieved)
                .containsExactly(tuple("Company careers portal", 2), tuple("LinkedIn", 1));
    }

    @Test
    void countsAJobItCannotPlaceAsUnattributed_ratherThanAgainstTheWrongBoard() {
        // A batch without the company portal has nowhere to put a job that names no board it searched.
        final List<SourceAudit> audits = service.auditScannedBatch(List.of(JobSource.LINKEDIN),
                List.of(job("Company website", "https://careers.acme.com/1"), job("LinkedIn", "not a url")),
                NOTHING_FILTERED, NOTHING_SAVED);

        assertThat(audits).extracting(SourceAudit::source, SourceAudit::jobsRetrieved)
                .containsExactly(tuple("LinkedIn", 1), tuple("Unattributed", 1));
    }

    @Test
    void countsEachSourcesJobsAtEveryStage() {
        final JobListing saved = job("LinkedIn", "https://linkedin.com/jobs/1");
        final JobListing duplicate = job("LinkedIn", "https://linkedin.com/jobs/2");
        final JobListing stale = job("LinkedIn", "https://linkedin.com/jobs/3");
        final JobListing undated = job("Company portal", "https://careers.acme.com/4");
        final FilteredOutJob staleDropped = new FilteredOutJob(stale, FilteredOutJob.Reason.OUTSIDE_RECENCY_WINDOW);
        final FilteredOutJob undatedDropped = new FilteredOutJob(undated, FilteredOutJob.Reason.DATE_POSTED_UNREADABLE);

        final List<SourceAudit> audits = service.auditScannedBatch(
                List.of(JobSource.COMPANY_PORTAL, JobSource.LINKEDIN),
                List.of(saved, duplicate, stale, undated),
                new JobFilterResult(List.of(saved, duplicate), List.of(staleDropped, undatedDropped)),
                new JobPersistResult(List.of(saved), List.of(duplicate), List.of("result-1")));

        assertThat(audits).containsExactly(
                new SourceAudit("Company careers portal", SourceAudit.Status.SCANNED, null, 1, 1,
                        List.of(undatedDropped), 0, 0, 0, List.of()),
                new SourceAudit("LinkedIn", SourceAudit.Status.SCANNED, null, 3, 1, List.of(staleDropped), 2, 1, 1,
                        List.of(duplicate)));
    }

    @Test
    void listsEachSourcesDuplicateJobs() {
        final JobListing portalDuplicate = job("Acme careers", "https://careers.acme.com/1");
        final JobListing linkedInDuplicate = job("LinkedIn", "https://linkedin.com/jobs/2");
        final JobListing anotherLinkedInDuplicate = job("LinkedIn", "https://linkedin.com/jobs/3");
        final List<JobListing> duplicates = List.of(portalDuplicate, linkedInDuplicate, anotherLinkedInDuplicate);

        final List<SourceAudit> audits = service.auditScannedBatch(
                List.of(JobSource.COMPANY_PORTAL, JobSource.LINKEDIN), duplicates,
                new JobFilterResult(duplicates, List.of()), new JobPersistResult(List.of(), duplicates, List.of()));

        assertThat(audits).extracting(SourceAudit::source, SourceAudit::duplicateJobsSkipped,
                        SourceAudit::duplicateJobs)
                .containsExactly(
                        tuple("Company careers portal", 1, List.of(portalDuplicate)),
                        tuple("LinkedIn", 2, List.of(linkedInDuplicate, anotherLinkedInDuplicate)));
    }

    @Test
    void marksEverySourceOfAFailedBatchAsFailed_withAShortenedReason() {
        final List<SourceAudit> audits = service.auditFailedBatch(
                List.of(JobSource.COMPANY_PORTAL, JobSource.LINKEDIN), new IllegalStateException("x".repeat(2000)));

        assertThat(audits).extracting(SourceAudit::source).containsExactly("Company careers portal", "LinkedIn");
        assertThat(audits).allSatisfy(audit -> {
            assertThat(audit.status()).isEqualTo(SourceAudit.Status.FAILED);
            assertThat(audit.failureReason()).startsWith("java.lang.IllegalStateException: xxx").hasSize(500);
            assertThat(audit.jobsRetrieved()).isZero();
        });
    }

    @Test
    void savesOneAuditForTheCompany_countingScannedAndFailedSources() {
        final List<SourceAudit> sourceAudits = List.of(
                new SourceAudit("Indeed UK", SourceAudit.Status.SCANNED, null, 1, 0, List.of(), 1, 1, 0, List.of()),
                new SourceAudit("Glassdoor UK", SourceAudit.Status.SCANNED, null, 0, 0, List.of(), 0, 0, 0,
                        List.of()),
                new SourceAudit("Unattributed", SourceAudit.Status.SCANNED, null, 1, 0, List.of(), 1, 1, 0,
                        List.of()),
                SourceAudit.failed("Reed", "boom"));

        service.record("user-1", "Acme Corp", sourceAudits);

        verify(jobSearchAuditRepository).save(
                new JobSearchAudit("audit-1", "user-1", "Acme Corp", NOW, 2, 1, sourceAudits));
    }

    @Test
    void swallowsAFailureToSaveTheAudit() {
        when(jobSearchAuditRepository.save(any())).thenThrow(new IllegalStateException("Mongo unavailable"));

        assertThatCode(() -> service.record("user-1", "Acme Corp", List.of())).doesNotThrowAnyException();
    }

    private static org.assertj.core.groups.Tuple tuple(final Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }

    private static JobListing job(final String source, final String url) {
        return new JobListing("Not specified", "Backend Engineer", url, "London, UK", "17 Aug 2026",
                "Not specified", "Not specified", source, 85);
    }
}
