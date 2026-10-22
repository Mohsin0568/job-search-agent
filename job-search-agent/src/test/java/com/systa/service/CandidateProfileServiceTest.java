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

    private static CognitoUserService.CognitoUser cognitoUser(final String email, final String fullName) {
        return new CognitoUserService.CognitoUser(Optional.ofNullable(email), Optional.ofNullable(fullName));
    }

    private void returnSavedProfile() {
        when(repository.save(any(CandidateProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void firstSave_createsProfileWithEmailFromCognito() {
        when(cognitoUserService.findUser(USER_ID, ACCESS_TOKEN)).thenReturn(cognitoUser("jane@example.com", "Jane Doe"));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        returnSavedProfile();

        final CandidateProfile saved = service().save(USER_ID, ACCESS_TOKEN, DETAILS);

        assertThat(saved.id()).isNotBlank();
        assertThat(saved.userId()).isEqualTo(USER_ID);
        assertThat(saved.email()).isEqualTo("jane@example.com");
        assertThat(saved.fullName()).isEqualTo("Jane Doe");
        assertThat(saved.companyPreferences()).containsExactly("Acme");
    }

    @Test
    void laterSave_refreshesTheFullName_soAProfileFromBeforeItWasKeptGetsIt() {
        when(cognitoUserService.findUser(USER_ID, ACCESS_TOKEN)).thenReturn(cognitoUser("jane@example.com", "Jane Doe"));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(new CandidateProfile(
                "existing-id", USER_ID, "jane@example.com", null, "Dev", List.of(), null, List.of("Globex"), 7)));
        returnSavedProfile();

        assertThat(service().save(USER_ID, ACCESS_TOKEN, DETAILS).fullName()).isEqualTo("Jane Doe");
    }

    @Test
    void saveWithoutANameInCognito_keepsTheStoredName() {
        when(cognitoUserService.findUser(USER_ID, ACCESS_TOKEN)).thenReturn(cognitoUser("jane@example.com", null));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(new CandidateProfile(
                "existing-id", USER_ID, "jane@example.com", "Jane Doe", "Dev", List.of(), null, List.of("Globex"), 7)));
        returnSavedProfile();

        assertThat(service().save(USER_ID, ACCESS_TOKEN, DETAILS).fullName()).isEqualTo("Jane Doe");
    }

    @Test
    void firstSaveWithoutANameInCognito_savesWithoutOne() {
        when(cognitoUserService.findUser(USER_ID, ACCESS_TOKEN)).thenReturn(cognitoUser("jane@example.com", null));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        returnSavedProfile();

        assertThat(service().save(USER_ID, ACCESS_TOKEN, DETAILS).fullName()).isNull();
    }

    @Test
    void laterSave_keepsIdAndRefreshesEmail() {
        when(cognitoUserService.findUser(USER_ID, ACCESS_TOKEN)).thenReturn(cognitoUser("new@example.com", "Jane Doe"));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(new CandidateProfile(
                "existing-id", USER_ID, "old@example.com", null, "Dev", List.of(), null, List.of("Globex"), 7)));
        returnSavedProfile();

        final CandidateProfile saved = service().save(USER_ID, ACCESS_TOKEN, DETAILS);

        assertThat(saved.id()).isEqualTo("existing-id");
        assertThat(saved.email()).isEqualTo("new@example.com");
        assertThat(saved.desiredRole()).isEqualTo("Senior Java Developer");
    }

    @Test
    void saveWithoutVerifiedEmail_keepsStoredEmail() {
        when(cognitoUserService.findUser(USER_ID, ACCESS_TOKEN)).thenReturn(cognitoUser(null, null));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(new CandidateProfile(
                "existing-id", USER_ID, "jane@example.com", null, "Dev", List.of(), null, List.of("Globex"), 7)));
        returnSavedProfile();

        assertThat(service().save(USER_ID, ACCESS_TOKEN, DETAILS).email()).isEqualTo("jane@example.com");
    }

    @Test
    void firstSaveWithoutVerifiedEmail_savesWithoutEmail() {
        when(cognitoUserService.findUser(USER_ID, ACCESS_TOKEN)).thenReturn(cognitoUser(null, null));
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        returnSavedProfile();

        assertThat(service().save(USER_ID, ACCESS_TOKEN, DETAILS).email()).isNull();
    }

    @Test
    void cognitoFailure_savesNothing() {
        when(cognitoUserService.findUser(USER_ID, ACCESS_TOKEN))
                .thenThrow(new IdentityProviderUnavailableException(USER_ID, null));

        assertThatThrownBy(() -> service().save(USER_ID, ACCESS_TOKEN, DETAILS))
                .isInstanceOf(IdentityProviderUnavailableException.class);
        verify(repository, never()).save(any());
    }
}
