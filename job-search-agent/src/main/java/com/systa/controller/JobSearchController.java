package com.systa.controller;

import com.systa.model.CandidateProfile;
import com.systa.model.JobSearchRequest;
import com.systa.model.JobSearchResponse;
import com.systa.service.JobSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobSearchController {

    private final JobSearchService jobSearchService;

    public JobSearchController(final JobSearchService jobSearchService) {
        this.jobSearchService = jobSearchService;
    }

    @PostMapping("/search")
    public ResponseEntity<JobSearchResponse> searchJobs(@RequestBody final JobSearchRequest request) {
        return ResponseEntity.ok(jobSearchService.searchJobs(request.userId()));
    }
}
