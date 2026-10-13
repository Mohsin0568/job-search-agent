package com.systa.repository;

import com.systa.model.TailoredResume;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TailoredResumeRepository extends MongoRepository<TailoredResume, String> {
}
