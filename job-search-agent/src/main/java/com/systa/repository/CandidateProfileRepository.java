package com.systa.repository;

import com.systa.model.CandidateProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CandidateProfileRepository extends MongoRepository<CandidateProfile, String> {

    Optional<CandidateProfile> findByUserId(final String userId);
}
