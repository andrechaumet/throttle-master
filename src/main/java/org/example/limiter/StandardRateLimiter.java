package org.example.limiter;

import static java.lang.Long.MAX_VALUE;
import static java.lang.Math.max;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.concurrent.atomic.AtomicInteger;
import org.example.RateLimiter;

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
  private long timeOut;
  private long lapsed;

  public StandardRateLimiter(int throughput) {
    this.throughput = throughput;
    this.requestCount = new AtomicInteger(0);
    this.lapsed = nanoTime();
    this.timeOut = MAX_VALUE;
  }

  public StandardRateLimiter(int throughput, long timeOut) {
    this.throughput = throughput;
    this.requestCount = new AtomicInteger(0);
    this.lapsed = nanoTime();
    this.timeOut = timeOut;
  }

  @Override
  public void acquire() throws InterruptedException {
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
  }

  private boolean timedOut(long initialTime) {
    return initialTime < timeOut;
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
  public void increase(int amount) {
    throughput += amount;
  }

  @Override
  public void decrease(int amount) {
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
