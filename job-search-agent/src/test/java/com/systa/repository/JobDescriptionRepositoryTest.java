package com.systa.repository;

import com.systa.MongoTestContainer;
import com.systa.model.JobDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(MongoTestContainer.class)
class JobDescriptionRepositoryTest {

    @Autowired
    private JobDescriptionRepository repository;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void keepsOneDescriptionPerJobResult_readBackByItsId() {
        final JobDescription description = new JobDescription("r-1", "user-1", "https://careers.acme.example/jobs/J-1",
                "Builds payment APIs.", List.of("Java", "Spring Boot"), Instant.parse("2026-10-13T09:00:00Z"));

        repository.save(description);
        repository.save(description);

        assertThat(repository.findById("r-1")).contains(description);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById("r-2")).isEmpty();
    }
}
