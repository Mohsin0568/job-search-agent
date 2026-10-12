package com.systa.repository;

import com.systa.MongoTestContainer;
import com.systa.model.UserResume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataMongoTest
@Import(MongoTestContainer.class)
class UserResumeRepositoryTest {

    private static final String USER_ID = "user-1";
    private static final String OTHER_USER_ID = "user-2";

    @Autowired
    private UserResumeRepository repository;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void listsAUsersVersions_newestFirst() {
        repository.saveAll(List.of(resume(USER_ID, 1, false), resume(USER_ID, 3, true), resume(USER_ID, 2, false),
                resume(OTHER_USER_ID, 1, true)));

        assertThat(repository.findByUserIdOrderByVersionDesc(USER_ID)).extracting(UserResume::version)
                .containsExactly(3, 2, 1);
        assertThat(repository.findByUserIdOrderByVersionDesc("user-with-no-resume")).isEmpty();
    }

    @Test
    void findsTheNewestVersion_andTheOneMarkedLatest() {
        repository.saveAll(List.of(resume(USER_ID, 1, false), resume(USER_ID, 2, true), resume(OTHER_USER_ID, 7, true)));

        assertThat(repository.findFirstByUserIdOrderByVersionDesc(USER_ID)).map(UserResume::version).contains(2);
        assertThat(repository.findFirstByUserIdAndLatestTrueOrderByVersionDesc(USER_ID)).map(UserResume::version)
                .contains(2);
        assertThat(repository.findFirstByUserIdAndLatestTrueOrderByVersionDesc("user-with-no-resume")).isEmpty();
    }

    @Test
    void readsTheNewestMarkedVersion_whenAnInterruptedUploadLeftTwoMarked() {
        repository.saveAll(List.of(resume(USER_ID, 1, true), resume(USER_ID, 2, true)));

        assertThat(repository.findFirstByUserIdAndLatestTrueOrderByVersionDesc(USER_ID)).map(UserResume::version)
                .contains(2);
    }

    @Test
    void findsOneVersion_onlyForTheUserItBelongsTo() {
        repository.saveAll(List.of(resume(USER_ID, 1, true), resume(OTHER_USER_ID, 2, true)));

        assertThat(repository.findByUserIdAndVersion(USER_ID, 1)).isPresent();
        assertThat(repository.findByUserIdAndVersion(USER_ID, 2)).isEmpty();
    }

    @Test
    void clearsTheLatestMarkerOnOlderVersionsOfThatUserOnly() {
        repository.saveAll(List.of(resume(USER_ID, 1, true), resume(USER_ID, 2, true), resume(USER_ID, 3, true),
                resume(OTHER_USER_ID, 1, true)));

        repository.clearLatestBeforeVersion(USER_ID, 3);

        assertThat(repository.findByUserIdOrderByVersionDesc(USER_ID)).extracting(UserResume::version, UserResume::latest)
                .containsExactly(tuple(3, true), tuple(2, false), tuple(1, false));
        assertThat(repository.findByUserIdAndVersion(OTHER_USER_ID, 1)).map(UserResume::latest).contains(true);
    }

    @Test
    void refusesASecondResumeWithTheSameUserAndVersion() {
        repository.save(resume(USER_ID, 1, true));

        assertThatThrownBy(() -> repository.save(new UserResume("another-id", USER_ID, 1, true, "key", "cv.pdf",
                "application/pdf", 10, Instant.parse("2026-10-12T09:00:00Z"), "text")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void savesAndReadsBackEveryField() {
        final UserResume resume = resume(USER_ID, 1, true);

        repository.save(resume);

        assertThat(repository.findById(resume.id())).contains(resume);
    }

    private static org.assertj.core.groups.Tuple tuple(final Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }

    private static UserResume resume(final String userId, final int version, final boolean latest) {
        return new UserResume(userId + "-v" + version, userId, version, latest,
                "resumes/%s/v%d/id.pdf".formatted(userId, version), "cv.pdf", "application/pdf", 2048,
                Instant.parse("2026-10-12T09:00:00Z"), "Senior Java Developer");
    }
}
