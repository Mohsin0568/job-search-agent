package com.systa.repository;

import com.systa.MongoTestContainer;
import com.systa.model.CandidateProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(MongoTestContainer.class)
class CandidateProfileRepositoryTest {

    @Autowired
    private CandidateProfileRepository repository;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void findByUserId_returnsThatUsersProfile_notAnotherUsers() {
        final CandidateProfile mine = profile("p-1", "user-1", "Backend Engineer");
        final CandidateProfile theirs = profile("p-2", "user-2", "Data Scientist");
        repository.saveAll(List.of(mine, theirs));

        assertThat(repository.findByUserId("user-1")).contains(mine);
        assertThat(repository.findByUserId("user-2")).contains(theirs);
    }

    @Test
    void findByUserId_isEmptyForAUserWithoutAProfile() {
        repository.save(profile("p-1", "user-1", "Backend Engineer"));

        assertThat(repository.findByUserId("user-3")).isEmpty();
    }

    @Test
    void profileWithoutARecencyWindow_isReadBackWithNull() {
        repository.save(new CandidateProfile("p-1", "user-1", "user1@example.com", "Backend Engineer",
                List.of("Java"), "Builds APIs", List.of("Acme Corp"), null));

        assertThat(repository.findByUserId("user-1"))
                .hasValueSatisfying(profile -> assertThat(profile.recencyWindowDays()).isNull());
    }

    private static CandidateProfile profile(final String id, final String userId, final String desiredRole) {
        return new CandidateProfile(id, userId, userId + "@example.com", desiredRole,
                List.of("Java", "Spring"), "Builds APIs", List.of("Acme Corp"), 14);
    }
}
