package com.systa.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthPropertiesTest {

    @Test
    void region_isTakenFromIssuer() {
        assertThat(new AuthProperties.Cognito("https://cognito-idp.eu-west-2.amazonaws.com/eu-west-2_AbC123", "c").region())
                .isEqualTo("eu-west-2");
        assertThat(new AuthProperties.Cognito("https://cognito-idp.us-east-1.amazonaws.com/us-east-1_x/", "c").region())
                .isEqualTo("us-east-1");
    }

    @Test
    void region_rejectsNonCognitoIssuer() {
        assertThatThrownBy(() -> new AuthProperties.Cognito("https://example.com/pool", "c").region())
                .isInstanceOf(IllegalStateException.class);
    }
}
