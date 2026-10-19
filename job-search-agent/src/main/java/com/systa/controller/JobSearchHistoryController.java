package com.systa.controller;

import com.systa.model.JobSearchRunDetailDto;
import com.systa.model.JobSearchRunSummaryDto;
import com.systa.service.JobSearchHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// The user is always the token's subject (Cognito "sub") - never a client-supplied id.
@RestController
@RequestMapping("/api/jobs/search-runs")
public class JobSearchHistoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final JobSearchHistoryService jobSearchHistoryService;

    public JobSearchHistoryController(final JobSearchHistoryService jobSearchHistoryService) {
        this.jobSearchHistoryService = jobSearchHistoryService;
    }

    /** The user's search history, newest run first: one row per run, with its totals across companies. */
    @GetMapping
    public List<JobSearchRunSummaryDto> getRuns(@AuthenticationPrincipal final Jwt jwt,
                                                @RequestParam(defaultValue = "0") final int page,
                                                @RequestParam(defaultValue = "20") final int size) {
        return jobSearchHistoryService.findRuns(
                jwt.getSubject(),
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE));
    }

    /** What one run found for each company, from each source. 404 if the user has no such run. */
    @GetMapping("/{runId}")
    public ResponseEntity<JobSearchRunDetailDto> getRun(@AuthenticationPrincipal final Jwt jwt,
                                                        @PathVariable final String runId) {
        return ResponseEntity.of(jobSearchHistoryService.findRun(jwt.getSubject(), runId));
    }
}
