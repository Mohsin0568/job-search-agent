package com.systa.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CognitoAccessTokenValidatorTest {

    private static final String CLIENT_ID = "test-client";

    private final CognitoAccessTokenValidator validator = new CognitoAccessTokenValidator(CLIENT_ID);

    @Test
    void acceptsAccessTokenForOurClient() {
        assertThat(validator.validate(token(Map.of("token_use", "access", "client_id", CLIENT_ID))).hasErrors())
                .isFalse();
    }

    @Test
    void rejectsIdToken() {
        // ID tokens carry the client in "aud" rather than "client_id" and must not be used as API credentials.
        assertThat(validator.validate(token(Map.of("token_use", "id", "aud", CLIENT_ID))).hasErrors())
                .isTrue();
    }

    @Test
    void rejectsAccessTokenForAnotherClient() {
        assertThat(validator.validate(token(Map.of("token_use", "access", "client_id", "other-client"))).hasErrors())
                .isTrue();
    }

    @Test
    void rejectsTokenWithoutClaims() {
        assertThat(validator.validate(token(Map.of("username", "someone"))).hasErrors())
                .isTrue();
    }

    private static Jwt token(final Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("cognito-sub-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.putAll(claims))
                .build();
    }
}
