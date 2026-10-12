package com.systa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3ClientConfig {

    // No credentials are set on purpose: the SDK's default chain finds them wherever the app runs -
    // ~/.aws/credentials or AWS_* environment variables on a developer's machine, the task or
    // instance role on AWS - so the same code works in both without configuration.
    @Bean(destroyMethod = "close")
    public S3Client s3Client(final ResumeProperties resumeProperties) {
        final S3ClientBuilder builder = S3Client.builder().httpClient(UrlConnectionHttpClient.create());
        if (StringUtils.hasText(resumeProperties.s3().region())) {
            builder.region(Region.of(resumeProperties.s3().region()));
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(final ResumeProperties resumeProperties) {
        final S3Presigner.Builder builder = S3Presigner.builder();
        if (StringUtils.hasText(resumeProperties.s3().region())) {
            builder.region(Region.of(resumeProperties.s3().region()));
        }
        return builder.build();
    }
}
