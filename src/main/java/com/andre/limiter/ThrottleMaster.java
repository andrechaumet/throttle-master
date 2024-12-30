package com.andre.limiter;

import static java.lang.Long.MAX_VALUE;
import static java.lang.Math.max;
import static java.lang.System.nanoTime;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RateLimiter designed to manage and control the flow of requests with the following features:
 * <ul>
 *   <li><b>Synchronous Limiting:</b> Restricts up to a specified number of simultaneous requests.
 *   <li><b>Priority Handling:</b> Allows certain requests to be prioritized for faster processing.
 *   <li><b>Hierarchical Rate Limiting:</b> Supports multi-level rate control (second, minute, etc).
 *   <li><b>Token Bucket:</b> Limits requests based on available capacity refilled over time.
 * </ul>
 *
 * Suitable for scenarios requiring precise and flexible rate control.
 * This class is under development. Use with caution.
 *
 * @author André Chaumet
 * @date 2024-09-24
 * @version 0.4
 */
public final class ThrottleMaster implements TokenBucket {

  static final TimeUnit[] SUPPORTED_TIME_UNITS = {SECONDS, MINUTES, HOURS};
  private static final long NEVER_TIMEOUT = MAX_VALUE;
  private static final int LOWEST_PRIORITY = 1;

  private final PriorityQueue priorityQueue;
  private final CycleTracker cycleTracker;
  private final long timeout;

  private ThrottleMaster(int[] throughput, long timeout) {
    this.cycleTracker = new CycleTracker(throughput);
    this.priorityQueue = new PriorityQueue();
    this.timeout = timeout;
  }

  /**
   * Acquires a resource with the lowest priority and using RateLimiter's instance default timeout.
   *
   * @throws TimeoutException if the acquisition fails due to timeout.
   */
  public void acquire() throws TimeoutException {
    acquire(LOWEST_PRIORITY, this.timeout);
  }

  /**
   * Acquires with the specified priority and using RateLimiter's instance default timeout.
   *
   * @param timeout the priority level for the acquisition request.
   * @throws TimeoutException if the acquisition fails due to timeout.
   */
  public void acquire(long timeout) throws TimeoutException {
    acquire(LOWEST_PRIORITY, timeout);
  }

  /**
   * Acquires a resource with the specified priority and using RateLimiter's instance default timeout.
   *
   * @param priority the priority level for the acquisition request.
   * @throws TimeoutException if the acquisition fails due to timeout.
   */
  public void acquire(int priority) throws TimeoutException {
    acquire(priority, this.timeout);
  }

  /**
   * Acquires a resource with the specified priority and custom timeout.
   *
   * @param priority the priority level for the acquisition request.
   * @param timeout the custom timeout given for the request in milliseconds.
   * @throws TimeoutException if the acquisition fails due to timeout.
   */
  public void acquire(int priority, long timeout) throws TimeoutException {
    priorityQueue.register(priority);
    long initialTime = nanoTime();
    do {
      if (tryAcquire(priority)) return;
    } while (!timedOut(initialTime, timeout));
    priorityQueue.remove(priority);
    throw new TimeoutException();
  }

  @Override
  public void supply() {

  }

  @Override
  public void supply(int amount) {

  }

  private boolean tryAcquire(int priority) {
    long currentTime = nanoTime();
    cycleTracker.reset(currentTime);
    if (acquired(priority)) return true;
    await(currentTime);
    return false;
  }

  private boolean acquired(int priority) {
    boolean amongFirst = priorityQueue.isAmongFirst(priority, cycleTracker.leftover());
    if (amongFirst && cycleTracker.available()) {
      return priorityQueue.remove(priority);
    }
    return false;
  }

  private synchronized void await(long currentTime) {
    long nextCycle = 1000 - NANOSECONDS.toMillis(currentTime - cycleTracker.lapsed());
    try {
      wait(max(1, nextCycle));
    } catch (InterruptedException e) {
      currentThread().interrupt();
    }
  }

  private boolean timedOut(long initialTime, long timeout) {
    return (nanoTime() - initialTime) >= timeout;
  }

  public static final class Builder {
    private final int[] throughput = new int[SUPPORTED_TIME_UNITS.length];
    private long timeout = NEVER_TIMEOUT;

    private Builder() {}

    public static Builder aRateLimiter() {
      return new Builder();
    }

    /**
     * Configures the rate limit for the rate limiter in terms of requests per time unit.
     *
     * <p>Sets the maximum number of permitted requests (throughput) for a specified {@link
     * TimeUnit}. The rate is stored internally for the corresponding ordinal of the provided time
     * unit.
     *
     * @param rate the maximum number of requests allowed per the specified time unit
     * @param unit the {@link TimeUnit} representing the granularity of the rate limit
     * @throws IllegalArgumentException if the amount is negative or the time unit is null
     */
    public Builder withRate(int rate, TimeUnit unit) {
      validateParameters(rate, unit, "Rate");
      this.throughput[getOrdinal(unit)] = rate;
      return this;
    }

    /**
     * Sets the timeout duration for acquiring permits in the rate limiter.
     *
     * <p>This timeout determines how long the rate limiter will wait for a permit to become
     * available before giving up. The duration is specified in the given {@link TimeUnit}.
     *
     * @param timeout the timeout duration to wait for a permit
     * @param unit the {@link TimeUnit} of the timeout duration
     * @throws IllegalArgumentException if the timeout is negative
     */
    public Builder withTimeout(long timeout, TimeUnit unit) {
      validateParameters(timeout, unit, "Timeout");
      this.timeout = unit.toNanos(timeout);
      return this;
    }

    /**
     * @param rate identified as seconds without requiring TimeUnit
     */
    public Builder withRate(int rate) {
      return withRate(rate, SECONDS);
    }

    /**
     * @param timeout identified as seconds without requiring TimeUnit
     */
    public Builder withTimeout(long timeout) {
      return withTimeout(timeout, SECONDS);
    }

    public ThrottleMaster build() {
      return new ThrottleMaster(throughput, timeout);
    }

    public MultiRateLimiter asRateLimiter() {
      return new ThrottleMaster(throughput, timeout);
    }

    public TokenBucket asTokenBucket() {
      return new ThrottleMaster(throughput, timeout);
    }

    private static int getOrdinal(TimeUnit unit) {
      for (int i = 0; i < SUPPORTED_TIME_UNITS.length; i++) {
        if (SUPPORTED_TIME_UNITS[i] == unit) return i;
      }
      throw new IllegalArgumentException(
              "TimeUnit must be one of " + Arrays.toString(SUPPORTED_TIME_UNITS) + ".");
    }

    private void validateParameters(long value, TimeUnit unit, String parameterName) {
      if (value <= 0) {
        throw new IllegalArgumentException(parameterName + " cannot be negative nor zero.");
      }
      if (unit == null) {
        throw new IllegalArgumentException("TimeUnit cannot be null.");
      }
    }
  }
}
