package com.systa.repository;

import com.systa.model.TailoredResume;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TailoredResumeRepository extends MongoRepository<TailoredResume, String> {

    List<TailoredResume> findByUserId(final String userId);
}
