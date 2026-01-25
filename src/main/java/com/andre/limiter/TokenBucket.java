package com.andre.limiter;

import java.util.concurrent.TimeoutException;

public interface TokenBucket {

  void acquire() throws TimeoutException;

  void supply();
}
