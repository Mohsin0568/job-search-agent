package com.systa.controller;

import com.systa.exception.JobDescriptionUnavailableException;
import com.systa.model.JobDescription;
import com.systa.model.JobListing;
import com.systa.model.UserJobSearchResult;
import com.systa.model.UserJobSearchResult.Status;
import com.systa.security.EveryoneLetIn;
import com.systa.security.SecurityConfig;
import com.systa.service.JobDescriptionService;
import com.systa.service.JobSearchResultQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JobSearchController.class, properties = {
        "auth.cognito.issuer-uri=https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_test",
        "auth.cognito.client-id=test-client",
        "auth.allowed-origins=http://localhost:5173"
})
@Import({SecurityConfig.class, EveryoneLetIn.class})
class JobSearchControllerTest {

    private static final String USER_ID = "cognito-sub-123";
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";
    private static final JobListing JOB = new JobListing("J-1", "Backend Engineer", "https://acme.example/jobs/1",
            "London", "2026-09-20", null, null, "LinkedIn", 82);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobSearchResultQueryService jobSearchResultQueryService;

    @MockitoBean
    private JobDescriptionService jobDescriptionService;

    @Test
    void getResults_returnsTheTokenSubjectsDashboardJobs_byDefault() throws Exception {
        when(jobSearchResultQueryService.findForUser(USER_ID, Status.ACTIVE, 0, 50)).thenReturn(List.of(
                new UserJobSearchResult("r-1", USER_ID, LocalDateTime.of(2026, 9, 28, 9, 0), "Acme", JOB)));

        mockMvc.perform(get("/api/jobs/results").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyName").value("Acme"))
                .andExpect(jsonPath("$[0].job.jobTitle").value("Backend Engineer"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].statusChangedAt").isEmpty());
    }

    @Test
    void getResults_withAStatus_returnsTheJobsMarkedWithIt() throws Exception {
        when(jobSearchResultQueryService.findForUser(USER_ID, Status.NOT_RELEVANT, 0, 50)).thenReturn(List.of(
                new UserJobSearchResult("r-1", USER_ID, LocalDateTime.of(2026, 9, 28, 9, 0), "Acme", JOB,
                        Status.NOT_RELEVANT, Instant.parse("2026-10-20T09:00:00Z"), "Too senior")));

        mockMvc.perform(get("/api/jobs/results").param("status", "NOT_RELEVANT")
                        .with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("NOT_RELEVANT"))
                .andExpect(jsonPath("$[0].statusChangedAt").value("2026-10-20T09:00:00Z"))
                .andExpect(jsonPath("$[0].notRelevantReason").value("Too senior"));
    }

    @Test
    void getResults_withAnUnknownStatus_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/jobs/results").param("status", "ARCHIVED")
                        .with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobSearchResultQueryService);
    }

    @Test
    void getResults_capsPageSize() throws Exception {
        mockMvc.perform(get("/api/jobs/results")
                        .param("page", "-3")
                        .param("size", "1000")
                        .with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk());

        verify(jobSearchResultQueryService).findForUser(USER_ID, Status.ACTIVE, 0, 100);
    }

    @Test
    void getResults_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/jobs/results"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jobSearchResultQueryService);
    }

    @Test
    void corsPreflight_fromFrontendOrigin_isAllowed() throws Exception {
        mockMvc.perform(options("/api/jobs/results")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN));
    }

    @Test
    void corsPreflight_fromUnknownOrigin_isRejected() throws Exception {
        mockMvc.perform(options("/api/jobs/results")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDescription_returnsWhatTheTokenSubjectsJobAsksFor() throws Exception {
        when(jobDescriptionService.getOrFetch(USER_ID, "r-1")).thenReturn(Optional.of(new JobDescription("r-1", USER_ID,
                "https://acme.example/jobs/1", "Builds payment APIs.", List.of("Java", "Spring Boot"),
                Instant.parse("2026-10-13T09:00:00Z"))));

        mockMvc.perform(post("/api/jobs/results/r-1/description").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobResultId").value("r-1"))
                .andExpect(jsonPath("$.description").value("Builds payment APIs."))
                .andExpect(jsonPath("$.requiredSkills[1]").value("Spring Boot"))
                .andExpect(jsonPath("$.fetchedAt").value("2026-10-13T09:00:00Z"))
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.sourceUrl").doesNotExist());
    }

    @Test
    void getDescription_forAJobTheUserDoesNotHave_isNotFound() throws Exception {
        when(jobDescriptionService.getOrFetch(USER_ID, "someone-elses")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/jobs/results/someone-elses/description")
                        .with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDescription_whenThePostingCannotBeRead_isUnprocessable_withTheReason() throws Exception {
        when(jobDescriptionService.getOrFetch(USER_ID, "r-1")).thenThrow(new JobDescriptionUnavailableException(
                "we couldn't find this job posting - it may have been removed or need a sign-in to view"));

        mockMvc.perform(post("/api/jobs/results/r-1/description").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(
                        "we couldn't find this job posting - it may have been removed or need a sign-in to view"));
    }

    @Test
    void getDescription_withoutAToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/jobs/results/r-1/description"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jobDescriptionService);
    }

    @Test
    void updateStatus_marksTheTokenSubjectsJob_andReturnsIt() throws Exception {
        when(jobSearchResultQueryService.updateStatus(USER_ID, "r-1", Status.APPLIED, null)).thenReturn(Optional.of(
                new UserJobSearchResult("r-1", USER_ID, LocalDateTime.of(2026, 9, 28, 9, 0), "Acme", JOB,
                        Status.APPLIED, Instant.parse("2026-10-20T09:00:00Z"), null)));

        mockMvc.perform(put("/api/jobs/results/r-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPLIED\"}")
                        .with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("r-1"))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.statusChangedAt").value("2026-10-20T09:00:00Z"));
    }

    @Test
    void updateStatus_forAJobTheUserDoesNotHave_isNotFound() throws Exception {
        when(jobSearchResultQueryService.updateStatus(USER_ID, "someone-elses", Status.ACTIVE, null))
                .thenReturn(Optional.empty());

        mockMvc.perform(put("/api/jobs/results/someone-elses/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}")
                        .with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_notRelevant_passesOnTheUsersReason() throws Exception {
        when(jobSearchResultQueryService.updateStatus(USER_ID, "r-1", Status.NOT_RELEVANT, "Needs Kubernetes"))
                .thenReturn(Optional.of(new UserJobSearchResult("r-1", USER_ID, LocalDateTime.of(2026, 9, 28, 9, 0),
                        "Acme", JOB, Status.NOT_RELEVANT, Instant.parse("2026-10-20T09:00:00Z"), "Needs Kubernetes")));

        mockMvc.perform(put("/api/jobs/results/r-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOT_RELEVANT\",\"reason\":\"Needs Kubernetes\"}")
                        .with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_RELEVANT"))
                .andExpect(jsonPath("$.notRelevantReason").value("Needs Kubernetes"));
    }

    @Test
    void updateStatus_withAReasonThatIsTooLong_isBadRequest() throws Exception {
        mockMvc.perform(put("/api/jobs/results/r-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOT_RELEVANT\",\"reason\":\"" + "x".repeat(501) + "\"}")
                        .with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("reason: size must be between 0 and 500"));

        verifyNoInteractions(jobSearchResultQueryService);
    }

    @Test
    void updateStatus_withoutAStatus_isBadRequest() throws Exception {
        mockMvc.perform(put("/api/jobs/results/r-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("status: must not be null"));

        verifyNoInteractions(jobSearchResultQueryService);
    }

    @Test
    void updateStatus_withAnUnknownStatus_isBadRequest() throws Exception {
        mockMvc.perform(put("/api/jobs/results/r-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}")
                        .with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobSearchResultQueryService);
    }

    @Test
    void updateStatus_withoutAToken_isUnauthorized() throws Exception {
        mockMvc.perform(put("/api/jobs/results/r-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPLIED\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jobSearchResultQueryService);
    }
}
