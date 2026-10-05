package com.systa.security;

import com.systa.exception.IdentityProviderUnavailableException;
import com.systa.exception.UserSessionRevokedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CognitoUserServiceTest {

    private static final String USER_ID = "cognito-sub-123";
    private static final String ACCESS_TOKEN = "access-token";

    @Mock
    private CognitoIdentityProviderClient cognitoClient;

    private CognitoUserService service() {
        return new CognitoUserService(cognitoClient);
    }

    @Test
    void returnsVerifiedEmail_usingTheUsersAccessToken() {
        when(cognitoClient.getUser(any(GetUserRequest.class))).thenReturn(user("jane@example.com", "true"));

        assertThat(service().findVerifiedEmail(USER_ID, ACCESS_TOKEN)).contains("jane@example.com");

        final ArgumentCaptor<GetUserRequest> request = ArgumentCaptor.forClass(GetUserRequest.class);
        verify(cognitoClient).getUser(request.capture());
        assertThat(request.getValue().accessToken()).isEqualTo(ACCESS_TOKEN);
    }

    @Test
    void returnsEmpty_whenEmailIsNotVerified() {
        when(cognitoClient.getUser(any(GetUserRequest.class))).thenReturn(user("jane@example.com", "false"));

        assertThat(service().findVerifiedEmail(USER_ID, ACCESS_TOKEN)).isEmpty();
    }

    @Test
    void returnsEmpty_whenUserHasNoEmail() {
        when(cognitoClient.getUser(any(GetUserRequest.class))).thenReturn(GetUserResponse.builder()
                .userAttributes(AttributeType.builder().name("sub").value(USER_ID).build())
                .build());

        assertThat(service().findVerifiedEmail(USER_ID, ACCESS_TOKEN)).isEmpty();
    }

    @Test
    void revokedToken_raisesUserSessionRevoked() {
        when(cognitoClient.getUser(any(GetUserRequest.class)))
                .thenThrow(NotAuthorizedException.builder().message("Access Token has been revoked").build());

        assertThatThrownBy(() -> service().findVerifiedEmail(USER_ID, ACCESS_TOKEN))
                .isInstanceOf(UserSessionRevokedException.class);
    }

    @Test
    void networkFailure_raisesIdentityProviderUnavailable() {
        when(cognitoClient.getUser(any(GetUserRequest.class))).thenThrow(SdkClientException.create("connection refused"));

        assertThatThrownBy(() -> service().findVerifiedEmail(USER_ID, ACCESS_TOKEN))
                .isInstanceOf(IdentityProviderUnavailableException.class);
    }

    @Test
    void serviceError_raisesIdentityProviderUnavailable() {
        when(cognitoClient.getUser(any(GetUserRequest.class))).thenThrow(
                CognitoIdentityProviderException.builder().statusCode(500).message("internal error").build());

        assertThatThrownBy(() -> service().findVerifiedEmail(USER_ID, ACCESS_TOKEN))
                .isInstanceOf(IdentityProviderUnavailableException.class);
    }

    private static GetUserResponse user(final String email, final String emailVerified) {
        return GetUserResponse.builder()
                .userAttributes(
                        AttributeType.builder().name("sub").value(USER_ID).build(),
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("email_verified").value(emailVerified).build())
                .build();
    }
}
