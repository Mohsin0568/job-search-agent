package com.systa.service;

import com.systa.model.JobSearchAudit;
import com.systa.model.JobSearchRun;
import com.systa.model.JobSearchRunDetailDto;
import com.systa.model.JobSearchRunDetailDto.CompanyResult;
import com.systa.model.JobSearchRunSummaryDto;
import com.systa.repository.JobSearchAuditRepository;
import com.systa.repository.JobSearchRunRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** The user's job search history: each run of the daily search, and what it found for each company. */
@Service
public class JobSearchHistoryService {

    // Newest first; the id breaks a tie so paging never skips or repeats a run.
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"));

    private final JobSearchRunRepository jobSearchRunRepository;
    private final JobSearchAuditRepository jobSearchAuditRepository;

    public JobSearchHistoryService(final JobSearchRunRepository jobSearchRunRepository,
                                   final JobSearchAuditRepository jobSearchAuditRepository) {
        this.jobSearchRunRepository = jobSearchRunRepository;
        this.jobSearchAuditRepository = jobSearchAuditRepository;
    }

    /** One page of the user's runs, newest first, each with its totals across companies. */
    public List<JobSearchRunSummaryDto> findRuns(final String userId, final int page, final int size) {
        final List<JobSearchRun> runs = jobSearchRunRepository.findByUserId(userId,
                PageRequest.of(page, size, NEWEST_FIRST));
        if (runs.isEmpty()) {
            return List.of();
        }
        final Map<String, List<JobSearchAudit>> auditsByRun = jobSearchAuditRepository
                .findCountsByUserIdAndRunIdIn(userId, runs.stream().map(JobSearchRun::id).toList())
                .stream()
                .collect(Collectors.groupingBy(JobSearchAudit::runId));
        return runs.stream()
                .map(run -> JobSearchRunSummaryDto.from(run, companies(auditsByRun.getOrDefault(run.id(), List.of()))))
                .toList();
    }

    /**
     * One of the user's runs, with each company in the order it was searched.
     *
     * @return empty when the user has no such run
     */
    public Optional<JobSearchRunDetailDto> findRun(final String userId, final String runId) {
        return jobSearchRunRepository.findByIdAndUserId(runId, userId).map(run -> {
            final List<CompanyResult> companies =
                    companies(jobSearchAuditRepository.findCountsByUserIdAndRunIdIn(userId, List.of(runId)));
            return new JobSearchRunDetailDto(JobSearchRunSummaryDto.from(run, companies), companies);
        });
    }

    private static List<CompanyResult> companies(final List<JobSearchAudit> audits) {
        return audits.stream()
                .sorted(Comparator.comparing(JobSearchAudit::searchedAt))
                .map(CompanyResult::from)
                .toList();
    }
}
