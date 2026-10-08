package com.systa.job;

import com.systa.MongoTestContainer;
import com.systa.model.CandidateProfile;
import com.systa.model.JobSearchOutcome;
import com.systa.model.UserJobSearchRun;
import com.systa.repository.CandidateProfileRepository;
import com.systa.repository.UserJobSearchRunRepository;
import com.systa.service.JobSearchService;
import com.systa.service.SuggestionSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// The app in job mode against a real Mongo, with the search itself replaced (it calls the LLM and
// paces its calls). Starting the context already runs the job once, with no users; the tests then
// run it again by hand.
@SpringBootTest(properties = {
        "OPEN_API_KEY=test-openai-key",
        "FIRECRAWL_API_KEY=test-firecrawl-key",
        "COGNITO_ISSUER_URI=https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_test",
        "COGNITO_CLIENT_ID=test-client",
        "spring.ai.mcp.client.initialized=false",
        "job-search.daily-run.page-size=1",
        "job-search.daily-run.delay-between-users=0s"
})
@ActiveProfiles("job")
@Import(MongoTestContainer.class)
class JobModeApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DailyJobSearchRunner runner;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private UserJobSearchRunRepository userJobSearchRunRepository;

    @MockitoBean
    private JobSearchService jobSearchService;

    @BeforeEach
    void clear() {
        candidateProfileRepository.deleteAll();
        userJobSearchRunRepository.deleteAll();
    }

    @Test
    void startsWithoutAWebServerOrTheApiOnlyStartupWork() {
        assertThat(context).isNotInstanceOf(WebApplicationContext.class);
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).isEmpty();
        // The autocomplete lists are seeded by the API, not the job.
        assertThat(context.getBeanNamesForType(SuggestionSeeder.class)).isEmpty();
    }

    @Test
    void searchesForEveryUserInMongo_recordsTheirRuns_andSkipsThemOnARerunTheSameDay() {
        final List<CandidateProfile> saved = candidateProfileRepository.saveAll(
                List.of(profile("ada", "Acme Corp"), profile("bob", "Globex"), profile("cy", "Initech")));
        when(jobSearchService.searchJobs(any())).thenReturn(new JobSearchOutcome(3, 0));

        runner.run(null);

        // Page size is 1, so reaching all three means paging through Mongo worked.
        saved.forEach(profile -> verify(jobSearchService).searchJobs(profile));
        assertThat(runner.getExitCode()).isZero();
        assertThat(userJobSearchRunRepository.findAll())
                .extracting(UserJobSearchRun::userId, UserJobSearchRun::status, UserJobSearchRun::batchesAttempted)
                .containsExactlyInAnyOrder(
                        tuple("ada", UserJobSearchRun.Status.SUCCEEDED, 3),
                        tuple("bob", UserJobSearchRun.Status.SUCCEEDED, 3),
                        tuple("cy", UserJobSearchRun.Status.SUCCEEDED, 3));

        clearInvocations(jobSearchService);
        runner.run(null);

        verifyNoInteractions(jobSearchService);
    }

    @Test
    void retriesAUserWhoseSearchFailed_onARerunTheSameDay() {
        final CandidateProfile ada = candidateProfileRepository.save(profile("ada", "Acme Corp"));
        when(jobSearchService.searchJobs(any())).thenReturn(new JobSearchOutcome(3, 3));

        runner.run(null);

        assertThat(runner.getExitCode()).isEqualTo(1);
        when(jobSearchService.searchJobs(any())).thenReturn(new JobSearchOutcome(3, 0));

        runner.run(null);

        verify(jobSearchService, times(2)).searchJobs(ada);
        assertThat(runner.getExitCode()).isZero();
        assertThat(userJobSearchRunRepository.findById("ada")).get()
                .extracting(UserJobSearchRun::status).isEqualTo(UserJobSearchRun.Status.SUCCEEDED);
    }

    private static CandidateProfile profile(final String userId, final String company) {
        return new CandidateProfile(null, userId, userId + "@example.com", "Senior Java Developer",
                List.of("Java"), "Builds APIs", List.of(company), 7);
    }
}
