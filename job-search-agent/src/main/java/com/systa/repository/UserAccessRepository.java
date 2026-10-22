package com.systa.repository;

import com.systa.model.UserAccess;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserAccessRepository extends MongoRepository<UserAccess, String> {

    Optional<UserAccess> findByUserId(String userId);
}
