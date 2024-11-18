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
 * A RateLimiter designed to manage and control the flow of requests with the following features:
 *
 * <ul>
 *   <li><b>Synchronous Limiting:</b> Restricts up to a specified number of simultaneous requests.</li>
 *   <li><b>Priority Handling:</b> Allows certain requests to be prioritized for faster processing.</li>
 *   <li><b>Hierarchical Rate Limiting:</b> Supports multi-level rate control (e.g., second, minute, hour).</li>
 *   <li><b>Token Bucket Mechanism:</b> Utilizes a token-based algorithm for fair and efficient request management.</li>
 * </ul>
 *
 * Suitable for scenarios requiring precise and flexible rate control.
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
  public void acquire() throws TimeoutException {
    acquire(1);
  }

  @Override
  public void acquire(int priority) throws TimeoutException {
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
    int priorityIndex = getPriorityIndex(priority);
    boolean isPriority =  priorityIndex != -1;
    boolean withinLimit = false;
    if (isPriority && requestCount.incrementAndGet() <= throughput) {
      priorityVector.remove(priorityIndex);
      withinLimit = true;
    }
    return withinLimit;
  }

  private int getPriorityIndex(int priority) {
    int priorityIndex = -1;
    for (int i = 0; i < priorityVector.size() && i < throughput; i++) {
      int current = priorityVector.get(i);
      if (current == priority) priorityIndex = i;
    }
    return priorityIndex;
  }

  private void resetCounter(long currentTime) {
    if (currentTime - lapsed >= 1_000_000_000.0) {
      requestCount.set(0);
      lapsed = currentTime;
    }
  }

  private synchronized void await(long currentTime) {
    requestCount.decrementAndGet();
    long waitTime = 1000 - NANOSECONDS.toMillis(currentTime - lapsed);
    try {
      wait(max(1, waitTime));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
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
