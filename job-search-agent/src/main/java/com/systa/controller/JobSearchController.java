package com.systa.controller;

import com.systa.model.UserJobSearchResult;
import com.systa.service.JobSearchResultQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobSearchController {

    private static final int MAX_PAGE_SIZE = 100;

    private final JobSearchResultQueryService jobSearchResultQueryService;

    public JobSearchController(final JobSearchResultQueryService jobSearchResultQueryService) {
        this.jobSearchResultQueryService = jobSearchResultQueryService;
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
}
