package com.systa.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Cognito access tokens carry no {@code aud} claim, so the standard audience check can't be used.
 * Instead we require {@code token_use=access} (rejecting ID tokens) and that {@code client_id}
 * matches our app client (rejecting tokens minted for other clients of the same User Pool).
 */
public class CognitoAccessTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final String ACCESS_TOKEN_USE = "access";

    private final String expectedClientId;

    public CognitoAccessTokenValidator(final String expectedClientId) {
        this.expectedClientId = expectedClientId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(final Jwt jwt) {
        if (!ACCESS_TOKEN_USE.equals(jwt.getClaimAsString("token_use"))) {
            return failure("Expected a Cognito access token");
        }
        if (!expectedClientId.equals(jwt.getClaimAsString("client_id"))) {
            return failure("Token was not issued to this application");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(final String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
    }
}
