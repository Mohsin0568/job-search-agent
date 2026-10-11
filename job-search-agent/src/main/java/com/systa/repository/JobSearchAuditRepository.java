package com.systa.repository;

import com.systa.model.JobSearchAudit;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface JobSearchAuditRepository extends MongoRepository<JobSearchAudit, String> {
}
