package com.systa.controller;

import com.systa.model.ResumeTemplate;
import com.systa.service.ResumeTemplatePreviewService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

// Open to anyone (see SecurityConfig): a preview shows only made-up sample content, and is loaded
// straight into an <img>, which can't send the user's token.
@RestController
@RequestMapping("/api/resume-templates")
public class ResumeTemplateController {

    private final ResumeTemplatePreviewService previewService;

    public ResumeTemplateController(final ResumeTemplatePreviewService previewService) {
        this.previewService = previewService;
    }

    /** A picture of the template's first page, laid out with a sample resume. 400 for an unknown template. */
    @GetMapping(value = "/{template}/preview", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> preview(@PathVariable final ResumeTemplate template) {
        return ResponseEntity.ok()
                // Changes only when a new version of the app is deployed.
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(previewService.preview(template));
    }
}
