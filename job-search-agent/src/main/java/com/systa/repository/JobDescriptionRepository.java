package com.systa.repository;

import com.systa.model.JobDescription;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JobDescriptionRepository extends MongoRepository<JobDescription, String> {
}
