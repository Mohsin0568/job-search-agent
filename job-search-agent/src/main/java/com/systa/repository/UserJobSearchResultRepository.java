package com.systa.repository;

import com.systa.model.UserJobSearchResult;
import com.systa.model.UserJobSearchResult.Status;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserJobSearchResultRepository extends MongoRepository<UserJobSearchResult, String> {

    Optional<UserJobSearchResult> findByUserIdAndCompanyNameAndJob_JobId(
            final String userId, final String companyName, final String jobId);

    /** One of the user's own results - empty for an id that exists but belongs to someone else. */
    Optional<UserJobSearchResult> findByIdAndUserId(final String id, final String userId);

    List<UserJobSearchResult> findByUserIdAndStatus(final String userId, final Status status, final Pageable pageable);

    /** Matches results with no status too - those saved before statuses existed. */
    List<UserJobSearchResult> findByUserIdAndStatusNotIn(final String userId, final Collection<Status> statuses,
                                                         final Pageable pageable);
}
