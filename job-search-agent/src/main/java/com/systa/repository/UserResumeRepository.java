package com.systa.repository;

import com.systa.model.UserResume;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;
import java.util.Optional;

public interface UserResumeRepository extends MongoRepository<UserResume, String> {

    List<UserResume> findByUserIdOrderByVersionDesc(final String userId);

    /** The user's newest version, whatever its marker says - used to number the next upload. */
    Optional<UserResume> findFirstByUserIdOrderByVersionDesc(final String userId);

    // Newest first, so an upload interrupted before the older marker was cleared still reads correctly.
    Optional<UserResume> findFirstByUserIdAndLatestTrueOrderByVersionDesc(final String userId);

    Optional<UserResume> findByUserIdAndVersion(final String userId, final int version);

    @Query("{ 'userId': ?0, 'latest': true, 'version': { '$lt': ?1 } }")
    @Update("{ '$set': { 'latest': false } }")
    void clearLatestBeforeVersion(final String userId, final int version);
}
