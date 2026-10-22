package com.systa.service;

import com.systa.config.AccessProperties;
import com.systa.exception.IdentityProviderUnavailableException;
import com.systa.model.UserAccess;
import com.systa.model.UserAccess.Status;
import com.systa.repository.UserAccessRepository;
import com.systa.security.CognitoUserService;
import com.systa.security.CognitoUserService.CognitoUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessServiceTest {

    private static final String USER_ID = "cognito-sub-1";
    private static final String TOKEN = "access-token";
    private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");

    @Mock
    private UserAccessRepository repository;

    @Mock
    private CognitoUserService cognitoUserService;

    private AccessService service(final boolean inviteOnly) {
        return new AccessService(repository, cognitoUserService, new AccessProperties(inviteOnly),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void cognitoEmail(final String email) {
        when(cognitoUserService.findUser(USER_ID, TOKEN))
                .thenReturn(new CognitoUser(Optional.ofNullable(email), Optional.of("Jane Doe")));
    }

    private UserAccess saved() {
        final ArgumentCaptor<UserAccess> captor = ArgumentCaptor.forClass(UserAccess.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void letsEveryoneIn_whenNotInviteOnly_withoutLookingAnythingUp() {
        assertThat(service(false).statusOf(USER_ID, TOKEN)).isEqualTo(Status.APPROVED);
        assertThat(service(false).mayUseApp(USER_ID)).isTrue();

        verifyNoInteractions(repository, cognitoUserService);
    }

    @Test
    void answersFromTheUsersRecord_withoutAskingCognito() {
        when(repository.findByUserId(USER_ID))
                .thenReturn(Optional.of(new UserAccess("jane@example.com", USER_ID, Status.REVOKED, NOW)));

        assertThat(service(true).statusOf(USER_ID, TOKEN)).isEqualTo(Status.REVOKED);

        verifyNoInteractions(cognitoUserService);
        verify(repository, never()).save(any());
    }

    @Test
    void takesUpAnInvite_forTheUsersVerifiedEmail_whateverItsCase() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        cognitoEmail(" Jane@Example.com");
        // As inserted by hand: just the email and the status.
        when(repository.findById("jane@example.com"))
                .thenReturn(Optional.of(new UserAccess("jane@example.com", null, Status.APPROVED, null)));

        assertThat(service(true).statusOf(USER_ID, TOKEN)).isEqualTo(Status.APPROVED);

        assertThat(saved()).isEqualTo(new UserAccess("jane@example.com", USER_ID, Status.APPROVED, NOW));
    }

    @Test
    void putsAUserWithoutAnInviteOnTheWaitlist() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        cognitoEmail("stranger@example.com");
        when(repository.findById("stranger@example.com")).thenReturn(Optional.empty());

        assertThat(service(true).statusOf(USER_ID, TOKEN)).isEqualTo(Status.PENDING);

        assertThat(saved()).isEqualTo(new UserAccess("stranger@example.com", USER_ID, Status.PENDING, NOW));
    }

    @Test
    void keepsAccessWithTheEmail_whenItMovesToANewAccount() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        cognitoEmail("jane@example.com");
        when(repository.findById("jane@example.com"))
                .thenReturn(Optional.of(new UserAccess("jane@example.com", "old-account", Status.APPROVED, NOW)));

        assertThat(service(true).statusOf(USER_ID, TOKEN)).isEqualTo(Status.APPROVED);
        assertThat(saved().userId()).isEqualTo(USER_ID);
    }

    @Test
    void doesNotLetIn_noRecord_whenCognitoHasNoVerifiedEmail() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        cognitoEmail(null);

        assertThat(service(true).statusOf(USER_ID, TOKEN)).isEqualTo(Status.PENDING);
        verify(repository, never()).save(any());
    }

    @Test
    void readsAHandEditedRecordWithNoStatus_asNotLetIn() {
        when(repository.findByUserId(USER_ID))
                .thenReturn(Optional.of(new UserAccess("jane@example.com", USER_ID, null, NOW)));

        assertThat(service(true).statusOf(USER_ID, TOKEN)).isEqualTo(Status.PENDING);
        assertThat(service(true).mayUseApp(USER_ID)).isFalse();
    }

    @Test
    void letsTheDailyJobSearchOnlyForApprovedUsers_neverAskingCognito() {
        when(repository.findByUserId("approved"))
                .thenReturn(Optional.of(new UserAccess("a@example.com", "approved", Status.APPROVED, NOW)));
        when(repository.findByUserId("waiting"))
                .thenReturn(Optional.of(new UserAccess("w@example.com", "waiting", Status.PENDING, NOW)));
        when(repository.findByUserId("unknown")).thenReturn(Optional.empty());

        assertThat(service(true).mayUseApp("approved")).isTrue();
        assertThat(service(true).mayUseApp("waiting")).isFalse();
        assertThat(service(true).mayUseApp("unknown")).isFalse();
        verifyNoInteractions(cognitoUserService);
    }

    @Test
    void passesOnACognitoFailure_savingNothing() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(cognitoUserService.findUser(USER_ID, TOKEN))
                .thenThrow(new IdentityProviderUnavailableException(USER_ID, null));

        assertThatThrownBy(() -> service(true).statusOf(USER_ID, TOKEN))
                .isInstanceOf(IdentityProviderUnavailableException.class);
        verify(repository, never()).save(any());
    }
}
