package com.systa.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@Configuration
public class CognitoClientConfig {

    // GetUser is authorised by the user's own access token, not by AWS credentials, so the
    // client is anonymous - the backend needs no IAM permissions on the User Pool.
    @Bean(destroyMethod = "close")
    public CognitoIdentityProviderClient cognitoIdentityProviderClient(final AuthProperties authProperties) {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(authProperties.cognito().region()))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}
