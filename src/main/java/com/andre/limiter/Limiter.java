package com.andre.limiter;

import java.util.concurrent.TimeoutException;

public interface Limiter {

  void acquire() throws TimeoutException;

  void acquire(long timeout) throws TimeoutException;
}
