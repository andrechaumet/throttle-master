package com.andre.limiter;

import java.util.concurrent.TimeoutException;

public interface TokenBucket extends RateLimiter {
    void acquire() throws TimeoutException;

    void acquire(int priority) throws TimeoutException;

    void supply();

    void supply(int amount);
}
