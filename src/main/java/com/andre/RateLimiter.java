package com.andre;

import java.util.concurrent.TimeoutException;

/**
 * This interface defines a rate limiter mechanism, which controls the rate at which actions are
 * allowed to be performed. It provides methods to acquire permission to proceed, increase or
 * decrease the rate, and retrieve the current rate.
 */
public interface RateLimiter {

  /**
   * Acquires permission to proceed. If the rate limit has been reached, this method will block
   * until enough capacity becomes available to allow progress.
   *
   * @throws InterruptedException if the thread is interrupted while waiting for permission.
   */
  void acquire() throws InterruptedException, TimeoutException;

  /**
   * Increases the rate limit by a specified amount. This allows for a higher number of actions to
   * be performed within the rate limit.
   *
   * @param amount the amount by which to increase the rate limit.
   */
  void increaseLimit(int amount);

  /**
   * Decreases the rate limit by a specified amount. This limits the number of actions that can be
   * performed within the rate limit.
   *
   * @param amount the amount by which to decrease the rate limit.
   */
  void decreaseLimit(int amount);

  /**
   * Retrieves the current rate limit.
   *
   * @return the current rate limit as an integer.
   */
  int currentRate();

  int queueSize();
}
