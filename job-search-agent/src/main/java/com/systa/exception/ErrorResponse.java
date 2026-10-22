package com.systa.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param code a fixed name for an error the frontend acts on, e.g. ACCESS_PENDING; left out of the JSON
 *             when the message alone is enough
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String message, String code) {

    public ErrorResponse(final String message) {
        this(message, null);
    }
}
