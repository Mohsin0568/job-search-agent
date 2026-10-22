package com.systa.controller;

import com.systa.model.JobSearchAudit.SourceAudit;
import com.systa.model.JobSearchRunDetailDto;
import com.systa.model.JobSearchRunDetailDto.CompanyResult;
import com.systa.model.JobSearchRunDetailDto.SourceResult;
import com.systa.model.JobSearchRunSummaryDto;
import com.systa.model.UserJobSearchRun.Status;
import com.systa.security.EveryoneLetIn;
import com.systa.security.SecurityConfig;
import com.systa.service.JobSearchHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JobSearchHistoryController.class, properties = {
        "auth.cognito.issuer-uri=https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_test",
        "auth.cognito.client-id=test-client",
        "auth.allowed-origins=http://localhost:5173"
})
@Import({SecurityConfig.class, EveryoneLetIn.class})
class JobSearchHistoryControllerTest {

    private static final String USER_ID = "cognito-sub-123";
    private static final Instant STARTED_AT = Instant.parse("2026-10-08T02:00:00Z");
    private static final JobSearchRunSummaryDto RUN = new JobSearchRunSummaryDto("run-1", "2026-10-08", STARTED_AT,
            STARTED_AT.plusSeconds(300), Status.PARTIAL, 1, 2, 1, 1, 1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobSearchHistoryService jobSearchHistoryService;

    @Test
    void getRuns_listsTheUsersRuns_withTheirTotals() throws Exception {
        when(jobSearchHistoryService.findRuns(USER_ID, 0, 20)).thenReturn(List.of(RUN));

        mockMvc.perform(get("/api/jobs/search-runs").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runId").value("run-1"))
                .andExpect(jsonPath("$[0].runDate").value("2026-10-08"))
                .andExpect(jsonPath("$[0].startedAt").value("2026-10-08T02:00:00Z"))
                .andExpect(jsonPath("$[0].finishedAt").value("2026-10-08T02:05:00Z"))
                .andExpect(jsonPath("$[0].status").value("PARTIAL"))
                .andExpect(jsonPath("$[0].companiesSearched").value(1))
                .andExpect(jsonPath("$[0].newJobs").value(2))
                .andExpect(jsonPath("$[0].duplicateJobs").value(1))
                .andExpect(jsonPath("$[0].filteredOutJobs").value(1))
                .andExpect(jsonPath("$[0].sourcesFailed").value(1));
    }

    @Test
    void getRuns_keepsThePageAndSizeInBounds() throws Exception {
        when(jobSearchHistoryService.findRuns(USER_ID, 0, 100)).thenReturn(List.of());

        mockMvc.perform(get("/api/jobs/search-runs").param("page", "-1").param("size", "5000")
                        .with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk());

        verify(jobSearchHistoryService).findRuns(USER_ID, 0, 100);
    }

    @Test
    void getRun_showsEachCompanysSources() throws Exception {
        when(jobSearchHistoryService.findRun(USER_ID, "run-1")).thenReturn(Optional.of(new JobSearchRunDetailDto(RUN,
                List.of(new CompanyResult("Acme Corp", STARTED_AT.plusSeconds(10), 2, 1, 1, 1, List.of(
                        new SourceResult("LinkedIn", SourceAudit.Status.SCANNED, 4, 2, 1, 1),
                        new SourceResult("Company careers portal", SourceAudit.Status.FAILED, 0, 0, 0, 0)))))));

        mockMvc.perform(get("/api/jobs/search-runs/run-1").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.runId").value("run-1"))
                .andExpect(jsonPath("$.companies[0].companyName").value("Acme Corp"))
                .andExpect(jsonPath("$.companies[0].newJobs").value(2))
                .andExpect(jsonPath("$.companies[0].sources[0].source").value("LinkedIn"))
                .andExpect(jsonPath("$.companies[0].sources[0].jobsFound").value(4))
                .andExpect(jsonPath("$.companies[0].sources[0].newJobs").value(2))
                .andExpect(jsonPath("$.companies[0].sources[0].duplicateJobs").value(1))
                .andExpect(jsonPath("$.companies[0].sources[0].filteredOutJobs").value(1))
                .andExpect(jsonPath("$.companies[0].sources[1].status").value("FAILED"))
                // The recorded failure reason is technical, so it is never sent.
                .andExpect(jsonPath("$.companies[0].sources[1].failureReason").doesNotExist());
    }

    @Test
    void getRun_isNotFound_whenTheUserHasNoSuchRun() throws Exception {
        when(jobSearchHistoryService.findRun(USER_ID, "someone-elses")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/jobs/search-runs/someone-elses").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresSignIn() throws Exception {
        mockMvc.perform(get("/api/jobs/search-runs")).andExpect(status().isUnauthorized());

        verifyNoInteractions(jobSearchHistoryService);
    }
}
