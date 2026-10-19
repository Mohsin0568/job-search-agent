package com.systa.repository;

import com.systa.model.JobSearchAudit;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;

public interface JobSearchAuditRepository extends MongoRepository<JobSearchAudit, String> {

    /**
     * The user's audits from those runs, counts only: each source's lists of filtered-out and duplicate jobs
     * are left behind (and read back as null), since the search history shows how many, not which.
     */
    @Query(value = "{ 'userId': ?0, 'runId': { $in: ?1 } }",
            fields = "{ 'sources.filteredOutJobs': 0, 'sources.duplicateJobs': 0 }")
    List<JobSearchAudit> findCountsByUserIdAndRunIdIn(String userId, Collection<String> runIds);
}
