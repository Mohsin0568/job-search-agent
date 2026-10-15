package com.systa.repository;

import com.systa.model.ResumeReview;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ResumeReviewRepository extends MongoRepository<ResumeReview, String> {

    List<ResumeReview> findByUserId(final String userId);
}
