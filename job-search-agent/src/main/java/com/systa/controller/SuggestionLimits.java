package com.systa.controller;

/** Request limits shared by the suggest endpoints. */
final class SuggestionLimits {

    static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_LIMIT = 20;

    private SuggestionLimits() {
    }

    static int clamp(final int requestedLimit) {
        return Math.clamp(requestedLimit, 1, MAX_LIMIT);
    }
}
