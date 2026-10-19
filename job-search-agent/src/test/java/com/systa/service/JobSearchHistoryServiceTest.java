package com.systa.service;

import com.systa.model.JobSearchAudit;
import com.systa.model.JobSearchAudit.SourceAudit;
import com.systa.model.JobSearchRun;
import com.systa.model.JobSearchRunDetailDto;
import com.systa.model.JobSearchRunDetailDto.CompanyResult;
import com.systa.model.JobSearchRunDetailDto.SourceResult;
import com.systa.model.JobSearchRunSummaryDto;
import com.systa.model.UserJobSearchRun.Status;
import com.systa.repository.JobSearchAuditRepository;
import com.systa.repository.JobSearchRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSearchHistoryServiceTest {

    private static final String USER_ID = "user-1";
    private static final Instant STARTED_AT = Instant.parse("2026-10-08T02:00:00Z");

    private static final JobSearchRun RUN = new JobSearchRun("run-1", USER_ID, "2026-10-08", STARTED_AT,
            STARTED_AT.plusSeconds(300), Status.PARTIAL, 4, 1);
    private static final JobSearchRun EARLIER_RUN = new JobSearchRun("run-0", USER_ID, "2026-10-07",
            STARTED_AT.minusSeconds(86_400), STARTED_AT.minusSeconds(86_100), Status.SUCCEEDED, 4, 0);

    // Acme: the portal found 2 jobs, both new; LinkedIn found 4 - 1 too old, 2 new, 1 already saved.
    private static final JobSearchAudit ACME = new JobSearchAudit("audit-acme", USER_ID, "run-1", "Acme Corp",
            STARTED_AT.plusSeconds(10), 2, 0, List.of(
                    scanned("Company careers portal", 2, 0, 2, 0),
                    scanned("LinkedIn", 4, 1, 2, 1)));
    // Globex: the portal found nothing new, LinkedIn's batch failed.
    private static final JobSearchAudit GLOBEX = new JobSearchAudit("audit-globex", USER_ID, "run-1", "Globex",
            STARTED_AT.plusSeconds(120), 1, 1, List.of(
                    scanned("Company careers portal", 1, 0, 0, 1),
                    SourceAudit.failed("LinkedIn", "java.lang.IllegalStateException: 429")));

    @Mock
    private JobSearchRunRepository jobSearchRunRepository;

    @Mock
    private JobSearchAuditRepository jobSearchAuditRepository;

    private JobSearchHistoryService service;

    @BeforeEach
    void setUp() {
        service = new JobSearchHistoryService(jobSearchRunRepository, jobSearchAuditRepository);
    }

    @Test
    void listsEachRunWithItsTotalsAcrossCompanies() {
        when(jobSearchRunRepository.findByUserId(eq(USER_ID), any())).thenReturn(List.of(RUN, EARLIER_RUN));
        when(jobSearchAuditRepository.findCountsByUserIdAndRunIdIn(USER_ID, List.of("run-1", "run-0")))
                .thenReturn(List.of(GLOBEX, ACME));

        assertThat(service.findRuns(USER_ID, 0, 20)).containsExactly(
                new JobSearchRunSummaryDto("run-1", "2026-10-08", STARTED_AT, STARTED_AT.plusSeconds(300),
                        Status.PARTIAL, 2, 4, 2, 1, 1),
                // Its audits weren't saved (or it searched nothing): a row with nothing found.
                new JobSearchRunSummaryDto("run-0", "2026-10-07", STARTED_AT.minusSeconds(86_400),
                        STARTED_AT.minusSeconds(86_100), Status.SUCCEEDED, 0, 0, 0, 0, 0));
    }

    @Test
    void asksForTheRequestedPage_newestRunFirst() {
        when(jobSearchRunRepository.findByUserId(eq(USER_ID), any())).thenReturn(List.of());

        service.findRuns(USER_ID, 2, 10);

        final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(jobSearchRunRepository).findByUserId(eq(USER_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().getOrderFor("startedAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void readsNoAudits_whenTheUserHasNoRuns() {
        when(jobSearchRunRepository.findByUserId(eq(USER_ID), any())).thenReturn(List.of());

        assertThat(service.findRuns(USER_ID, 0, 20)).isEmpty();
        verifyNoInteractions(jobSearchAuditRepository);
    }

    @Test
    void showsARunsCompaniesInTheOrderTheyWereSearched_withEachSourcesCounts() {
        when(jobSearchRunRepository.findByIdAndUserId("run-1", USER_ID)).thenReturn(Optional.of(RUN));
        when(jobSearchAuditRepository.findCountsByUserIdAndRunIdIn(USER_ID, List.of("run-1")))
                .thenReturn(List.of(GLOBEX, ACME));

        final JobSearchRunDetailDto detail = service.findRun(USER_ID, "run-1").orElseThrow();

        assertThat(detail.run()).isEqualTo(new JobSearchRunSummaryDto("run-1", "2026-10-08", STARTED_AT,
                STARTED_AT.plusSeconds(300), Status.PARTIAL, 2, 4, 2, 1, 1));
        assertThat(detail.companies()).containsExactly(
                new CompanyResult("Acme Corp", STARTED_AT.plusSeconds(10), 4, 1, 1, 0, List.of(
                        new SourceResult("Company careers portal", SourceAudit.Status.SCANNED, 2, 2, 0, 0),
                        new SourceResult("LinkedIn", SourceAudit.Status.SCANNED, 4, 2, 1, 1))),
                new CompanyResult("Globex", STARTED_AT.plusSeconds(120), 0, 1, 0, 1, List.of(
                        new SourceResult("Company careers portal", SourceAudit.Status.SCANNED, 1, 0, 1, 0),
                        new SourceResult("LinkedIn", SourceAudit.Status.FAILED, 0, 0, 0, 0))));
    }

    @Test
    void findsNothing_forARunTheUserDoesNotHave() {
        when(jobSearchRunRepository.findByIdAndUserId(anyString(), eq(USER_ID))).thenReturn(Optional.empty());

        assertThat(service.findRun(USER_ID, "someone-elses")).isEmpty();
        verifyNoInteractions(jobSearchAuditRepository);
    }

    // Counts only, as the repository reads them back: no job lists.
    private static SourceAudit scanned(final String source, final int retrieved, final int filteredOut,
                                       final int saved, final int duplicates) {
        return new SourceAudit(source, SourceAudit.Status.SCANNED, null, retrieved, filteredOut, null,
                saved + duplicates, saved, duplicates, null);
    }
}
