package com.systa.service;

import com.systa.exception.IdentityProviderUnavailableException;
import com.systa.model.CandidateProfile;
import com.systa.model.CandidateProfileDto;
import com.systa.repository.CandidateProfileRepository;
import com.systa.security.CognitoUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateProfileServiceTest {

    private static final String USER_ID = "cognito-sub-123";
    private static final String ACCESS_TOKEN = "access-token";
    private static final CandidateProfileDto DETAILS =
            new CandidateProfileDto("Senior Java Developer", List.of("Java"), null, List.of("Acme"), 14);

    @Mock
    private CandidateProfileRepository repository;

    @Mock
    private CognitoUserService cognitoUserService;

    private CandidateProfileService service() {
        return new CandidateProfileService(repository, cognitoUserService);
    }

    private void returnSavedProfile() {
        when(repository.save(any(CandidateProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void firstSave_createsProfileWithEmailFromCognito() {
        when(cognitoUserService.findVerifiedEmail(USER_ID, ACCESS_TOKEN)).thenReturn(Optional.of("jane@example.com"));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        returnSavedProfile();

        final CandidateProfile saved = service().save(USER_ID, ACCESS_TOKEN, DETAILS);

        assertThat(saved.id()).isNotBlank();
        assertThat(saved.userId()).isEqualTo(USER_ID);
        assertThat(saved.email()).isEqualTo("jane@example.com");
        assertThat(saved.companyPreferences()).containsExactly("Acme");
    }

    @Test
    void laterSave_keepsIdAndRefreshesEmail() {
        when(cognitoUserService.findVerifiedEmail(USER_ID, ACCESS_TOKEN)).thenReturn(Optional.of("new@example.com"));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(new CandidateProfile(
                "existing-id", USER_ID, "old@example.com", "Dev", List.of(), null, List.of("Globex"), 7)));
        returnSavedProfile();

        final CandidateProfile saved = service().save(USER_ID, ACCESS_TOKEN, DETAILS);

        assertThat(saved.id()).isEqualTo("existing-id");
        assertThat(saved.email()).isEqualTo("new@example.com");
        assertThat(saved.desiredRole()).isEqualTo("Senior Java Developer");
    }

    @Test
    void saveWithoutVerifiedEmail_keepsStoredEmail() {
        when(cognitoUserService.findVerifiedEmail(USER_ID, ACCESS_TOKEN)).thenReturn(Optional.empty());
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(new CandidateProfile(
                "existing-id", USER_ID, "jane@example.com", "Dev", List.of(), null, List.of("Globex"), 7)));
        returnSavedProfile();

        assertThat(service().save(USER_ID, ACCESS_TOKEN, DETAILS).email()).isEqualTo("jane@example.com");
    }

    @Test
    void firstSaveWithoutVerifiedEmail_savesWithoutEmail() {
        when(cognitoUserService.findVerifiedEmail(USER_ID, ACCESS_TOKEN)).thenReturn(Optional.empty());
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        returnSavedProfile();

        assertThat(service().save(USER_ID, ACCESS_TOKEN, DETAILS).email()).isNull();
    }

    @Test
    void cognitoFailure_savesNothing() {
        when(cognitoUserService.findVerifiedEmail(USER_ID, ACCESS_TOKEN))
                .thenThrow(new IdentityProviderUnavailableException(USER_ID, null));

        assertThatThrownBy(() -> service().save(USER_ID, ACCESS_TOKEN, DETAILS))
                .isInstanceOf(IdentityProviderUnavailableException.class);
        verify(repository, never()).save(any());
    }
}
