package com.systa.repository;

import com.systa.model.JobSearchRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface JobSearchRunRepository extends MongoRepository<JobSearchRun, String> {

    List<JobSearchRun> findByUserId(String userId, Pageable pageable);

    Optional<JobSearchRun> findByIdAndUserId(String id, String userId);
}
