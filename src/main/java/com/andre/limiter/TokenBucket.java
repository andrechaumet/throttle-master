package com.andre.limiter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface TokenBucket {

  void acquire() throws TimeoutException;

  void acquire(long timeout, TimeUnit timeUnit) throws TimeoutException;

  void supply();
}
