package com.andre.pool;

public class PoolExhaustedException extends RuntimeException {

  public PoolExhaustedException(String message) {
    super(message);
  }
}
