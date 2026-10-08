package com.systa.repository;

import com.systa.model.UserJobSearchRun;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserJobSearchRunRepository extends MongoRepository<UserJobSearchRun, String> {
}
