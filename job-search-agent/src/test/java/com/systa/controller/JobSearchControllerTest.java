package com.systa.controller;

import com.systa.exception.JobDescriptionUnavailableException;
import com.systa.model.JobDescription;
import com.systa.model.JobListing;
import com.systa.model.UserJobSearchResult;
import com.systa.security.SecurityConfig;
import com.systa.service.JobDescriptionService;
import com.systa.service.JobSearchResultQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JobSearchController.class, properties = {
        "auth.cognito.issuer-uri=https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_test",
        "auth.cognito.client-id=test-client",
        "auth.allowed-origins=http://localhost:5173"
})
@Import(SecurityConfig.class)
class JobSearchControllerTest {

    private static final String USER_ID = "cognito-sub-123";
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobSearchResultQueryService jobSearchResultQueryService;

    @MockitoBean
    private JobDescriptionService jobDescriptionService;

    @Test
    void getResults_returnsResultsForTokenSubject() throws Exception {
        final JobListing job = new JobListing("J-1", "Backend Engineer", "https://acme.example/jobs/1",
                "London", "2026-09-20", null, null, "LinkedIn", 82);
        when(jobSearchResultQueryService.findForUser(USER_ID, 0, 50)).thenReturn(List.of(
                new UserJobSearchResult("r-1", USER_ID, LocalDateTime.of(2026, 9, 28, 9, 0), "Acme", job)));

        mockMvc.perform(get("/api/jobs/results").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].companyName").value("Acme"))
                .andExpect(jsonPath("$[0].job.jobTitle").value("Backend Engineer"));
    }

    @Test
    void getResults_capsPageSize() throws Exception {
        mockMvc.perform(get("/api/jobs/results")
                        .param("page", "-3")
                        .param("size", "1000")
                        .with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk());

        verify(jobSearchResultQueryService).findForUser(USER_ID, 0, 100);
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
}
