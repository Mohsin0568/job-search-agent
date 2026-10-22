package com.systa.exception;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void leavesTheCodeOut_whenThereIsNone() {
        assertThat(json.writeValueAsString(new ErrorResponse("resume file is too large")))
                .isEqualTo("{\"message\":\"resume file is too large\"}");
    }

    @Test
    void includesTheCode_whenThereIsOne() {
        assertThat(json.writeValueAsString(new ErrorResponse("on the waitlist", "ACCESS_PENDING")))
                .isEqualTo("{\"message\":\"on the waitlist\",\"code\":\"ACCESS_PENDING\"}");
    }
}
