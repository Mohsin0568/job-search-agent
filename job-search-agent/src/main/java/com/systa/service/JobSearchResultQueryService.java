package com.systa.service;

import com.systa.model.UserJobSearchResult;
import com.systa.repository.UserJobSearchResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class JobSearchResultQueryService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "jobRunDateTime");

    private final UserJobSearchResultRepository userJobSearchResultRepository;

    public JobSearchResultQueryService(final UserJobSearchResultRepository userJobSearchResultRepository) {
        this.userJobSearchResultRepository = userJobSearchResultRepository;
    }

    public List<UserJobSearchResult> findForUser(final String userId, final int page, final int size) {
        log.info("Fetching job results for user {}", userId);
        return userJobSearchResultRepository.findByUserId(userId, PageRequest.of(page, size, NEWEST_FIRST));
    }
}
