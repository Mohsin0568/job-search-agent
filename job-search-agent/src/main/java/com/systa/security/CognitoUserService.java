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
 * email or name claim, and neither can be trusted from the client.
 */
@Service
@Slf4j
public class CognitoUserService {

    private static final String EMAIL = "email";
    private static final String EMAIL_VERIFIED = "email_verified";
    private static final String NAME = "name";

    private final CognitoIdentityProviderClient cognitoClient;

    public CognitoUserService(final CognitoIdentityProviderClient cognitoClient) {
        this.cognitoClient = cognitoClient;
    }

    /**
     * Who the user is, as Cognito has them.
     *
     * @param verifiedEmail their email if Cognito has verified it; empty if it's missing or unverified
     * @param fullName      the full name they gave when registering; empty if they gave none
     */
    public record CognitoUser(Optional<String> verifiedEmail, Optional<String> fullName) {}

    /** The user's verified email and full name, from one call to Cognito with their own access token. */
    public CognitoUser findUser(final String userId, final String accessToken) {
        final Map<String, String> attributes = getAttributes(userId, accessToken);
        return new CognitoUser(verifiedEmail(userId, attributes), fullName(userId, attributes));
    }

    private static Optional<String> verifiedEmail(final String userId, final Map<String, String> attributes) {
        final String email = attributes.get(EMAIL);
        if (email == null || !Boolean.parseBoolean(attributes.get(EMAIL_VERIFIED))) {
            log.warn("Cognito user has no verified email - userId={}", userId);
            return Optional.empty();
        }
        return Optional.of(email);
    }

    private static Optional<String> fullName(final String userId, final Map<String, String> attributes) {
        final String name = attributes.get(NAME);
        if (name == null || name.isBlank()) {
            log.warn("Cognito user has no name - userId={}", userId);
            return Optional.empty();
        }
        return Optional.of(name.strip());
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
