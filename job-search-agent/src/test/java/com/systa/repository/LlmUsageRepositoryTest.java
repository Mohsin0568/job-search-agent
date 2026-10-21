package com.systa.repository;

import com.systa.MongoTestContainer;
import com.systa.model.LlmUsage;
import com.systa.model.LlmUsage.Operation;
import com.systa.model.LlmUsage.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(MongoTestContainer.class)
class LlmUsageRepositoryTest {

    @Autowired
    private LlmUsageRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void savesAndReadsBackAUsage() {
        final LlmUsage usage = usage("u-1", "user-1", "run-1", Instant.parse("2026-10-21T09:00:00Z"));

        repository.save(usage);

        assertThat(repository.findById("u-1")).contains(usage);
    }

    @Test
    void findsTheUsersUsage_latestFirst() {
        repository.save(usage("u-1", "user-1", "run-1", Instant.parse("2026-10-20T09:00:00Z")));
        repository.save(usage("u-2", "user-1", "run-2", Instant.parse("2026-10-21T09:00:00Z")));
        repository.save(usage("u-3", "user-2", "run-3", Instant.parse("2026-10-21T10:00:00Z")));

        assertThat(repository.findByUserIdOrderByCalledAtDesc("user-1")).extracting(LlmUsage::id)
                .containsExactly("u-2", "u-1");
    }

    @Test
    void findsARunsUsage() {
        repository.save(usage("u-1", "user-1", "run-1", Instant.parse("2026-10-20T09:00:00Z")));
        repository.save(usage("u-2", "user-1", "run-2", Instant.parse("2026-10-21T09:00:00Z")));

        assertThat(repository.findByRunId("run-1")).extracting(LlmUsage::id).containsExactly("u-1");
    }

    @Test
    void indexesUsageByUserAndTime_byRun_andByRequest() {
        assertThat(mongoTemplate.indexOps(LlmUsage.class).getIndexInfo()).extracting(IndexInfo::getName)
                .contains("user_calledAt", "runId", "requestId");
    }

    private static LlmUsage usage(final String id, final String userId, final String runId, final Instant calledAt) {
        return new LlmUsage(id, "req-" + id, userId, Operation.JOB_SEARCH, "gpt-4.1", 1_000, 200, 1_200, 300,
                runId, "Acme Corp", Status.SUCCEEDED, null, calledAt, 4_200);
    }
}
