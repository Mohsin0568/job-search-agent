package com.systa.controller;

import com.systa.exception.ResumeRequiredException;
import com.systa.exception.ResumeReviewRequiredException;
import com.systa.model.ResumeDownload;
import com.systa.model.ResumeReview.RedFlag;
import com.systa.model.ResumeTemplate;
import com.systa.model.TailoredResume;
import com.systa.model.TailoredResume.Status;
import com.systa.model.TailoredResumeContent;
import com.systa.model.TailoringFix;
import com.systa.security.EveryoneLetIn;
import com.systa.security.SecurityConfig;
import com.systa.service.TailoredResumeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TailoredResumeController.class, properties = {
        "auth.cognito.issuer-uri=https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_test",
        "auth.cognito.client-id=test-client",
        "auth.allowed-origins=http://localhost:5173"
})
@Import({SecurityConfig.class, EveryoneLetIn.class})
class TailoredResumeControllerTest {

    private static final String USER_ID = "cognito-sub-123";
    private static final String URL = "/api/jobs/results/r-1/tailored-resume";
    private static final Instant REQUESTED_AT = Instant.parse("2026-10-13T09:00:00Z");

    private static final TailoredResume PENDING = new TailoredResume("r-1", USER_ID, Status.PENDING, 3,
            ResumeTemplate.MODERN, null, null, null, REQUESTED_AT, null, null, null);
    private static final TailoredResume READY = PENDING.ready("tailored/u/r-1/v3-modern.pdf",
            "Jane-Doe-Acme-resume.pdf", new TailoredResumeContent("Jane Doe", null, null, "Summary", null, null, null,
                    null, null), REQUESTED_AT.plusSeconds(40));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TailoredResumeService tailoredResumeService;

    @Test
    void request_whenTailoringStarts_isAccepted_withThePendingStatus() throws Exception {
        when(tailoredResumeService.request(USER_ID, "r-1", null, null)).thenReturn(Optional.of(PENDING));

        mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobResultId").value("r-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.resumeVersion").value(3))
                .andExpect(jsonPath("$.template").value("MODERN"))
                .andExpect(jsonPath("$.requestedAt").value("2026-10-13T09:00:00Z"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void request_whenOneIsAlreadyReady_isOk_withoutSayingWhereItIsStored() throws Exception {
        when(tailoredResumeService.request(USER_ID, "r-1", null, null)).thenReturn(Optional.of(READY));

        mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.completedAt").value("2026-10-13T09:00:40Z"))
                .andExpect(jsonPath("$.s3Key").doesNotExist())
                .andExpect(jsonPath("$.fileName").doesNotExist())
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void request_passesOnTheTemplateAskedFor() throws Exception {
        when(tailoredResumeService.request(USER_ID, "r-1", ResumeTemplate.COMPACT, null))
                .thenReturn(Optional.of(READY));

        mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"template\":\"COMPACT\"}"))
                .andExpect(status().isOk());

        verify(tailoredResumeService).request(USER_ID, "r-1", ResumeTemplate.COMPACT, null);
    }

    @Test
    void request_withAnEmptyBody_keepsTheCurrentTemplate() throws Exception {
        when(tailoredResumeService.request(USER_ID, "r-1", null, null)).thenReturn(Optional.of(PENDING));

        mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted());

        verify(tailoredResumeService).request(USER_ID, "r-1", null, null);
    }

    @Test
    void request_passesOnWhatToFix_andShowsWhatItFixes() throws Exception {
        final TailoredResume fixing = new TailoredResume("r-1", USER_ID, Status.PENDING, 3, ResumeTemplate.MODERN,
                null, null, null, REQUESTED_AT, null, null, new TailoredResume.Fix(List.of("Kafka"),
                List.of(new RedFlag("Vague results", "No numbers on any achievement."))));
        final TailoringFix fix = new TailoringFix(List.of("Kafka"), true);
        when(tailoredResumeService.request(USER_ID, "r-1", null, fix)).thenReturn(Optional.of(fixing));

        mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fix\":{\"keywords\":[\"Kafka\"],\"redFlags\":true}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.fix.keywords[0]").value("Kafka"))
                .andExpect(jsonPath("$.fix.redFlags[0].issue").value("Vague results"))
                .andExpect(jsonPath("$.fix.redFlags[0].explanation").value("No numbers on any achievement."));

        verify(tailoredResumeService).request(USER_ID, "r-1", null, fix);
    }

    @Test
    void request_toFixWithoutAReview_isConflict_withWhatToDo() throws Exception {
        final TailoringFix fix = new TailoringFix(List.of("Kafka"), false);
        when(tailoredResumeService.request(USER_ID, "r-1", null, fix)).thenThrow(new ResumeReviewRequiredException(
                "review your latest resume against this job before fixing what the review finds"));

        mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fix\":{\"keywords\":[\"Kafka\"],\"redFlags\":false}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("review your latest resume against this job before fixing what the review finds"));
    }

    @Test
    void request_tailoredAsIs_showsNoFix() throws Exception {
        when(tailoredResumeService.request(USER_ID, "r-1", null, null)).thenReturn(Optional.of(READY));

        mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fix").isEmpty());
    }

    @Test
    void request_forATemplateThatDoesNotExist_isBadRequest() throws Exception {
        mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"template\":\"FANCY\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tailoredResumeService);
    }

    @Test
    void request_forAJobTheUserDoesNotHave_isNotFound() throws Exception {
        when(tailoredResumeService.request(USER_ID, "r-1", null, null)).thenReturn(Optional.empty());

        mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isNotFound());
    }

    @Test
    void request_beforeUploadingAResume_isConflict_withWhatToDo() throws Exception {
        when(tailoredResumeService.request(USER_ID, "r-1", null, null))
                .thenThrow(new ResumeRequiredException("upload your resume before tailoring it to a job"));

        mockMvc.perform(post(URL).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("upload your resume before tailoring it to a job"));
    }

    @Test
    void request_withoutAToken_isUnauthorized() throws Exception {
        mockMvc.perform(post(URL)).andExpect(status().isUnauthorized());

        verifyNoInteractions(tailoredResumeService);
    }

    @Test
    void get_returnsWhereTailoringHasGotTo_includingWhyItFailed() throws Exception {
        when(tailoredResumeService.find(USER_ID, "r-1")).thenReturn(Optional.of(
                PENDING.failed("we couldn't find this job posting", REQUESTED_AT.plusSeconds(20))));

        mockMvc.perform(get(URL).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("we couldn't find this job posting"));
    }

    @Test
    void get_whenNeverRequested_isNotFound() throws Exception {
        when(tailoredResumeService.find(USER_ID, "r-1")).thenReturn(Optional.empty());

        mockMvc.perform(get(URL).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDownloadLink_returnsTheLinkAndWhenItExpires() throws Exception {
        when(tailoredResumeService.downloadLink(USER_ID, "r-1")).thenReturn(Optional.of(
                new ResumeDownload("https://bucket.s3.amazonaws.com/tailored?sig=abc", REQUESTED_AT.plusSeconds(300))));

        mockMvc.perform(get(URL + "/download").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://bucket.s3.amazonaws.com/tailored?sig=abc"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-13T09:05:00Z"));
    }

    @Test
    void getDownloadLink_beforeItIsReady_isNotFound() throws Exception {
        when(tailoredResumeService.downloadLink(USER_ID, "r-1")).thenReturn(Optional.empty());

        mockMvc.perform(get(URL + "/download").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAll_listsTheTokenSubjectsTailoredResumes() throws Exception {
        when(tailoredResumeService.findAll(USER_ID)).thenReturn(List.of(READY,
                new TailoredResume("r-2", USER_ID, Status.PENDING, 3, null, null, null, null, REQUESTED_AT, null,
                        null, null)));

        mockMvc.perform(get("/api/jobs/tailored-resumes").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].jobResultId").value("r-1"))
                .andExpect(jsonPath("$[0].status").value("READY"))
                .andExpect(jsonPath("$[1].jobResultId").value("r-2"))
                .andExpect(jsonPath("$[1].status").value("PENDING"))
                // Tailored before there was a choice of template: shown as the one it was laid out in.
                .andExpect(jsonPath("$[1].template").value("CLASSIC"))
                .andExpect(jsonPath("$[0].s3Key").doesNotExist());
    }

    @Test
    void getAll_withoutAToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/jobs/tailored-resumes")).andExpect(status().isUnauthorized());
    }
}
