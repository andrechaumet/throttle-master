package com.andre.limiter;

import java.util.concurrent.TimeoutException;

public interface RateLimiter {
    void acquire() throws TimeoutException;

    void acquire(int priority) throws TimeoutException;

    void acquire(long timeout) throws TimeoutException;

    void acquire(int priority, long timeout) throws TimeoutException;
}
