package com.systa.repository;

import com.systa.MongoTestContainer;
import com.systa.model.TailoredResume;
import com.systa.model.TailoredResume.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataMongoTest
@Import(MongoTestContainer.class)
class TailoredResumeRepositoryTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-10-13T09:00:00Z");
    private static final TailoredResume PENDING = new TailoredResume("r-1", "user-1", Status.PENDING, 3, null, null,
            null, REQUESTED_AT, null);

    @Autowired
    private TailoredResumeRepository repository;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void savesAndReadsBackEveryState() {
        repository.insert(PENDING);
        assertThat(repository.findById("r-1")).contains(PENDING);

        final TailoredResume ready = PENDING.ready("tailored/user-1/r-1/v3.pdf", "Jane-Doe-Acme-resume.pdf",
                REQUESTED_AT.plusSeconds(40));
        repository.save(ready);
        assertThat(repository.findById("r-1")).contains(ready);

        final TailoredResume failed = PENDING.failed("we couldn't find this job posting", REQUESTED_AT.plusSeconds(20));
        repository.save(failed);
        assertThat(repository.findById("r-1")).contains(failed);
        assertThat(repository.count()).isEqualTo(1);
    }

    // What lets two requests arriving together agree on which of them does the work.
    @Test
    void refusesToInsertASecondRecordForTheSameJob() {
        repository.insert(PENDING);

        assertThatThrownBy(() -> repository.insert(PENDING)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void listsOnlyThatUsersTailoredResumes() {
        repository.insert(PENDING);
        repository.insert(new TailoredResume("r-2", "user-1", Status.PENDING, 3, null, null, null, REQUESTED_AT, null));
        repository.insert(new TailoredResume("r-3", "user-2", Status.PENDING, 1, null, null, null, REQUESTED_AT, null));

        assertThat(repository.findByUserId("user-1")).extracting(TailoredResume::jobResultId)
                .containsExactlyInAnyOrder("r-1", "r-2");
        assertThat(repository.findByUserId("user-with-none")).isEmpty();
    }
}
