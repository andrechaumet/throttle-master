package com.andre.limiter;

import java.util.concurrent.TimeoutException;

public interface RateLimiter extends Limiter {

  void acquire(int priority) throws TimeoutException;

  void acquire(int priority, long timeout) throws TimeoutException;
}
