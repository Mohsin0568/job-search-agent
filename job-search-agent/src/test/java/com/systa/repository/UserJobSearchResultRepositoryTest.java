package com.systa.repository;

import com.systa.MongoTestContainer;
import com.systa.model.JobListing;
import com.systa.model.UserJobSearchResult;
import com.systa.model.UserJobSearchResult.Status;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(MongoTestContainer.class)
class UserJobSearchResultRepositoryTest {

    private static final String USER_ID = "user-1";
    private static final String OTHER_USER_ID = "user-2";
    private static final LocalDateTime FIRST_RUN = LocalDateTime.of(2026, 8, 18, 12, 0);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "jobRunDateTime");
    private static final EnumSet<Status> MARKED = EnumSet.of(Status.APPLIED, Status.NOT_RELEVANT);
    private static final Instant MARKED_AT = Instant.parse("2026-08-20T09:00:00Z");

    @Autowired
    private UserJobSearchResultRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void findByUserIdAndStatusNotIn_returnsOnlyThatUsersUnmarkedResults() {
        repository.saveAll(List.of(
                result("r-1", USER_ID, FIRST_RUN, "Acme Corp", "J-1"),
                result("r-2", OTHER_USER_ID, FIRST_RUN, "Acme Corp", "J-1"),
                result("r-3", USER_ID, FIRST_RUN, "Globex", "J-2"),
                result("applied", USER_ID, FIRST_RUN, "Globex", "J-3").withStatus(Status.APPLIED, MARKED_AT),
                result("not-relevant", USER_ID, FIRST_RUN, "Globex", "J-4")
                        .withStatus(Status.NOT_RELEVANT, MARKED_AT)));

        assertThat(repository.findByUserIdAndStatusNotIn(USER_ID, MARKED, Pageable.unpaged()))
                .extracting(UserJobSearchResult::id)
                .containsExactlyInAnyOrder("r-1", "r-3");
        assertThat(repository.findByUserIdAndStatusNotIn("user-with-no-results", MARKED, Pageable.unpaged()))
                .isEmpty();
    }

    @Test
    void findByUserIdAndStatusNotIn_findsResultsSavedBeforeStatusesExisted_asActive() {
        mongoTemplate.insert(new Document("_id", "before-statuses")
                .append("userId", USER_ID)
                .append("jobRunDateTime", FIRST_RUN)
                .append("companyName", "Acme Corp")
                .append("job", new Document("jobId", "J-1").append("jobTitle", "Engineer")),
                "user_job_search_result");

        assertThat(repository.findByUserIdAndStatusNotIn(USER_ID, MARKED, Pageable.unpaged()))
                .singleElement()
                .satisfies(found -> {
                    assertThat(found.id()).isEqualTo("before-statuses");
                    assertThat(found.status()).isEqualTo(Status.ACTIVE);
                    assertThat(found.statusChangedAt()).isNull();
                });
    }

    @Test
    void findByUserIdAndStatus_returnsOnlyThatUsersResultsWithThatStatus() {
        repository.saveAll(List.of(
                result("active", USER_ID, FIRST_RUN, "Acme Corp", "J-1"),
                result("applied", USER_ID, FIRST_RUN, "Acme Corp", "J-2").withStatus(Status.APPLIED, MARKED_AT),
                result("not-relevant", USER_ID, FIRST_RUN, "Acme Corp", "J-3")
                        .withStatus(Status.NOT_RELEVANT, MARKED_AT),
                result("other-users", OTHER_USER_ID, FIRST_RUN, "Acme Corp", "J-2")
                        .withStatus(Status.APPLIED, MARKED_AT)));

        assertThat(repository.findByUserIdAndStatus(USER_ID, Status.APPLIED, Pageable.unpaged()))
                .extracting(UserJobSearchResult::id)
                .containsExactly("applied");
        assertThat(repository.findByUserIdAndStatus(USER_ID, Status.NOT_RELEVANT, Pageable.unpaged()))
                .extracting(UserJobSearchResult::id)
                .containsExactly("not-relevant");
    }

    @Test
    void findByIdAndUserId_findsAResultOnlyForTheUserItBelongsTo() {
        repository.saveAll(List.of(
                result("r-1", USER_ID, FIRST_RUN, "Acme Corp", "J-1"),
                result("r-2", OTHER_USER_ID, FIRST_RUN, "Acme Corp", "J-1")));

        assertThat(repository.findByIdAndUserId("r-1", USER_ID)).map(UserJobSearchResult::id).contains("r-1");
        assertThat(repository.findByIdAndUserId("r-2", USER_ID)).isEmpty();
        assertThat(repository.findByIdAndUserId("no-such-result", USER_ID)).isEmpty();
    }

    @Test
    void findByUserIdAndStatusNotIn_pagesThroughResults_newestRunFirst() {
        repository.saveAll(List.of(
                result("oldest", USER_ID, FIRST_RUN, "Acme Corp", "J-1"),
                result("newest", USER_ID, FIRST_RUN.plusDays(2), "Acme Corp", "J-3"),
                result("middle", USER_ID, FIRST_RUN.plusDays(1), "Acme Corp", "J-2"),
                result("other-user", OTHER_USER_ID, FIRST_RUN.plusDays(3), "Acme Corp", "J-4")));

        assertThat(repository.findByUserIdAndStatusNotIn(USER_ID, MARKED, PageRequest.of(0, 2, NEWEST_FIRST)))
                .extracting(UserJobSearchResult::id)
                .containsExactly("newest", "middle");
        assertThat(repository.findByUserIdAndStatusNotIn(USER_ID, MARKED, PageRequest.of(1, 2, NEWEST_FIRST)))
                .extracting(UserJobSearchResult::id)
                .containsExactly("oldest");
    }

    @Test
    void findByUserIdAndCompanyNameAndJobId_needsAllThreeToMatch() {
        repository.saveAll(List.of(
                result("r-1", USER_ID, FIRST_RUN, "Acme Corp", "J-1"),
                result("r-2", OTHER_USER_ID, FIRST_RUN, "Acme Corp", "J-1")));

        assertThat(repository.findByUserIdAndCompanyNameAndJob_JobId(USER_ID, "Acme Corp", "J-1"))
                .map(UserJobSearchResult::id)
                .contains("r-1");
        assertThat(repository.findByUserIdAndCompanyNameAndJob_JobId(USER_ID, "Globex", "J-1")).isEmpty();
        assertThat(repository.findByUserIdAndCompanyNameAndJob_JobId(USER_ID, "Acme Corp", "J-2")).isEmpty();
        assertThat(repository.findByUserIdAndCompanyNameAndJob_JobId("user-3", "Acme Corp", "J-1")).isEmpty();
    }

    @Test
    void savedResult_isReadBackUnchanged() {
        final UserJobSearchResult saved = result("r-1", USER_ID, FIRST_RUN, "Acme Corp", "J-1");
        final UserJobSearchResult marked = result("r-2", USER_ID, FIRST_RUN, "Acme Corp", "J-2")
                .withStatus(Status.NOT_RELEVANT, "Too senior", MARKED_AT);
        repository.saveAll(List.of(saved, marked));

        assertThat(repository.findById("r-1")).contains(saved);
        assertThat(repository.findById("r-2")).contains(marked);
    }

    private static UserJobSearchResult result(final String id, final String userId, final LocalDateTime runAt,
                                              final String companyName, final String jobId) {
        return new UserJobSearchResult(id, userId, runAt, companyName,
                new JobListing(jobId, "Engineer", "https://example.com/" + jobId, "London, UK",
                        "10 Aug 2026", "20 Aug 2026", "£50,000 - £60,000", "LinkedIn", 90));
    }
}
