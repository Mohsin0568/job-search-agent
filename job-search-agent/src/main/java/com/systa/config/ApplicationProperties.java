package com.systa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "job-search")
public record ApplicationProperties(
        @DefaultValue("gpt-4.1") String model,
        @DefaultValue("2") int maxAttemptsPerBatch,
        @DefaultValue("30s") Duration rateLimitRetryDelay
) {}