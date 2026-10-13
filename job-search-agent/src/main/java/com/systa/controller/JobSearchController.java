package com.systa.controller;

import com.systa.model.JobDescriptionDto;
import com.systa.model.UserJobSearchResult;
import com.systa.service.JobDescriptionService;
import com.systa.service.JobSearchResultQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobSearchController {

    private static final int MAX_PAGE_SIZE = 100;

    private final JobSearchResultQueryService jobSearchResultQueryService;
    private final JobDescriptionService jobDescriptionService;

    public JobSearchController(final JobSearchResultQueryService jobSearchResultQueryService,
                               final JobDescriptionService jobDescriptionService) {
        this.jobSearchResultQueryService = jobSearchResultQueryService;
        this.jobDescriptionService = jobDescriptionService;
    }

    // Searches are run by the daily job (see DailyJobSearchRunner), not by users - this only reads results.
    // The user is always the token's subject (Cognito "sub") - never a client-supplied id.
    @GetMapping("/results")
    public List<UserJobSearchResult> getResults(@AuthenticationPrincipal final Jwt jwt,
                                                @RequestParam(defaultValue = "0") final int page,
                                                @RequestParam(defaultValue = "50") final int size) {
        return jobSearchResultQueryService.findForUser(
                jwt.getSubject(),
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE));
    }

    /**
     * What one of the user's saved jobs asks for. The first call for a job reads its posting, which
     * takes a while; later calls return what was kept. POST, because it can do that work.
     * 404 if the user has no such job, 422 if the posting can't be read.
     */
    @PostMapping("/results/{id}/description")
    public ResponseEntity<JobDescriptionDto> getDescription(@AuthenticationPrincipal final Jwt jwt,
                                                            @PathVariable final String id) {
        return ResponseEntity.of(jobDescriptionService.getOrFetch(jwt.getSubject(), id)
                .map(JobDescriptionDto::from));
    }
}
