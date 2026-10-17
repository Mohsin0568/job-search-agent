package com.systa.service;

import com.systa.exception.JobSearchParseException;
import com.systa.model.CandidateProfile;
import com.systa.model.CompanySearchResult;
import com.systa.model.FilteredOutJob;
import com.systa.model.JobFilterResult;
import com.systa.model.JobListing;
import com.systa.model.JobPersistResult;
import com.systa.model.JobSearchAudit;
import com.systa.model.JobSearchAudit.SourceAudit;
import com.systa.model.JobSearchOutcome;
import com.systa.model.JobSource;
import com.systa.repository.JobSearchAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSearchServiceTest {

    private static final String USER_ID = "user-1";
    // Worked out from the sources currently switched on, so the tests hold as sources come and go.
    private static final int SOURCE_COUNT = JobSource.values().length;
    private static final int SOURCES_PER_BATCH = 2;
    private static final int BATCHES_PER_COMPANY = (SOURCE_COUNT + SOURCES_PER_BATCH - 1) / SOURCES_PER_BATCH;

    @Mock
    private JobSearchLlmService jobSearchLlmService;

    @Mock
    private JobSearchResultFilterService jobSearchResultFilterService;

    @Mock
    private JobSearchResultPersistenceService jobSearchResultPersistenceService;

    @Mock
    private JobSearchAuditRepository jobSearchAuditRepository;

    @Captor
    private ArgumentCaptor<List<String>> sourcesCaptor;

    @Captor
    private ArgumentCaptor<JobSearchAudit> auditCaptor;

    private JobSearchService service;

    @BeforeEach
    void setUp() {
        // No pause between batch calls, so the tests don't wait on the rate-limit spacing.
        service = new JobSearchService(jobSearchLlmService, jobSearchResultFilterService,
                jobSearchResultPersistenceService, new JobSearchAuditService(jobSearchAuditRepository),
                Duration.ZERO);

        // By default the LLM finds one job per batch, the filter keeps everything and all of it is new.
        lenient().when(jobSearchLlmService.searchJobsForCompanyBatch(anyString(), any(), anyString(), anyList()))
                .thenAnswer(call -> found(call.getArgument(2), call.<List<String>>getArgument(3)));
        lenient().when(jobSearchResultFilterService.filterStaleJobs(anyString(), any(), anyList(), any()))
                .thenAnswer(call -> new JobFilterResult(call.<CompanySearchResult>getArgument(3).jobs(), List.of()));
        lenient().when(jobSearchResultPersistenceService.persist(anyString(), any()))
                .thenAnswer(call -> new JobPersistResult(call.<CompanySearchResult>getArgument(1).jobs(), List.of(),
                        List.of()));
    }

    @Test
    void searchesEverySourceOnceForEachPreferredCompany_inSmallBatches() {
        final CandidateProfile profile = profile("Acme Corp", "Globex");

        final JobSearchOutcome outcome = service.searchJobs(profile);

        assertThat(outcome).isEqualTo(new JobSearchOutcome(2 * BATCHES_PER_COMPANY, 0, List.of()));

        for (final String company : List.of("Acme Corp", "Globex")) {
            verify(jobSearchLlmService, times(BATCHES_PER_COMPANY))
                    .searchJobsForCompanyBatch(eq(USER_ID), eq(profile), eq(company), sourcesCaptor.capture());
        }
        final List<List<String>> batches = sourcesCaptor.getAllValues();
        assertThat(batches).hasSize(2 * BATCHES_PER_COMPANY)
                .allSatisfy(batch -> assertThat(batch).hasSizeBetween(1, SOURCES_PER_BATCH));
        // Each company gets the same batches, which between them cover every source once.
        assertThat(batches.subList(0, BATCHES_PER_COMPANY))
                .isEqualTo(batches.subList(BATCHES_PER_COMPANY, 2 * BATCHES_PER_COMPANY));
        assertThat(batches.subList(0, BATCHES_PER_COMPANY).stream().flatMap(List::stream))
                .hasSize(SOURCE_COUNT)
                .doesNotHaveDuplicates()
                .anySatisfy(source -> assertThat(source).contains("official careers"))
                .anySatisfy(source -> assertThat(source).contains("LinkedIn"));
    }

    @Test
    void searchesCompaniesInTheUsersPreferredOrder() {
        service.searchJobs(profile("Globex", "Acme Corp"));

        final InOrder inOrder = inOrder(jobSearchLlmService);
        inOrder.verify(jobSearchLlmService, times(BATCHES_PER_COMPANY))
                .searchJobsForCompanyBatch(any(), any(), eq("Globex"), anyList());
        inOrder.verify(jobSearchLlmService, times(BATCHES_PER_COMPANY))
                .searchJobsForCompanyBatch(any(), any(), eq("Acme Corp"), anyList());
    }

    @Test
    void filtersEachBatchWithTheProfilesRecencyWindow_thenSavesWhatIsLeft() {
        final JobListing fresh = job("fresh", "LinkedIn");
        // doReturn, so re-stubbing doesn't run setUp's answer with the matchers' null arguments.
        doReturn(new JobFilterResult(List.of(fresh), List.of()))
                .when(jobSearchResultFilterService).filterStaleJobs(anyString(), any(), anyList(), any());

        service.searchJobs(profile(30, List.of("Acme Corp")));

        final ArgumentCaptor<CompanySearchResult> unfiltered = ArgumentCaptor.forClass(CompanySearchResult.class);
        verify(jobSearchResultFilterService, times(BATCHES_PER_COMPANY))
                .filterStaleJobs(eq(USER_ID), eq(30), anyList(), unfiltered.capture());
        assertThat(unfiltered.getAllValues()).allSatisfy(batchResult -> {
            assertThat(batchResult.companyName()).isEqualTo("Acme Corp");
            assertThat(batchResult.jobs()).hasSize(1);
        });
        verify(jobSearchResultPersistenceService, times(BATCHES_PER_COMPANY)).persist(USER_ID,
                new CompanySearchResult("Acme Corp", List.of(fresh)));
    }

    @Test
    void passesOnAMissingRecencyWindow_forTheFilterToDefault() {
        service.searchJobs(profile(null, List.of("Acme Corp")));

        verify(jobSearchResultFilterService, times(BATCHES_PER_COMPANY))
                .filterStaleJobs(eq(USER_ID), eq(null), anyList(), any());
    }

    @Test
    void savesEachBatchBeforeSearchingTheNext_soAnEarlierResultSurvivesALaterFailure() {
        service.searchJobs(profile("Acme Corp"));

        final InOrder inOrder = inOrder(jobSearchLlmService, jobSearchResultPersistenceService);
        for (int batch = 0; batch < BATCHES_PER_COMPANY; batch++) {
            inOrder.verify(jobSearchLlmService).searchJobsForCompanyBatch(any(), any(), any(), anyList());
            inOrder.verify(jobSearchResultPersistenceService).persist(eq(USER_ID), any());
        }
    }

    @Test
    void carriesOnWithTheOtherBatchesAndCompanies_whenOneSearchFails_andCountsTheFailures() {
        doThrow(new IllegalStateException("429 rate limit, retries exhausted"))
                .doThrow(new JobSearchParseException("Failed to parse job search response from LLM", null))
                .doAnswer(call -> found(call.getArgument(2), call.<List<String>>getArgument(3)))
                .when(jobSearchLlmService).searchJobsForCompanyBatch(anyString(), any(), anyString(), anyList());

        final JobSearchOutcome outcome = service.searchJobs(profile("Acme Corp", "Globex", "Initech"));

        assertThat(outcome).isEqualTo(new JobSearchOutcome(3 * BATCHES_PER_COMPANY, 2, List.of()));
        assertThat(outcome.someBatchesFailed()).isTrue();
        assertThat(outcome.allBatchesFailed()).isFalse();
        verify(jobSearchLlmService, times(3 * BATCHES_PER_COMPANY))
                .searchJobsForCompanyBatch(anyString(), any(), anyString(), anyList());
        verify(jobSearchResultPersistenceService, times(3 * BATCHES_PER_COMPANY - 2)).persist(eq(USER_ID), any());
    }

    @Test
    void carriesOn_whenSavingABatchFails() {
        doThrow(new IllegalStateException("Mongo unavailable"))
                .doReturn(new JobPersistResult(List.of(), List.of(), List.of()))
                .when(jobSearchResultPersistenceService).persist(eq(USER_ID), any());

        final JobSearchOutcome outcome = service.searchJobs(profile("Acme Corp"));

        assertThat(outcome).isEqualTo(new JobSearchOutcome(BATCHES_PER_COMPANY, 1, List.of()));
        verify(jobSearchResultPersistenceService, times(BATCHES_PER_COMPANY)).persist(eq(USER_ID), any());
    }

    @Test
    void reportsEveryBatchAsFailed_whenNoSearchGetsThrough() {
        doThrow(new IllegalStateException("429 rate limit, retries exhausted"))
                .when(jobSearchLlmService).searchJobsForCompanyBatch(anyString(), any(), anyString(), anyList());

        final JobSearchOutcome outcome = service.searchJobs(profile("Acme Corp"));

        assertThat(outcome).isEqualTo(new JobSearchOutcome(BATCHES_PER_COMPANY, BATCHES_PER_COMPANY, List.of()));
        assertThat(outcome.allBatchesFailed()).isTrue();
        verifyNoInteractions(jobSearchResultPersistenceService);
    }

    @Test
    void recordsAnAuditForEachCompany_countingEachSourcesJobsThroughThePipeline() {
        final JobListing portalJob = job("portal", "Company portal");
        final JobListing linkedInSaved = job("linkedin-saved", "LinkedIn");
        final JobListing linkedInDuplicate = job("linkedin-duplicate", "LinkedIn");
        final JobListing linkedInStale = job("linkedin-stale", "LinkedIn");
        final FilteredOutJob stale = new FilteredOutJob(linkedInStale, FilteredOutJob.Reason.OUTSIDE_RECENCY_WINDOW);
        // The first batch (company portal + LinkedIn) finds four jobs, any other batch finds nothing.
        doReturn(new CompanySearchResult("Acme Corp",
                        List.of(portalJob, linkedInSaved, linkedInDuplicate, linkedInStale)))
                .doReturn(new CompanySearchResult("Acme Corp", List.of()))
                .when(jobSearchLlmService).searchJobsForCompanyBatch(anyString(), any(), anyString(), anyList());
        doReturn(new JobFilterResult(List.of(portalJob, linkedInSaved, linkedInDuplicate), List.of(stale)))
                .doReturn(new JobFilterResult(List.of(), List.of()))
                .when(jobSearchResultFilterService).filterStaleJobs(anyString(), any(), anyList(), any());
        doReturn(new JobPersistResult(List.of(portalJob, linkedInSaved), List.of(linkedInDuplicate),
                List.of("result-portal", "result-linkedin")))
                .doReturn(new JobPersistResult(List.of(), List.of(), List.of()))
                .when(jobSearchResultPersistenceService).persist(anyString(), any());

        service.searchJobs(profile("Acme Corp"));

        verify(jobSearchAuditRepository).save(auditCaptor.capture());
        final JobSearchAudit audit = auditCaptor.getValue();
        assertThat(audit.userId()).isEqualTo(USER_ID);
        assertThat(audit.companyName()).isEqualTo("Acme Corp");
        assertThat(audit.sourcesScanned()).isEqualTo(SOURCE_COUNT);
        assertThat(audit.sourcesFailed()).isZero();
        assertThat(audit.sources()).extracting(SourceAudit::source)
                .containsExactlyElementsOf(Arrays.stream(JobSource.values()).map(JobSource::displayName).toList());
        assertThat(audit.sources().get(0)).isEqualTo(
                new SourceAudit("Company careers portal", SourceAudit.Status.SCANNED, null, 1, 0, List.of(), 1, 1, 0,
                        List.of()));
        assertThat(audit.sources().get(1)).isEqualTo(
                new SourceAudit("LinkedIn", SourceAudit.Status.SCANNED, null, 3, 1, List.of(stale), 2, 1, 1,
                        List.of(linkedInDuplicate)));
        assertThat(audit.sources().subList(2, SOURCE_COUNT)).allSatisfy(source -> {
            assertThat(source.status()).isEqualTo(SourceAudit.Status.SCANNED);
            assertThat(source.jobsRetrieved()).isZero();
        });
    }

    @Test
    void auditsTheSourcesOfAFailedBatchAsFailed_withTheReason() {
        doThrow(new IllegalStateException("429 rate limit, retries exhausted"))
                .doAnswer(call -> found(call.getArgument(2), call.<List<String>>getArgument(3)))
                .when(jobSearchLlmService).searchJobsForCompanyBatch(anyString(), any(), anyString(), anyList());

        service.searchJobs(profile("Acme Corp"));

        verify(jobSearchAuditRepository).save(auditCaptor.capture());
        final JobSearchAudit audit = auditCaptor.getValue();
        assertThat(audit.sourcesScanned()).isEqualTo(SOURCE_COUNT - 2);
        assertThat(audit.sourcesFailed()).isEqualTo(2);
        assertThat(audit.sources().subList(0, 2)).allSatisfy(source -> {
            assertThat(source.status()).isEqualTo(SourceAudit.Status.FAILED);
            assertThat(source.failureReason()).contains("429 rate limit");
            assertThat(source.jobsRetrieved()).isZero();
        });
    }

    @Test
    void returnsTheIdsOfEveryJobItSaved_inTheOrderItSavedThem_skippingFailedBatches() {
        doReturn(new JobPersistResult(List.of(), List.of(), List.of("acme-1", "acme-2")))
                .when(jobSearchResultPersistenceService).persist(anyString(), argThat(forCompany("Acme Corp")));
        doReturn(new JobPersistResult(List.of(), List.of(), List.of("globex-1")))
                .when(jobSearchResultPersistenceService).persist(anyString(), argThat(forCompany("Globex")));
        // Initech's search fails, so nothing of it is saved.
        doThrow(new IllegalStateException("429 rate limit, retries exhausted"))
                .when(jobSearchLlmService).searchJobsForCompanyBatch(anyString(), any(), eq("Initech"), anyList());

        final JobSearchOutcome outcome = service.searchJobs(profile("Acme Corp", "Initech", "Globex"));

        assertThat(outcome.batchesFailed()).isEqualTo(BATCHES_PER_COMPANY);
        final List<String> expected = new ArrayList<>();
        for (int batch = 0; batch < BATCHES_PER_COMPANY; batch++) {
            expected.addAll(List.of("acme-1", "acme-2"));
        }
        for (int batch = 0; batch < BATCHES_PER_COMPANY; batch++) {
            expected.add("globex-1");
        }
        assertThat(outcome.newJobResultIds()).containsExactlyElementsOf(expected);
    }

    @Test
    void carriesOn_whenRecordingTheAuditFails() {
        when(jobSearchAuditRepository.save(any())).thenThrow(new IllegalStateException("Mongo unavailable"));

        final JobSearchOutcome outcome = service.searchJobs(profile("Acme Corp", "Globex"));

        assertThat(outcome).isEqualTo(new JobSearchOutcome(2 * BATCHES_PER_COMPANY, 0, List.of()));
        verify(jobSearchAuditRepository, times(2)).save(any());
    }

    private static ArgumentMatcher<CompanySearchResult> forCompany(final String companyName) {
        return result -> result != null && companyName.equals(result.companyName());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void searchesNothing_whenTheProfileNamesNoCompanies(final List<String> companyPreferences) {
        final JobSearchOutcome outcome = service.searchJobs(profile(7, companyPreferences));

        assertThat(outcome.nothingToSearch()).isTrue();
        assertThat(outcome.allBatchesFailed()).isFalse();
        verifyNoInteractions(jobSearchLlmService, jobSearchResultFilterService, jobSearchResultPersistenceService,
                jobSearchAuditRepository);
    }

    private static CandidateProfile profile(final String... companyPreferences) {
        return profile(7, List.of(companyPreferences));
    }

    private static CandidateProfile profile(final Integer recencyWindowDays, final List<String> companyPreferences) {
        return new CandidateProfile("profile-1", USER_ID, "ada@example.com", "Senior Java Developer",
                List.of("Java", "Spring Boot"), "Builds APIs", companyPreferences, recencyWindowDays);
    }

    private static JobListing job(final String title, final String source) {
        return new JobListing("J-" + title, title, "https://example.com/" + title, "London, UK", "17 Aug 2026",
                "Not specified", "Not specified", source, 85);
    }

    private static CompanySearchResult found(final String company, final List<String> sources) {
        return new CompanySearchResult(company, List.of(new JobListing("J-" + sources.hashCode(), "Backend Engineer",
                "https://example.com/job", "London, UK", "17 Aug 2026", "Not specified", "Not specified",
                sources.get(0), 85)));
    }
}
