package com.systa.controller;

import com.systa.model.UserAccess.Status;
import com.systa.security.SecurityConfig;
import com.systa.service.AccessService;
import com.systa.service.SuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/access, and the check on every other API request (AccessWebConfig) that it stands behind. */
@WebMvcTest(controllers = {AccessController.class, CompanyController.class}, properties = {
        "auth.cognito.issuer-uri=https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_test",
        "auth.cognito.client-id=test-client",
        "auth.allowed-origins=http://localhost:5173"
})
@Import(SecurityConfig.class)
class AccessControllerTest {

    private static final String USER_ID = "cognito-sub-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessService accessService;

    // Behind CompanyController, an API that is turned away or let through.
    @MockitoBean
    private SuggestionService suggestionService;

    @Test
    void access_saysWhereTheUserStands_evenWhenTheyAreNotLetIn() throws Exception {
        when(accessService.statusOf(USER_ID, "token")).thenReturn(Status.PENDING);
        when(accessService.inviteOnly()).thenReturn(true);

        mockMvc.perform(get("/api/access").with(jwt().jwt(token -> token.subject(USER_ID).tokenValue("token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.inviteOnly").value(true));
    }

    @Test
    void access_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/access")).andExpect(status().isUnauthorized());

        verifyNoInteractions(accessService);
    }

    @Test
    void otherApis_turnAwayAUserOnTheWaitlist_beforeDoingAnything() throws Exception {
        when(accessService.statusOf(USER_ID, "token")).thenReturn(Status.PENDING);

        mockMvc.perform(get("/api/companies").with(jwt().jwt(token -> token.subject(USER_ID).tokenValue("token"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_PENDING"))
                .andExpect(jsonPath("$.message").value("the app is invite-only for now, and you're on the waitlist"));

        verifyNoInteractions(suggestionService);
    }

    @Test
    void otherApis_turnAwayARevokedUser() throws Exception {
        when(accessService.statusOf(any(), any())).thenReturn(Status.REVOKED);

        mockMvc.perform(get("/api/companies").with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_REVOKED"));

        verifyNoInteractions(suggestionService);
    }

    @Test
    void otherApis_letAnApprovedUserThrough() throws Exception {
        when(accessService.statusOf(USER_ID, "token")).thenReturn(Status.APPROVED);
        when(suggestionService.allCompanies()).thenReturn(List.of());

        mockMvc.perform(get("/api/companies").with(jwt().jwt(token -> token.subject(USER_ID).tokenValue("token"))))
                .andExpect(status().isOk());

        verify(suggestionService).allCompanies();
    }

    @Test
    void otherApis_withoutToken_areUnauthorized_notForbidden() throws Exception {
        mockMvc.perform(get("/api/companies")).andExpect(status().isUnauthorized());

        verifyNoInteractions(accessService);
    }
}
