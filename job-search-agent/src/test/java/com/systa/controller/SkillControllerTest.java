package com.systa.controller;

import com.systa.model.Suggestion;
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

@WebMvcTest(controllers = SkillController.class, properties = {
        "auth.cognito.issuer-uri=https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_test",
        "auth.cognito.client-id=test-client",
        "auth.allowed-origins=http://localhost:5173"
})
@Import(SecurityConfig.class)
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SuggestionService suggestionService;

    @Test
    void suggest_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/skills/suggest").param("q", "spr"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(suggestionService);
    }

    @Test
    void suggest_returnsSkillNames() throws Exception {
        when(suggestionService.suggestSkills("spr", 10)).thenReturn(List.of(new Suggestion("Spring Boot")));

        mockMvc.perform(get("/api/skills/suggest").param("q", "spr").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Spring Boot"));
    }

    @Test
    void suggest_clampsLimit() throws Exception {
        mockMvc.perform(get("/api/skills/suggest").param("q", "spr").param("limit", "999").with(jwt()))
                .andExpect(status().isOk());
        verify(suggestionService).suggestSkills("spr", 20);

        mockMvc.perform(get("/api/skills/suggest").param("q", "java").param("limit", "0").with(jwt()))
                .andExpect(status().isOk());
        verify(suggestionService).suggestSkills("java", 1);
    }

    @Test
    void suggest_withOverlongQuery_returnsEmptyWithoutSearching() throws Exception {
        mockMvc.perform(get("/api/skills/suggest").param("q", "x".repeat(101)).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verifyNoInteractions(suggestionService);
    }
}
