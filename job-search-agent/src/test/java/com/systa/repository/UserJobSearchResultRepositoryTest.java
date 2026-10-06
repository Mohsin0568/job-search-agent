package com.systa.repository;

import com.systa.MongoTestContainer;
import com.systa.model.JobListing;
import com.systa.model.UserJobSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import(MongoTestContainer.class)
class UserJobSearchResultRepositoryTest {

    private static final String USER_ID = "user-1";
    private static final String OTHER_USER_ID = "user-2";
    private static final LocalDateTime FIRST_RUN = LocalDateTime.of(2026, 8, 18, 12, 0);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "jobRunDateTime");

    @Autowired
    private UserJobSearchResultRepository repository;

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void findByUserId_returnsOnlyThatUsersResults() {
        repository.saveAll(List.of(
                result("r-1", USER_ID, FIRST_RUN, "Acme Corp", "J-1"),
                result("r-2", OTHER_USER_ID, FIRST_RUN, "Acme Corp", "J-1"),
                result("r-3", USER_ID, FIRST_RUN, "Globex", "J-2")));

        assertThat(repository.findByUserId(USER_ID, Pageable.unpaged()))
                .extracting(UserJobSearchResult::id)
                .containsExactlyInAnyOrder("r-1", "r-3");
        assertThat(repository.findByUserId("user-with-no-results", Pageable.unpaged())).isEmpty();
    }

    @Test
    void findByUserId_pagesThroughResults_newestRunFirst() {
        repository.saveAll(List.of(
                result("oldest", USER_ID, FIRST_RUN, "Acme Corp", "J-1"),
                result("newest", USER_ID, FIRST_RUN.plusDays(2), "Acme Corp", "J-3"),
                result("middle", USER_ID, FIRST_RUN.plusDays(1), "Acme Corp", "J-2"),
                result("other-user", OTHER_USER_ID, FIRST_RUN.plusDays(3), "Acme Corp", "J-4")));

        assertThat(repository.findByUserId(USER_ID, PageRequest.of(0, 2, NEWEST_FIRST)))
                .extracting(UserJobSearchResult::id)
                .containsExactly("newest", "middle");
        assertThat(repository.findByUserId(USER_ID, PageRequest.of(1, 2, NEWEST_FIRST)))
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
        repository.save(saved);

        assertThat(repository.findById("r-1")).contains(saved);
    }

    private static UserJobSearchResult result(final String id, final String userId, final LocalDateTime runAt,
                                              final String companyName, final String jobId) {
        return new UserJobSearchResult(id, userId, runAt, companyName,
                new JobListing(jobId, "Engineer", "https://example.com/" + jobId, "London, UK",
                        "10 Aug 2026", "20 Aug 2026", "£50,000 - £60,000", "LinkedIn", 90));
    }
}
