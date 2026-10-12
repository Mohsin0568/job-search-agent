package com.systa.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "resume")
public record ResumeProperties(
        @NotNull @Valid S3 s3,
        @DefaultValue("5m") Duration downloadUrlTtl
) {

    public record S3(
            @NotBlank String bucket,
            // Blank leaves the region to the AWS SDK's own lookup.
            String region
    ) {}
}
