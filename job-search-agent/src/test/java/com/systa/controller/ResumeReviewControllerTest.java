package com.systa.controller;

import com.systa.exception.ResumeRequiredException;
import com.systa.model.ResumeReview;
import com.systa.model.ResumeReview.RedFlag;
import com.systa.model.ResumeReview.Status;
import com.systa.model.ResumeReviewResult;
import com.systa.security.SecurityConfig;
import com.systa.service.ResumeReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ResumeReviewController.class, properties = {
        "auth.cognito.issuer-uri=https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_test",
        "auth.cognito.client-id=test-client",
        "auth.allowed-origins=http://localhost:5173"
})
@Import(SecurityConfig.class)
class ResumeReviewControllerTest {

    private static final String USER_ID = "cognito-sub-123";
    private static final String URL = "/api/jobs/results/r-1/resume-review";
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-15T09:00:00Z");

    private static final ResumeReview PENDING = new ResumeReview("r-1", USER_ID, Status.PENDING, 3, null, null, null,
            null, REQUESTED_AT, null);
    private static final ResumeReview READY = PENDING.ready(new ResumeReviewResult(64, List.of("Kubernetes"),
            List.of(new RedFlag("No measurable results", "Bullets list duties, not outcomes."))),
            REQUESTED_AT.plusSeconds(30));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResumeReviewService resumeReviewService;

    @Test
    void request_whenAReviewStarts_isAccepted_withThePendingStatus() throws Exception {
        when(resumeReviewService.request(USER_ID, "r-1")).thenReturn(Optional.of(PENDING));

        mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobResultId").value("r-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.resumeVersion").value(3))
                .andExpect(jsonPath("$.requestedAt").value("2026-10-15T09:00:00Z"))
                .andExpect(jsonPath("$.resumeAtsScore").doesNotExist());
    }

    @Test
    void request_whenOneIsAlreadyReady_isOk_withWhatItFound_butNotWhoseItIs() throws Exception {
        when(resumeReviewService.request(USER_ID, "r-1")).thenReturn(Optional.of(READY));

        mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.resumeAtsScore").value(64))
                .andExpect(jsonPath("$.missingKeywords[0]").value("Kubernetes"))
                .andExpect(jsonPath("$.redFlags[0].issue").value("No measurable results"))
                .andExpect(jsonPath("$.redFlags[0].explanation").value("Bullets list duties, not outcomes."))
                .andExpect(jsonPath("$.reviewedAt").value("2026-10-15T09:00:30Z"))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void request_forAJobTheUserDoesNotHave_isNotFound() throws Exception {
        when(resumeReviewService.request(USER_ID, "r-1")).thenReturn(Optional.empty());

        mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isNotFound());
    }

    @Test
    void request_beforeUploadingAResume_isConflict_withWhatToDo() throws Exception {
        when(resumeReviewService.request(USER_ID, "r-1"))
                .thenThrow(new ResumeRequiredException("upload your resume before it can be reviewed against a job"));

        mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("upload your resume before it can be reviewed against a job"));
    }

    @Test
    void getAll_listsTheTokenSubjectsReviews_includingOnesThatFoundNothing() throws Exception {
        when(resumeReviewService.findAll(USER_ID)).thenReturn(List.of(READY,
                new ResumeReview("r-2", USER_ID, Status.READY, 3, 95, List.of(), List.of(), null, REQUESTED_AT,
                        REQUESTED_AT)));

        mockMvc.perform(get("/api/jobs/resume-reviews").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].jobResultId").value("r-1"))
                .andExpect(jsonPath("$[1].resumeAtsScore").value(95))
                .andExpect(jsonPath("$[1].missingKeywords").isEmpty())
                .andExpect(jsonPath("$[1].redFlags").isEmpty());
    }

    @Test
    void withoutAToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/jobs/resume-reviews")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL)).andExpect(status().isUnauthorized());

        verifyNoInteractions(resumeReviewService);
    }
}
