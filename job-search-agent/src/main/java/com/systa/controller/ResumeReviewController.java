package com.systa.controller;

import com.systa.model.ResumeReview;
import com.systa.model.ResumeReviewDto;
import com.systa.service.ResumeReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// The user is always the token's subject (Cognito "sub") - never a client-supplied id.
@RestController
@RequestMapping("/api/jobs")
public class ResumeReviewController {

    private final ResumeReviewService resumeReviewService;

    public ResumeReviewController(final ResumeReviewService resumeReviewService) {
        this.resumeReviewService = resumeReviewService;
    }

    /** Every resume review the user has, so a page of jobs can show them all in one call. */
    @GetMapping("/resume-reviews")
    public List<ResumeReviewDto> getAll(@AuthenticationPrincipal final Jwt jwt) {
        return resumeReviewService.findAll(jwt.getSubject()).stream()
                .map(ResumeReviewDto::from)
                .toList();
    }

    /**
     * Asks for the user's latest resume to be reviewed against this job. 200 when a review of it is
     * already ready; 202 when one has started or is under way - poll the list until it is READY or FAILED.
     * 404 if the user has no such job, 409 if they haven't uploaded a resume.
     */
    @PostMapping("/results/{id}/resume-review")
    public ResponseEntity<ResumeReviewDto> request(@AuthenticationPrincipal final Jwt jwt,
                                                   @PathVariable final String id) {
        return resumeReviewService.request(jwt.getSubject(), id)
                .map(review -> ResponseEntity
                        .status(review.status() == ResumeReview.Status.READY ? HttpStatus.OK : HttpStatus.ACCEPTED)
                        .body(ResumeReviewDto.from(review)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
