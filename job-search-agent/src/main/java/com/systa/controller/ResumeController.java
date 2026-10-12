package com.systa.controller;

import com.systa.model.ResumeDownload;
import com.systa.model.ResumeDto;
import com.systa.service.ResumeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

// The user is always the token's subject (Cognito "sub") - never a client-supplied id.
@RestController
@RequestMapping("/api/profile/resume")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(final ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /** Uploads a new version, which becomes the user's latest resume. Earlier versions are kept. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResumeDto upload(@AuthenticationPrincipal final Jwt jwt,
                            @RequestPart("file") final MultipartFile file) throws IOException {
        return ResumeDto.from(resumeService.upload(jwt.getSubject(), file.getOriginalFilename(), file.getBytes()));
    }

    /** Every version the user has uploaded, newest first. Empty for a user with no resume yet. */
    @GetMapping
    public List<ResumeDto> getVersions(@AuthenticationPrincipal final Jwt jwt) {
        return resumeService.findVersions(jwt.getSubject()).stream()
                .map(ResumeDto::from)
                .toList();
    }

    /** A short-lived link to download one version. 404 if the user has no such version. */
    @GetMapping("/{version}/download")
    public ResponseEntity<ResumeDownload> getDownloadLink(@AuthenticationPrincipal final Jwt jwt,
                                                          @PathVariable final int version) {
        return ResponseEntity.of(resumeService.downloadLink(jwt.getSubject(), version));
    }
}
