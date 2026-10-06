package com.systa.repository;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Limit;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SuggestionRepositoryPrefixTest {

    private final CompanyRepository repository = Mockito.mock(CompanyRepository.class, Mockito.CALLS_REAL_METHODS);

    @Test
    void prefixIsAnchoredAtTheStartOfAKey() {
        when(repository.findBySearchKeysMatching(anyString(), any(Limit.class))).thenReturn(List.of());

        repository.findBySearchKeyPrefix("del", Limit.of(5));

        verify(repository).findBySearchKeysMatching("^del", Limit.of(5));
    }

    @Test
    void rejectsPrefixesThatCouldInjectRegex() {
        assertThatThrownBy(() -> repository.findBySearchKeyPrefix(".*", Limit.of(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.findBySearchKeyPrefix("c++", Limit.of(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.findBySearchKeyPrefix("Del", Limit.of(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
