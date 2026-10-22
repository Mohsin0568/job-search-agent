package com.systa.service;

import com.systa.config.AccessProperties;
import com.systa.model.CandidateProfile;
import com.systa.model.UserAccess;
import com.systa.model.UserAccess.Status;
import com.systa.repository.CandidateProfileRepository;
import com.systa.repository.UserAccessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExistingUsersAccessSeederTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");

    @Mock
    private CandidateProfileRepository profiles;

    @Mock
    private UserAccessRepository access;

    private void run(final boolean inviteOnly) {
        new ExistingUsersAccessSeeder(profiles, access, new AccessProperties(inviteOnly), Clock.fixed(NOW, ZoneOffset.UTC))
                .run(null);
    }

    private static CandidateProfile profile(final String userId, final String email) {
        return new CandidateProfile("p-" + userId, userId, email, null, "Dev", List.of(), null, List.of("Acme"), 7);
    }

    private List<UserAccess> saved() {
        final ArgumentCaptor<UserAccess> captor = ArgumentCaptor.forClass(UserAccess.class);
        verify(access, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void letsInEveryExistingUserWithoutARecord() {
        when(profiles.findAll()).thenReturn(List.of(profile("ada", "Ada@Example.com"), profile("bob", "bob@example.com")));
        when(access.findByUserId(anyString())).thenReturn(Optional.empty());
        when(access.findById(anyString())).thenReturn(Optional.empty());

        run(true);

        assertThat(saved()).containsExactly(
                new UserAccess("ada@example.com", "ada", Status.APPROVED, NOW),
                new UserAccess("bob@example.com", "bob", Status.APPROVED, NOW));
    }

    @Test
    void leavesUsersWhoAlreadyHaveARecord_soARevokedUserStaysOut() {
        when(profiles.findAll()).thenReturn(List.of(profile("ada", "ada@example.com")));
        when(access.findByUserId("ada"))
                .thenReturn(Optional.of(new UserAccess("ada@example.com", "ada", Status.REVOKED, NOW)));

        run(true);

        verify(access, never()).save(any());
    }

    @Test
    void takesUpAnInviteAsItStands_andLeavesAnotherAccountsRecord() {
        when(profiles.findAll()).thenReturn(List.of(profile("ada", "ada@example.com"), profile("bob", "bob@example.com")));
        when(access.findByUserId(anyString())).thenReturn(Optional.empty());
        when(access.findById("ada@example.com"))
                .thenReturn(Optional.of(new UserAccess("ada@example.com", null, Status.REVOKED, null)));
        when(access.findById("bob@example.com"))
                .thenReturn(Optional.of(new UserAccess("bob@example.com", "other-account", Status.PENDING, NOW)));

        run(true);

        assertThat(saved()).containsExactly(new UserAccess("ada@example.com", "ada", Status.REVOKED, NOW));
    }

    @Test
    void skipsAUserWithNoEmail() {
        when(profiles.findAll()).thenReturn(List.of(profile("ada", null)));
        when(access.findByUserId("ada")).thenReturn(Optional.empty());

        run(true);

        verify(access, never()).save(any());
    }

    @Test
    void doesNothing_whenNotInviteOnly() {
        run(false);

        verifyNoInteractions(profiles, access);
    }
}
