package com.systa.service;

import com.systa.model.UserJobSearchResult;
import com.systa.model.UserJobSearchResult.Status;
import com.systa.repository.UserJobSearchResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
public class JobSearchResultQueryService {

    private static final Sort NEWEST_RUN_FIRST = Sort.by(Sort.Direction.DESC, "jobRunDateTime");
    // The id breaks ties, so paging doesn't skip or repeat jobs marked at the same moment.
    private static final Sort LAST_MARKED_FIRST = Sort.by(Sort.Direction.DESC, "statusChangedAt")
            .and(NEWEST_RUN_FIRST)
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final UserJobSearchResultRepository userJobSearchResultRepository;
    private final Clock clock;

    @Autowired
    public JobSearchResultQueryService(final UserJobSearchResultRepository userJobSearchResultRepository) {
        this(userJobSearchResultRepository, Clock.systemUTC());
    }

    JobSearchResultQueryService(final UserJobSearchResultRepository userJobSearchResultRepository,
                                final Clock clock) {
        this.userJobSearchResultRepository = userJobSearchResultRepository;
        this.clock = clock;
    }

    /**
     * A page of the user's jobs with that status. Jobs on the dashboard come newest run first; jobs
     * the user has marked come in the order they marked them, latest first.
     */
    public List<UserJobSearchResult> findForUser(final String userId, final Status status, final int page,
                                                 final int size) {
        log.info("Fetching job results for user {} - status={}", userId, status);
        if (status == Status.ACTIVE) {
            // Not "status = ACTIVE", so results saved before statuses existed are still found.
            return userJobSearchResultRepository.findByUserIdAndStatusNotIn(userId,
                    EnumSet.complementOf(EnumSet.of(Status.ACTIVE)), PageRequest.of(page, size, NEWEST_RUN_FIRST));
        }
        return userJobSearchResultRepository.findByUserIdAndStatus(userId, status,
                PageRequest.of(page, size, LAST_MARKED_FIRST));
    }

    /**
     * Marks one of the user's jobs - ACTIVE puts it back on the dashboard. The reason, which is optional,
     * is kept only for NOT_RELEVANT; marking a not-relevant job again changes its reason.
     * Empty if the user has no such job. Marking a job just as it already is changes nothing.
     */
    public Optional<UserJobSearchResult> updateStatus(final String userId, final String jobResultId,
                                                      final Status status, final String reason) {
        return userJobSearchResultRepository.findByIdAndUserId(jobResultId, userId)
                .map(result -> {
                    final UserJobSearchResult marked = result.withStatus(status, reason, clock.instant());
                    if (result.status() == status
                            && Objects.equals(result.notRelevantReason(), marked.notRelevantReason())) {
                        return result;
                    }
                    log.info("Job marked - userId={}, jobResultId={}, from={}, to={}, withReason={}",
                            userId, jobResultId, result.status(), status, marked.notRelevantReason() != null);
                    return userJobSearchResultRepository.save(marked);
                });
    }
}
