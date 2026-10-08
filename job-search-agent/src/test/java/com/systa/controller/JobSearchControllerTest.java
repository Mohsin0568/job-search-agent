package com.systa.controller;

import com.systa.model.JobListing;
import com.systa.model.UserJobSearchResult;
import com.systa.security.SecurityConfig;
import com.systa.service.JobSearchResultQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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
}
