package com.systa.repository;

import com.systa.model.UserJobSearchResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserJobSearchResultRepository extends MongoRepository<UserJobSearchResult, String> {

    Optional<UserJobSearchResult> findByUserIdAndCompanyNameAndJob_JobId(
            final String userId, final String companyName, final String jobId);

    List<UserJobSearchResult> findByUserId(final String userId, final Pageable pageable);
}
