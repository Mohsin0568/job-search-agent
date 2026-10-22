package com.systa.repository;

import com.systa.MongoTestContainer;
import com.systa.model.UserAccess;
import com.systa.model.UserAccess.Status;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataMongoTest
@Import(MongoTestContainer.class)
class UserAccessRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");

    @Autowired
    private UserAccessRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void readsAnInviteInsertedByHand() {
        mongoTemplate.getCollection("user_access")
                .insertOne(new Document("_id", "jane@example.com").append("status", "APPROVED"));

        assertThat(repository.findById("jane@example.com"))
                .contains(new UserAccess("jane@example.com", null, Status.APPROVED, null));
    }

    @Test
    void findsAUsersRecordByTheirUserId() {
        final UserAccess access = new UserAccess("jane@example.com", "sub-1", Status.PENDING, NOW);
        repository.save(access);

        assertThat(repository.findByUserId("sub-1")).contains(access);
        assertThat(repository.findByUserId("sub-2")).isEmpty();
    }

    @Test
    void allowsManyInvitesNotYetTakenUp_butOneRecordPerUser() {
        repository.save(new UserAccess("a@example.com", null, Status.APPROVED, null));
        repository.save(new UserAccess("b@example.com", null, Status.APPROVED, null));
        repository.save(new UserAccess("c@example.com", "sub-1", Status.APPROVED, NOW));

        assertThat(repository.count()).isEqualTo(3);
        assertThatThrownBy(() -> repository.save(new UserAccess("d@example.com", "sub-1", Status.PENDING, NOW)))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
