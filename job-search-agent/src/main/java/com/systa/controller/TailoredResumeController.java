package com.systa.controller;

import com.systa.model.ResumeDownload;
import com.systa.model.TailoredResume;
import com.systa.model.TailoredResumeDto;
import com.systa.service.TailoredResumeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The user is always the token's subject (Cognito "sub") - never a client-supplied id.
@RestController
@RequestMapping("/api/jobs/results/{id}/tailored-resume")
public class TailoredResumeController {

    private final TailoredResumeService tailoredResumeService;

    public TailoredResumeController(final TailoredResumeService tailoredResumeService) {
        this.tailoredResumeService = tailoredResumeService;
    }

    /**
     * Asks for the user's latest resume to be tailored to this job. 200 when one is already ready;
     * 202 when tailoring has started or is under way - poll the GET until it is READY or FAILED.
     * 404 if the user has no such job, 409 if they haven't uploaded a resume.
     */
    @PostMapping
    public ResponseEntity<TailoredResumeDto> request(@AuthenticationPrincipal final Jwt jwt,
                                                     @PathVariable final String id) {
        return tailoredResumeService.request(jwt.getSubject(), id)
                .map(tailoredResume -> ResponseEntity
                        .status(tailoredResume.status() == TailoredResume.Status.READY
                                ? HttpStatus.OK : HttpStatus.ACCEPTED)
                        .body(TailoredResumeDto.from(tailoredResume)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Where tailoring has got to. 404 if it was never asked for. */
    @GetMapping
    public ResponseEntity<TailoredResumeDto> get(@AuthenticationPrincipal final Jwt jwt,
                                                 @PathVariable final String id) {
        return ResponseEntity.of(tailoredResumeService.find(jwt.getSubject(), id).map(TailoredResumeDto::from));
    }

    /** A short-lived link to download the tailored resume. 404 unless it is ready. */
    @GetMapping("/download")
    public ResponseEntity<ResumeDownload> getDownloadLink(@AuthenticationPrincipal final Jwt jwt,
                                                          @PathVariable final String id) {
        return ResponseEntity.of(tailoredResumeService.downloadLink(jwt.getSubject(), id));
    }
}
