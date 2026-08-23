package com.tem.spring.core.ratelimit;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {
    private final int limit;
    private final int remaining;

    public RateLimitExceededException(String message, int limit, int remaining) {
        super(message);
        this.limit = limit;
        this.remaining = remaining;
    }
}
