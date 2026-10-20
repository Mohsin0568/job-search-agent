package com.systa.service;

import com.systa.model.JobListing;
import com.systa.model.UserJobSearchResult;
import com.systa.model.UserJobSearchResult.Status;
import com.systa.repository.UserJobSearchResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSearchResultQueryServiceTest {

    private static final String USER_ID = "user-1";
    private static final Instant NOW = Instant.parse("2026-10-20T09:00:00Z");
    private static final UserJobSearchResult RESULT = new UserJobSearchResult("id-1", USER_ID,
            LocalDateTime.of(2026, 8, 18, 12, 0), "Acme Corp",
            new JobListing("J-1", "Engineer", "https://example.com/J-1", "London, UK",
                    "10 Aug 2026", "20 Aug 2026", "£50,000 - £60,000", "LinkedIn", 90));

    @Mock
    private UserJobSearchResultRepository userJobSearchResultRepository;

    private JobSearchResultQueryService service;

    @BeforeEach
    void setUp() {
        service = new JobSearchResultQueryService(userJobSearchResultRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void findForUser_active_requestsTheGivenPageOfJobsNotMarked_newestRunFirst() {
        when(userJobSearchResultRepository.findByUserIdAndStatusNotIn(USER_ID,
                EnumSet.of(Status.APPLIED, Status.NOT_RELEVANT),
                PageRequest.of(2, 25, Sort.by(Sort.Direction.DESC, "jobRunDateTime"))))
                .thenReturn(List.of(RESULT));

        assertThat(service.findForUser(USER_ID, Status.ACTIVE, 2, 25)).containsExactly(RESULT);
    }

    @Test
    void findForUser_marked_requestsTheGivenPageOfJobsWithThatStatus_lastMarkedFirst() {
        final UserJobSearchResult applied = RESULT.withStatus(Status.APPLIED, NOW);
        when(userJobSearchResultRepository.findByUserIdAndStatus(USER_ID, Status.APPLIED,
                PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "statusChangedAt")
                        .and(Sort.by(Sort.Direction.DESC, "jobRunDateTime"))
                        .and(Sort.by(Sort.Direction.DESC, "id")))))
                .thenReturn(List.of(applied));

        assertThat(service.findForUser(USER_ID, Status.APPLIED, 1, 10)).containsExactly(applied);
    }

    @Test
    void updateStatus_savesTheJobWithItsNewStatusAndWhenItChanged() {
        when(userJobSearchResultRepository.findByIdAndUserId("id-1", USER_ID)).thenReturn(Optional.of(RESULT));
        when(userJobSearchResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        final Optional<UserJobSearchResult> updated =
                service.updateStatus(USER_ID, "id-1", Status.NOT_RELEVANT, "  Needs Kubernetes, which I don't have ");

        final UserJobSearchResult expected = new UserJobSearchResult("id-1", USER_ID, RESULT.jobRunDateTime(),
                "Acme Corp", RESULT.job(), Status.NOT_RELEVANT, NOW, "Needs Kubernetes, which I don't have");
        assertThat(updated).contains(expected);
        verify(userJobSearchResultRepository).save(expected);
    }

    @Test
    void updateStatus_toActive_putsAMarkedJobBackOnTheDashboard() {
        when(userJobSearchResultRepository.findByIdAndUserId("id-1", USER_ID))
                .thenReturn(Optional.of(RESULT.withStatus(Status.APPLIED, NOW.minusSeconds(3600))));
        when(userJobSearchResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.updateStatus(USER_ID, "id-1", Status.ACTIVE, null)).hasValueSatisfying(updated -> {
            assertThat(updated.status()).isEqualTo(Status.ACTIVE);
            assertThat(updated.statusChangedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void updateStatus_toTheStatusItAlreadyHas_changesNothing() {
        final UserJobSearchResult applied = RESULT.withStatus(Status.APPLIED, NOW.minusSeconds(3600));
        when(userJobSearchResultRepository.findByIdAndUserId("id-1", USER_ID)).thenReturn(Optional.of(applied));

        assertThat(service.updateStatus(USER_ID, "id-1", Status.APPLIED, null)).contains(applied);
        verify(userJobSearchResultRepository, never()).save(any());
    }

    @Test
    void updateStatus_forAJobTheUserDoesNotHave_isEmpty() {
        when(userJobSearchResultRepository.findByIdAndUserId("someone-elses", USER_ID)).thenReturn(Optional.empty());

        assertThat(service.updateStatus(USER_ID, "someone-elses", Status.APPLIED, null)).isEmpty();
        verify(userJobSearchResultRepository, never()).save(any());
    }

    @Test
    void aResultWithNoStatus_isActive() {
        assertThat(new UserJobSearchResult("id-1", USER_ID, RESULT.jobRunDateTime(), "Acme Corp", RESULT.job(),
                null, null, null).status()).isEqualTo(Status.ACTIVE);
    }

    @Test
    void updateStatus_withABlankReason_keepsNoReason() {
        when(userJobSearchResultRepository.findByIdAndUserId("id-1", USER_ID)).thenReturn(Optional.of(RESULT));
        when(userJobSearchResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.updateStatus(USER_ID, "id-1", Status.NOT_RELEVANT, "   "))
                .map(UserJobSearchResult::notRelevantReason)
                .isEmpty();
    }

    @Test
    void updateStatus_keepsAReasonOnlyForNotRelevant() {
        when(userJobSearchResultRepository.findByIdAndUserId("id-1", USER_ID)).thenReturn(Optional.of(RESULT));
        when(userJobSearchResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.updateStatus(USER_ID, "id-1", Status.APPLIED, "Applied through a recruiter"))
                .hasValueSatisfying(updated -> {
                    assertThat(updated.status()).isEqualTo(Status.APPLIED);
                    assertThat(updated.notRelevantReason()).isNull();
                });
    }

    @Test
    void updateStatus_onANotRelevantJob_withANewReason_savesTheNewReason() {
        when(userJobSearchResultRepository.findByIdAndUserId("id-1", USER_ID)).thenReturn(Optional.of(
                RESULT.withStatus(Status.NOT_RELEVANT, "Too senior", NOW.minusSeconds(3600))));
        when(userJobSearchResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.updateStatus(USER_ID, "id-1", Status.NOT_RELEVANT, "Needs relocation"))
                .map(UserJobSearchResult::notRelevantReason)
                .contains("Needs relocation");
    }

    @Test
    void updateStatus_backToActive_dropsTheReason() {
        when(userJobSearchResultRepository.findByIdAndUserId("id-1", USER_ID)).thenReturn(Optional.of(
                RESULT.withStatus(Status.NOT_RELEVANT, "Too senior", NOW.minusSeconds(3600))));
        when(userJobSearchResultRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.updateStatus(USER_ID, "id-1", Status.ACTIVE, null))
                .map(UserJobSearchResult::notRelevantReason)
                .isEmpty();
    }

    @Test
    void updateStatus_onANotRelevantJob_withTheSameReason_changesNothing() {
        final UserJobSearchResult notRelevant =
                RESULT.withStatus(Status.NOT_RELEVANT, "Too senior", NOW.minusSeconds(3600));
        when(userJobSearchResultRepository.findByIdAndUserId("id-1", USER_ID)).thenReturn(Optional.of(notRelevant));

        assertThat(service.updateStatus(USER_ID, "id-1", Status.NOT_RELEVANT, " Too senior")).contains(notRelevant);
        verify(userJobSearchResultRepository, never()).save(any());
    }
}
