package com.systa.repository;

import com.systa.model.LlmUsage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface LlmUsageRepository extends MongoRepository<LlmUsage, String> {

    List<LlmUsage> findByUserIdOrderByCalledAtDesc(String userId);

    List<LlmUsage> findByRunId(String runId);
}
