package com.systa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "access")
public record AccessProperties(
        // Only invited users may use the app (see UserAccess). Set INVITE_ONLY=false to open it to everyone.
        @DefaultValue("true") boolean inviteOnly
) {}
