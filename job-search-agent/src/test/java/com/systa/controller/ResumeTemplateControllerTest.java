package com.systa.controller;

import com.systa.model.ResumeTemplate;
import com.systa.security.SecurityConfig;
import com.systa.service.ResumeTemplatePreviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ResumeTemplateController.class, properties = {
        "auth.cognito.issuer-uri=https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_test",
        "auth.cognito.client-id=test-client",
        "auth.allowed-origins=http://localhost:5173"
})
@Import(SecurityConfig.class)
class ResumeTemplateControllerTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResumeTemplatePreviewService previewService;

    // An <img> can't send the user's token, and the picture holds nothing of theirs.
    @Test
    void preview_isAPicture_thatAnyoneMayLoad_andBrowsersMayCache() throws Exception {
        when(previewService.preview(ResumeTemplate.KENDALL)).thenReturn(PNG);

        mockMvc.perform(get("/api/resume-templates/KENDALL/preview"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("Cache-Control", "max-age=3600, public"));
    }

    @Test
    void preview_ofATemplateThatDoesNotExist_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/resume-templates/FANCY/preview")).andExpect(status().isBadRequest());

        verifyNoInteractions(previewService);
    }

    @Test
    void onlyReadingAPreviewIsOpen_everythingElseStillNeedsAToken() throws Exception {
        mockMvc.perform(post("/api/resume-templates/KENDALL/preview")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/resume-templates")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/resume-templates/KENDALL/preview/extra")).andExpect(status().isUnauthorized());
    }
}
