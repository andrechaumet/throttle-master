package com.andre.limiter;

import java.util.concurrent.TimeUnit;

/**
 * Defines a token bucket for controlling access to a resource. The available token count
 * decreases with each acquisition and increases with each supply, allowing the rate of access
 * to fluctuate dynamically based on consumption and replenishment.
 */
public interface TokenBucket {

  /**
   * Supplies a token to the bucket, enabling pending acquisitions.
   */
  void supply();

  /**
   * Attempts to acquire a token using the default timeout. If no timeout is configured,
   * blocks indefinitely until a token becomes available.
   *
   * @return {@code true} if a token was acquired, {@code false} if a finite timeout elapsed before one became available
   */
  boolean acquire();

  /**
   * Attempts to acquire a token, waiting up to the specified timeout.
   *
   * @param timeout  the maximum time to wait for a token
   * @param timeUnit the unit of the timeout argument
   * @return {@code true} if a token was acquired, {@code false} if the timeout elapsed before one became available
   */
  boolean acquire(long timeout, TimeUnit timeUnit);
}
