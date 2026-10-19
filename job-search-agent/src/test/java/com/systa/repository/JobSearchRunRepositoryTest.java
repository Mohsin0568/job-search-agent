package com.systa.repository;

import com.systa.MongoTestContainer;
import com.systa.model.JobSearchRun;
import com.systa.model.UserJobSearchRun.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(MongoTestContainer.class)
class JobSearchRunRepositoryTest {

    private static final Instant FIRST_RUN = Instant.parse("2026-10-08T02:00:00Z");

    @Autowired
    private JobSearchRunRepository repository;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void savesAndReadsBackARun() {
        final JobSearchRun run = run("run-1", "user-1", 0);

        repository.save(run);

        assertThat(repository.findById("run-1")).contains(run);
    }

    @Test
    void pagesThroughOnlyThatUsersRuns_inTheOrderAsked() {
        repository.saveAll(List.of(run("run-1", "user-1", 0), run("run-2", "user-1", 1), run("run-3", "user-1", 2),
                run("someone-elses", "user-2", 3)));
        final Sort newestFirst = Sort.by(Sort.Order.desc("startedAt"));

        assertThat(repository.findByUserId("user-1", PageRequest.of(0, 2, newestFirst)))
                .extracting(JobSearchRun::id).containsExactly("run-3", "run-2");
        assertThat(repository.findByUserId("user-1", PageRequest.of(1, 2, newestFirst)))
                .extracting(JobSearchRun::id).containsExactly("run-1");
    }

    @Test
    void findsARunOnlyForItsOwnUser() {
        repository.save(run("run-1", "user-1", 0));

        assertThat(repository.findByIdAndUserId("run-1", "user-1")).isPresent();
        assertThat(repository.findByIdAndUserId("run-1", "user-2")).isEmpty();
    }

    private static JobSearchRun run(final String id, final String userId, final int daysLater) {
        final Instant startedAt = FIRST_RUN.plusSeconds(daysLater * 86_400L);
        return new JobSearchRun(id, userId, "2026-10-0" + (8 + daysLater), startedAt, startedAt.plusSeconds(90),
                Status.SUCCEEDED, 3, 0);
    }
}
