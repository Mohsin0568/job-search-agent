package com.systa.controller;

import com.systa.model.CompanyDto;
import com.systa.model.Sector;
import com.systa.model.Suggestion;
import com.systa.security.EveryoneLetIn;
import com.systa.security.SecurityConfig;
import com.systa.service.SuggestionService;
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
@Import({SecurityConfig.class, EveryoneLetIn.class})
class CompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SuggestionService suggestionService;

    @Test
    void all_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(suggestionService);
    }

    @Test
    void all_returnsEachCompanyWithItsSectorLabel() throws Exception {
        when(suggestionService.allCompanies()).thenReturn(List.of(
                new CompanyDto("Barclays", Sector.BANKING),
                new CompanyDto("BAE Systems", Sector.ENGINEERING_AEROSPACE_AND_DEFENCE)));

        mockMvc.perform(get("/api/companies").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Barclays"))
                .andExpect(jsonPath("$[0].sector").value("Banking"))
                .andExpect(jsonPath("$[1].sector").value("Engineering, Aerospace & Defence"));
    }

    @Test
    void suggest_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/companies/suggest").param("q", "del"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(suggestionService);
    }

    @Test
    void suggest_returnsCompanyNames() throws Exception {
        when(suggestionService.suggestCompanies("del", 10)).thenReturn(List.of(new Suggestion("Deliveroo")));

        mockMvc.perform(get("/api/companies/suggest").param("q", "del").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Deliveroo"));
    }

    @Test
    void suggest_clampsLimit() throws Exception {
        mockMvc.perform(get("/api/companies/suggest").param("q", "del").param("limit", "999").with(jwt()))
                .andExpect(status().isOk());
        verify(suggestionService).suggestCompanies("del", 20);

        mockMvc.perform(get("/api/companies/suggest").param("q", "acme").param("limit", "0").with(jwt()))
                .andExpect(status().isOk());
        verify(suggestionService).suggestCompanies("acme", 1);
    }

    @Test
    void suggest_withOverlongQuery_returnsEmptyWithoutSearching() throws Exception {
        mockMvc.perform(get("/api/companies/suggest").param("q", "x".repeat(101)).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verifyNoInteractions(suggestionService);
    }
}
