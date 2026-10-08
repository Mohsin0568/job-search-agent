package com.systa.service;

import com.systa.model.CandidateProfile;
import com.systa.model.CompanySearchResult;
import com.systa.model.JobListing;
import com.systa.model.JobSearchOutcome;
import com.systa.model.JobSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private static final int BATCHES_PER_COMPANY = 3;

    @Mock
    private JobSearchLlmService jobSearchLlmService;

    @Mock
    private JobSearchResultFilterService jobSearchResultFilterService;

    @Mock
    private JobSearchResultPersistenceService jobSearchResultPersistenceService;

    @Captor
    private ArgumentCaptor<List<String>> sourcesCaptor;

    private JobSearchService service;

    @BeforeEach
    void setUp() {
        // No pause between batch calls, so the tests don't wait on the rate-limit spacing.
        service = new JobSearchService(jobSearchLlmService, jobSearchResultFilterService,
                jobSearchResultPersistenceService, Duration.ZERO);

        // By default the LLM finds one job per batch and the filter keeps everything.
        lenient().when(jobSearchLlmService.searchJobsForCompanyBatch(anyString(), any(), anyString(), anyList()))
                .thenAnswer(call -> found(call.getArgument(2), call.<List<String>>getArgument(3)));
        lenient().when(jobSearchResultFilterService.filterStaleJobs(anyString(), any(), any()))
                .thenAnswer(call -> call.getArgument(2));
    }

    @Test
    void searchesEverySourceOnceForEachPreferredCompany_inSmallBatches() {
        final CandidateProfile profile = profile("Acme Corp", "Globex");

        final JobSearchOutcome outcome = service.searchJobs(profile);

        assertThat(outcome).isEqualTo(new JobSearchOutcome(2 * BATCHES_PER_COMPANY, 0));

        for (final String company : List.of("Acme Corp", "Globex")) {
            verify(jobSearchLlmService, times(BATCHES_PER_COMPANY))
                    .searchJobsForCompanyBatch(eq(USER_ID), eq(profile), eq(company), sourcesCaptor.capture());
        }
        final List<List<String>> batches = sourcesCaptor.getAllValues();
        assertThat(batches).hasSize(2 * BATCHES_PER_COMPANY).allSatisfy(batch -> assertThat(batch).hasSize(2));
        // Each company gets the same batches, which between them cover six different sources.
        assertThat(batches.subList(0, BATCHES_PER_COMPANY)).isEqualTo(batches.subList(BATCHES_PER_COMPANY, 6));
        assertThat(batches.subList(0, BATCHES_PER_COMPANY).stream().flatMap(List::stream))
                .hasSize(6)
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
        final JobSearchResponse fresh = new JobSearchResponse(List.of(new CompanySearchResult("Acme Corp", List.of())));
        when(jobSearchResultFilterService.filterStaleJobs(anyString(), any(), any())).thenReturn(fresh);

        service.searchJobs(profile(30, List.of("Acme Corp")));

        final ArgumentCaptor<JobSearchResponse> unfiltered = ArgumentCaptor.forClass(JobSearchResponse.class);
        verify(jobSearchResultFilterService, times(BATCHES_PER_COMPANY))
                .filterStaleJobs(eq(USER_ID), eq(30), unfiltered.capture());
        assertThat(unfiltered.getAllValues()).allSatisfy(response -> {
            assertThat(response.companies()).hasSize(1);
            assertThat(response.companies().get(0).companyName()).isEqualTo("Acme Corp");
            assertThat(response.companies().get(0).jobs()).hasSize(1);
        });
        verify(jobSearchResultPersistenceService, times(BATCHES_PER_COMPANY)).persist(USER_ID, fresh);
    }

    @Test
    void passesOnAMissingRecencyWindow_forTheFilterToDefault() {
        service.searchJobs(profile(null, List.of("Acme Corp")));

        verify(jobSearchResultFilterService, times(BATCHES_PER_COMPANY)).filterStaleJobs(eq(USER_ID), eq(null), any());
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

        final JobSearchOutcome outcome = service.searchJobs(profile("Acme Corp", "Globex"));

        assertThat(outcome).isEqualTo(new JobSearchOutcome(2 * BATCHES_PER_COMPANY, 2));
        assertThat(outcome.someBatchesFailed()).isTrue();
        assertThat(outcome.allBatchesFailed()).isFalse();
        verify(jobSearchLlmService, times(2 * BATCHES_PER_COMPANY))
                .searchJobsForCompanyBatch(anyString(), any(), anyString(), anyList());
        verify(jobSearchResultPersistenceService, times(2 * BATCHES_PER_COMPANY - 2)).persist(eq(USER_ID), any());
    }

    @Test
    void carriesOn_whenSavingABatchFails() {
        doThrow(new IllegalStateException("Mongo unavailable")).doNothing()
                .when(jobSearchResultPersistenceService).persist(eq(USER_ID), any());

        final JobSearchOutcome outcome = service.searchJobs(profile("Acme Corp"));

        assertThat(outcome).isEqualTo(new JobSearchOutcome(BATCHES_PER_COMPANY, 1));
        verify(jobSearchResultPersistenceService, times(BATCHES_PER_COMPANY)).persist(eq(USER_ID), any());
    }

    @Test
    void reportsEveryBatchAsFailed_whenNoSearchGetsThrough() {
        doThrow(new IllegalStateException("429 rate limit, retries exhausted"))
                .when(jobSearchLlmService).searchJobsForCompanyBatch(anyString(), any(), anyString(), anyList());

        final JobSearchOutcome outcome = service.searchJobs(profile("Acme Corp"));

        assertThat(outcome).isEqualTo(new JobSearchOutcome(BATCHES_PER_COMPANY, BATCHES_PER_COMPANY));
        assertThat(outcome.allBatchesFailed()).isTrue();
        verifyNoInteractions(jobSearchResultPersistenceService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void searchesNothing_whenTheProfileNamesNoCompanies(final List<String> companyPreferences) {
        final JobSearchOutcome outcome = service.searchJobs(profile(7, companyPreferences));

        assertThat(outcome.nothingToSearch()).isTrue();
        assertThat(outcome.allBatchesFailed()).isFalse();
        verifyNoInteractions(jobSearchLlmService, jobSearchResultFilterService, jobSearchResultPersistenceService);
    }

    private static CandidateProfile profile(final String... companyPreferences) {
        return profile(7, List.of(companyPreferences));
    }

    private static CandidateProfile profile(final Integer recencyWindowDays, final List<String> companyPreferences) {
        return new CandidateProfile("profile-1", USER_ID, "ada@example.com", "Senior Java Developer",
                List.of("Java", "Spring Boot"), "Builds APIs", companyPreferences, recencyWindowDays);
    }

    private static CompanySearchResult found(final String company, final List<String> sources) {
        return new CompanySearchResult(company, List.of(new JobListing("J-" + sources.hashCode(), "Backend Engineer",
                "https://example.com/job", "London, UK", "17 Aug 2026", "Not specified", "Not specified",
                sources.get(0), 85)));
    }
}
