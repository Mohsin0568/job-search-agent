package com.systa.repository;

import com.systa.MongoTestContainer;
import com.systa.model.ResumeReview;
import com.systa.model.ResumeReview.RedFlag;
import com.systa.model.ResumeReview.Status;
import com.systa.model.ResumeReviewResult;
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
class ResumeReviewRepositoryTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-10-15T09:00:00Z");
    private static final ResumeReview PENDING = new ResumeReview("r-1", "user-1", Status.PENDING, 3, null, null, null,
            null, REQUESTED_AT, null);

    @Autowired
    private ResumeReviewRepository repository;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void savesAndReadsBackEveryState() {
        repository.insert(PENDING);
        assertThat(repository.findById("r-1:v3")).contains(PENDING);

        final ResumeReview ready = PENDING.ready(new ResumeReviewResult(64, List.of("Kubernetes", "Terraform"),
                List.of(new RedFlag("Short roles", "Three jobs in two years."))), REQUESTED_AT.plusSeconds(30));
        repository.save(ready);
        assertThat(repository.findById("r-1:v3")).contains(ready);

        // Nothing found is kept as empty lists, not lost.
        final ResumeReview clean = PENDING.ready(new ResumeReviewResult(96, List.of(), List.of()),
                REQUESTED_AT.plusSeconds(30));
        repository.save(clean);
        assertThat(repository.findById("r-1:v3")).contains(clean);

        final ResumeReview failed = PENDING.failed("we couldn't find this job posting", REQUESTED_AT.plusSeconds(20));
        repository.save(failed);
        assertThat(repository.findById("r-1:v3")).contains(failed);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void keepsAReviewForEachVersionOfTheResume() {
        repository.insert(PENDING);
        final ResumeReview nextVersion = new ResumeReview("r-1", "user-1", Status.PENDING, 4, null, null, null, null,
                REQUESTED_AT, null);
        repository.insert(nextVersion);

        assertThat(repository.findById("r-1:v3")).contains(PENDING);
        assertThat(repository.findById("r-1:v4")).contains(nextVersion);
        assertThat(repository.findByUserId("user-1")).containsExactlyInAnyOrder(PENDING, nextVersion);
    }

    // What lets two requests arriving together agree on which of them does the work.
    @Test
    void refusesToInsertASecondRecordForTheSameJobAndResumeVersion() {
        repository.insert(PENDING);

        assertThatThrownBy(() -> repository.insert(PENDING)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void listsOnlyThatUsersReviews() {
        repository.insert(PENDING);
        repository.insert(new ResumeReview("r-2", "user-1", Status.PENDING, 3, null, null, null, null, REQUESTED_AT,
                null));
        repository.insert(new ResumeReview("r-3", "user-2", Status.PENDING, 1, null, null, null, null, REQUESTED_AT,
                null));

        assertThat(repository.findByUserId("user-1")).extracting(ResumeReview::jobResultId)
                .containsExactlyInAnyOrder("r-1", "r-2");
        assertThat(repository.findByUserId("user-with-none")).isEmpty();
    }
}
