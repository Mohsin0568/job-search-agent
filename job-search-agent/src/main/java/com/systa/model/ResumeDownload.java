package com.systa.model;

import java.time.Instant;

/** A short-lived link that downloads a resume straight from S3. */
public record ResumeDownload(String url, Instant expiresAt) {}
