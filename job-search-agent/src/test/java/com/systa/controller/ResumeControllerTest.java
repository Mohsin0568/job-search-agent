package com.systa.controller;

import com.systa.exception.InvalidResumeException;
import com.systa.exception.ResumeStorageException;
import com.systa.exception.ResumeUploadConflictException;
import com.systa.model.ResumeDownload;
import com.systa.model.UserResume;
import com.systa.security.SecurityConfig;
import com.systa.service.ResumeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ResumeController.class, properties = {
        "auth.cognito.issuer-uri=https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_test",
        "auth.cognito.client-id=test-client",
        "auth.allowed-origins=http://localhost:5173"
})
@Import(SecurityConfig.class)
class ResumeControllerTest {

    private static final String USER_ID = "cognito-sub-123";
    private static final Instant UPLOADED_AT = Instant.parse("2026-10-12T09:00:00Z");
    private static final byte[] CONTENT = "%PDF-1.7 resume".getBytes();
    private static final MockMultipartFile FILE =
            new MockMultipartFile("file", "jane-cv.pdf", "application/pdf", CONTENT);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResumeService resumeService;

    @Test
    void upload_storesTheFileForTheTokenSubject_andReturnsTheNewVersion() throws Exception {
        when(resumeService.upload(USER_ID, "jane-cv.pdf", CONTENT)).thenReturn(resume(2, true));

        mockMvc.perform(multipart("/api/profile/resume").file(FILE).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.latest").value(true))
                .andExpect(jsonPath("$.fileName").value("cv-v2.pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(2048))
                .andExpect(jsonPath("$.uploadedAt").value("2026-10-12T09:00:00Z"))
                // Where the file is stored, and its text, stay on the server.
                .andExpect(jsonPath("$.s3Key").doesNotExist())
                .andExpect(jsonPath("$.extractedText").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void upload_withoutAToken_isUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/profile/resume").file(FILE))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(resumeService);
    }

    @Test
    void upload_withoutAFile_isBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/profile/resume").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(resumeService);
    }

    @Test
    void upload_ofAFileThatIsNotAUsableResume_isBadRequest_withTheReason() throws Exception {
        when(resumeService.upload(anyString(), anyString(), any()))
                .thenThrow(new InvalidResumeException("resume must be a PDF file"));

        mockMvc.perform(multipart("/api/profile/resume").file(FILE).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("resume must be a PDF file"));
    }

    @Test
    void upload_whenStorageIsDown_isServiceUnavailable_withoutLeakingDetails() throws Exception {
        when(resumeService.upload(anyString(), anyString(), any()))
                .thenThrow(new ResumeStorageException("Could not store the resume in S3 - key=resumes/x", null));

        mockMvc.perform(multipart("/api/profile/resume").file(FILE).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("we couldn't reach resume storage right now, please try again"));
    }

    @Test
    void upload_racingAnotherUpload_isConflict() throws Exception {
        when(resumeService.upload(anyString(), anyString(), any()))
                .thenThrow(new ResumeUploadConflictException("version taken", null));

        mockMvc.perform(multipart("/api/profile/resume").file(FILE).with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isConflict());
    }

    @Test
    void getVersions_listsTheTokenSubjectsVersions_newestFirst() throws Exception {
        when(resumeService.findVersions(USER_ID)).thenReturn(List.of(resume(2, true), resume(1, false)));

        mockMvc.perform(get("/api/profile/resume").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].latest").value(true))
                .andExpect(jsonPath("$[1].version").value(1))
                .andExpect(jsonPath("$[1].latest").value(false))
                .andExpect(jsonPath("$[0].s3Key").doesNotExist());
    }

    @Test
    void getVersions_forAUserWithNoResume_isAnEmptyList() throws Exception {
        when(resumeService.findVersions(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/profile/resume").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getDownloadLink_returnsTheLinkAndWhenItExpires() throws Exception {
        when(resumeService.downloadLink(USER_ID, 2)).thenReturn(Optional.of(
                new ResumeDownload("https://bucket.s3.amazonaws.com/resume?sig=abc", UPLOADED_AT.plusSeconds(300))));

        mockMvc.perform(get("/api/profile/resume/2/download").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://bucket.s3.amazonaws.com/resume?sig=abc"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-12T09:05:00Z"));
    }

    @Test
    void getDownloadLink_forAVersionTheUserDoesNotHave_isNotFound() throws Exception {
        when(resumeService.downloadLink(USER_ID, 9)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/profile/resume/9/download").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDownloadLink_withoutAToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/profile/resume/1/download"))
                .andExpect(status().isUnauthorized());
    }

    private static UserResume resume(final int version, final boolean latest) {
        return new UserResume("id-" + version, USER_ID, version, latest, "resumes/x/v" + version + "/id.pdf",
                "cv-v" + version + ".pdf", "application/pdf", 2048, UPLOADED_AT, "Senior Java Developer");
    }
}
