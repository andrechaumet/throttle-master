package com.andre.limiter;

import static java.lang.Math.max;
import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.andre.RateLimiter;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This component restricts the number of times a function can be invoked within a
 * specified time period and taking priorities in count. It aims to prevent system or external
 * service overload and ensures controlled resource usage.
 *
 * <p>A rate limiter can be used, for example, to ensure that no more than X requests per second are
 * made to an API.</p>
 *
 * @author André Chaumet
 * @date 2024-09-24
 * @version 0.1
 */
public class StandardRateLimiter implements RateLimiter {
  private final List<Integer> priorityVector;
  private final AtomicInteger requestCount;
  private int throughput;
  private long timeout;
  private long lapsed;

  public StandardRateLimiter(int throughput) {
    this(throughput, Long.MAX_VALUE);
  }

  public StandardRateLimiter(int throughput, long timeout) {
    this.requestCount = new AtomicInteger(0);
    this.priorityVector = new Vector<>();
    this.throughput = throughput;
    this.lapsed = nanoTime();
    this.timeout = timeout;
  }

  @Override
  public void acquire() throws InterruptedException, TimeoutException {
    acquire(1);
  }

  public void acquire(int priority) throws InterruptedException, TimeoutException {
    long initialTime = nanoTime();
    register(priority);
    while (!timedOut(initialTime)) {
      long currentTime = nanoTime();
      resetCounter(currentTime);
      if (withinLimit(priority)) return;
      await(currentTime);
    }
    throw new TimeoutException();
  }

  private void register(int priority) {
    int i = 0;
    while (i < priorityVector.size() && priorityVector.get(i) > priority) i++;
    priorityVector.add(i, priority);
  }

  private boolean timedOut(long initialTime) {
    return (nanoTime() - initialTime) >= timeout;
  }

  private boolean withinLimit(int priority) {
    int priorityIndex = isPriority(priority);
    if (priorityIndex != -1 && requestCount.incrementAndGet() <= throughput) {
      priorityVector.remove(priorityIndex);
      return true;
    }
    return false;
  }

  private int isPriority(int priority) {
    for (int i = 0; i < priorityVector.size() && i < throughput; i++) {
      int current = priorityVector.get(i);
      if (current == priority) return i;
    }
    return -1;
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
  public void adjustTimeout(long timeout) {
    this.timeout = timeout;
  }

  @Override
  public void adjustLimit(int amount) {
    this.throughput = amount;
  }

  @Override
  public int queueSize() {
    return requestCount.get();
  }

  @Override
  public int currentRate() {
    return throughput;
  }
}
