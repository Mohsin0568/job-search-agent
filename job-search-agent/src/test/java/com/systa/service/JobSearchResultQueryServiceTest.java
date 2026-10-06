package com.systa.service;

import com.systa.model.JobListing;
import com.systa.model.UserJobSearchResult;
import com.systa.repository.UserJobSearchResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSearchResultQueryServiceTest {

    private static final String USER_ID = "user-1";

    @Mock
    private UserJobSearchResultRepository userJobSearchResultRepository;

    @InjectMocks
    private JobSearchResultQueryService service;

    @Test
    void findForUser_requestsTheGivenPage_newestRunFirst() {
        final UserJobSearchResult result = new UserJobSearchResult("id-1", USER_ID,
                LocalDateTime.of(2026, 8, 18, 12, 0), "Acme Corp",
                new JobListing("J-1", "Engineer", "https://example.com/J-1", "London, UK",
                        "10 Aug 2026", "20 Aug 2026", "£50,000 - £60,000", "LinkedIn", 90));
        when(userJobSearchResultRepository.findByUserId(USER_ID,
                PageRequest.of(2, 25, Sort.by(Sort.Direction.DESC, "jobRunDateTime"))))
                .thenReturn(List.of(result));

        assertThat(service.findForUser(USER_ID, 2, 25)).containsExactly(result);
    }
}
