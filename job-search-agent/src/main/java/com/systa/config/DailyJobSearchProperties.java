package com.systa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "job-search.daily-run")
public record DailyJobSearchProperties(
        // How many profiles to load from Mongo at a time.
        @DefaultValue("50") int pageSize,
        // Users are searched one after another; this spaces them out like the batches within a user.
        @DefaultValue("5s") Duration delayBetweenUsers,
        // Search again for users already searched today, e.g. when trying the job out locally.
        @DefaultValue("false") boolean force
) {}
