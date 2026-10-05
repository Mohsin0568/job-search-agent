package com.systa.security;

import com.systa.exception.IdentityProviderUnavailableException;
import com.systa.exception.UserSessionRevokedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Reads the signed-in user's attributes from Cognito. Needed because Cognito access tokens carry no
 * email claim, and an email sent by the client can't be trusted.
 */
@Service
@Slf4j
public class CognitoUserService {

    private static final String EMAIL = "email";
    private static final String EMAIL_VERIFIED = "email_verified";

    private final CognitoIdentityProviderClient cognitoClient;

    public CognitoUserService(final CognitoIdentityProviderClient cognitoClient) {
        this.cognitoClient = cognitoClient;
    }

    /** The user's email if Cognito has verified it; empty if it's missing or unverified. */
    public Optional<String> findVerifiedEmail(final String userId, final String accessToken) {
        final Map<String, String> attributes = getAttributes(userId, accessToken);

        final String email = attributes.get(EMAIL);
        if (email == null || !Boolean.parseBoolean(attributes.get(EMAIL_VERIFIED))) {
            log.warn("Cognito user has no verified email - userId={}", userId);
            return Optional.empty();
        }
        return Optional.of(email);
    }

    private Map<String, String> getAttributes(final String userId, final String accessToken) {
        try {
            final List<AttributeType> attributes = cognitoClient
                    .getUser(GetUserRequest.builder().accessToken(accessToken).build())
                    .userAttributes();
            return attributes.stream().collect(Collectors.toMap(AttributeType::name, AttributeType::value, (a, b) -> a));
        } catch (final NotAuthorizedException e) {
            throw new UserSessionRevokedException(userId, e);
        } catch (final SdkException e) {
            throw new IdentityProviderUnavailableException(userId, e);
        }
    }
}
