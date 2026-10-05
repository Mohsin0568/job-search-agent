package com.systa.controller;

import com.systa.model.CompanySuggestion;
import com.systa.security.SecurityConfig;
import com.systa.service.CompanySuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CompanyController.class, properties = {
        "auth.cognito.issuer-uri=https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_test",
        "auth.cognito.client-id=test-client",
        "auth.allowed-origins=http://localhost:5173"
})
@Import(SecurityConfig.class)
class CompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanySuggestionService companySuggestionService;

    @Test
    void suggest_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/companies/suggest").param("q", "del"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(companySuggestionService);
    }

    @Test
    void suggest_returnsCompanyNames() throws Exception {
        when(companySuggestionService.suggest("del", 10)).thenReturn(List.of(new CompanySuggestion("Deliveroo")));

        mockMvc.perform(get("/api/companies/suggest").param("q", "del").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Deliveroo"));
    }

    @Test
    void suggest_clampsLimit() throws Exception {
        mockMvc.perform(get("/api/companies/suggest").param("q", "del").param("limit", "999").with(jwt()))
                .andExpect(status().isOk());
        verify(companySuggestionService).suggest("del", 20);

        mockMvc.perform(get("/api/companies/suggest").param("q", "acme").param("limit", "0").with(jwt()))
                .andExpect(status().isOk());
        verify(companySuggestionService).suggest("acme", 1);
    }

    @Test
    void suggest_withOverlongQuery_returnsEmptyWithoutSearching() throws Exception {
        mockMvc.perform(get("/api/companies/suggest").param("q", "x".repeat(101)).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verifyNoInteractions(companySuggestionService);
    }
}
