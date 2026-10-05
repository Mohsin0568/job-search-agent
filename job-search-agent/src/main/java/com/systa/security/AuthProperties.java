package com.systa.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Validated
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        @NotNull @Valid Cognito cognito,
        @NotEmpty List<String> allowedOrigins
) {

    public record Cognito(
            @NotBlank String issuerUri,
            @NotBlank String clientId
    ) {

        private static final Pattern ISSUER_REGION =
                Pattern.compile("^https://cognito-idp\\.([a-z0-9-]+)\\.amazonaws\\.com/[^/]+/?$");

        /** The AWS region, taken from the issuer (https://cognito-idp.<region>.amazonaws.com/<userPoolId>). */
        public String region() {
            final Matcher matcher = ISSUER_REGION.matcher(issuerUri);
            if (!matcher.matches()) {
                throw new IllegalStateException("auth.cognito.issuer-uri is not a Cognito User Pool issuer: " + issuerUri);
            }
            return matcher.group(1);
        }
    }
}
