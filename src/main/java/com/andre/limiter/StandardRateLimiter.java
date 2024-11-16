package com.andre.limiter;

import static java.lang.Math.max;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.andre.RateLimiter;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate Limiter: This component restricts the number of times a function can be invoked within a
 * specified time period and taking priorities in count. It aims to prevent system or external
 * service overload and ensures controlled resource usage.
 *
 * <p>A rate limiter can be used, for example, to ensure that no more than X requests per second are
 * made to an API.
 *
 * @author André Chaumet
 * @date 2024-09-24
 * @version 0.1
 */
public class StandardRateLimiter implements RateLimiter {
  private final AtomicInteger requestCount;
  private int throughput;
  private long timeout;
  private long lapsed;

  public StandardRateLimiter(int throughput) {
    this(throughput, Long.MAX_VALUE);
  }

  public StandardRateLimiter(int throughput, long timeout) {
    this.throughput = throughput;
    this.requestCount = new AtomicInteger(0);
    this.lapsed = nanoTime();
    this.timeout = timeout;
  }

  @Override
  public void acquire() throws InterruptedException, TimeoutException {
    long initialTime = nanoTime();
    while (timedOut(initialTime)) {
      long currentTime = nanoTime();
      resetCounter(currentTime);
      if (withinLimit()) {
        return;
      } else {
        await(currentTime);
      }
    }
    throw new TimeoutException();
  }

  private boolean timedOut(long initialTime) {
    return (nanoTime() - initialTime) < timeout;
  }

  private boolean withinLimit() {
    return requestCount.incrementAndGet() <= throughput;
  }

  private void resetCounter(long currentTime) {
    if (currentTime - lapsed >= 1_000_000_000.0) {
      requestCount.set(0);
      lapsed = currentTime;
    }
  }

  private synchronized void await(long currentTime) throws InterruptedException {
    requestCount.decrementAndGet();
    long waitTime = 1000 - NANOSECONDS.toMillis(currentTime - lapsed);
    wait(max(1, waitTime));
  }

  @Override
  public void increaseLimit(int amount) {
    throughput += amount;
  }

  @Override
  public void decreaseLimit(int amount) {
    throughput -= amount;
  }

  @Override
  public int currentRate() {
    return throughput;
  }

  @Override
  public int queueSize() {
    return requestCount.get();
  }
}
