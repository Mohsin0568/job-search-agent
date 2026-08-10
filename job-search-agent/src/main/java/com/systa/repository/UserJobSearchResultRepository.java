package com.systa.repository;

import com.systa.model.UserJobSearchResult;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserJobSearchResultRepository extends MongoRepository<UserJobSearchResult, String> {

    Optional<UserJobSearchResult> findByUserIdAndCompanyName(final String userId, final String companyName);
}
