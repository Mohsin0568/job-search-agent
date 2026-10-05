package com.systa.controller;

import com.systa.exception.IdentityProviderUnavailableException;
import com.systa.exception.UserSessionRevokedException;
import com.systa.model.CandidateProfile;
import com.systa.model.CandidateProfileDto;
import com.systa.security.SecurityConfig;
import com.systa.service.CandidateProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProfileController.class, properties = {
        "auth.cognito.issuer-uri=https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_test",
        "auth.cognito.client-id=test-client",
        "auth.allowed-origins=http://localhost:5173"
})
@Import(SecurityConfig.class)
class ProfileControllerTest {

    private static final String USER_ID = "cognito-sub-123";
    private static final String EMAIL = "jane@example.com";
    private static final String ACCESS_TOKEN = "access-token";

    private static final String VALID_BODY = """
            {
              "desiredRole": "Senior Java Developer",
              "skills": ["Java", "Spring Boot"],
              "currentJobDescription": "Building APIs",
              "companyPreferences": ["Acme", "Globex"],
              "recencyWindowDays": 14
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CandidateProfileService candidateProfileService;

    @Test
    void getProfile_whenNoneSaved_isNotFound() throws Exception {
        when(candidateProfileService.findByUserId(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/profile").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProfile_returnsEditableFieldsOnly() throws Exception {
        when(candidateProfileService.findByUserId(USER_ID)).thenReturn(Optional.of(new CandidateProfile(
                "p-1", USER_ID, EMAIL, "Senior Java Developer", List.of("Java"), "Building APIs", List.of("Acme"), 14)));

        mockMvc.perform(get("/api/profile").with(jwt().jwt(token -> token.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredRole").value("Senior Java Developer"))
                .andExpect(jsonPath("$.companyPreferences[0]").value("Acme"))
                .andExpect(jsonPath("$.recencyWindowDays").value(14))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void saveProfile_savesForTokenSubject_passingTheAccessToken() throws Exception {
        when(candidateProfileService.save(eq(USER_ID), eq(ACCESS_TOKEN), any())).thenReturn(new CandidateProfile(
                "p-1", USER_ID, EMAIL, "Senior Java Developer", List.of("Java", "Spring Boot"), "Building APIs",
                List.of("Acme", "Globex"), 14));

        mockMvc.perform(put("/api/profile")
                        .with(jwt().jwt(token -> token.subject(USER_ID).tokenValue(ACCESS_TOKEN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyPreferences.length()").value(2))
                .andExpect(jsonPath("$.email").doesNotExist());

        final ArgumentCaptor<CandidateProfileDto> captor = ArgumentCaptor.forClass(CandidateProfileDto.class);
        verify(candidateProfileService).save(eq(USER_ID), eq(ACCESS_TOKEN), captor.capture());
        assertThat(captor.getValue().skills()).containsExactly("Java", "Spring Boot");
    }

    @Test
    void saveProfile_whenCognitoRevokedSession_isUnauthorized() throws Exception {
        when(candidateProfileService.save(eq(USER_ID), anyString(), any()))
                .thenThrow(new UserSessionRevokedException(USER_ID, null));

        mockMvc.perform(put("/api/profile")
                        .with(jwt().jwt(token -> token.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void saveProfile_whenCognitoUnavailable_isServiceUnavailable() throws Exception {
        when(candidateProfileService.save(eq(USER_ID), anyString(), any()))
                .thenThrow(new IdentityProviderUnavailableException(USER_ID, null));

        mockMvc.perform(put("/api/profile")
                        .with(jwt().jwt(token -> token.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void saveProfile_withTooManyCompanies_isBadRequest() throws Exception {
        final String companies = IntStream.rangeClosed(1, 11)
                .mapToObj(i -> "\"Company " + i + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElseThrow();

        mockMvc.perform(put("/api/profile")
                        .with(jwt().jwt(token -> token.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyPreferences\":[" + companies + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("companyPreferences")));

        verify(candidateProfileService, never()).save(anyString(), any(), any());
    }

    @Test
    void saveProfile_withInvalidRecencyWindow_isBadRequest() throws Exception {
        mockMvc.perform(put("/api/profile")
                        .with(jwt().jwt(token -> token.subject(USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recencyWindowDays\":0}"))
                .andExpect(status().isBadRequest());

        verify(candidateProfileService, never()).save(anyString(), any(), any());
    }

    @Test
    void saveProfile_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(candidateProfileService);
    }
}
