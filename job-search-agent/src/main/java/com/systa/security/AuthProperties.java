package com.systa.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        @NotNull @Valid Cognito cognito,
        @NotEmpty List<String> allowedOrigins
) {

    public record Cognito(
            @NotBlank String issuerUri,
            @NotBlank String clientId
    ) {}
}
