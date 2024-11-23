package com.andre.limiter;

import static java.lang.Math.max;
import static java.lang.System.nanoTime;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.concurrent.TimeoutException;

/**
 * RateLimiter designed to manage and control the flow of requests with the following features:
 *
 * <ul>
 *   <li><b>Synchronous Limiting:</b> Restricts up to a specified number of simultaneous requests.
 *   <li><b>Priority Handling:</b> Allows certain requests to be prioritized for faster processing.
 *   <li><b>Hierarchical Rate Limiting:</b> Supports multi-level rate control (second, minute, etc).
 *   <li><b>Token Bucket:</b> Limits requests based on available capacity refilled over time.
 * </ul>
 *
 * Suitable for scenarios requiring precise and flexible rate control.
 *
 * @author André Chaumet
 * @date 2024-09-24
 * @version 0.2
 */
public final class RateLimiter {

  private static final long HIGHEST_TIMEOUT = Long.MAX_VALUE;
  public static final int LOWEST_PRIORITY = 1;

  private final PriorityQueue priorityQueue;
  private final CycleTracker cycleTracker;
  private final long timeout;

  public RateLimiter(int throughput) {
    this(throughput, HIGHEST_TIMEOUT);
  }

  public RateLimiter(int throughput, long timeout) {
    this.cycleTracker = new CycleTracker(throughput);
    this.priorityQueue = new PriorityQueue();
    this.timeout = timeout;
  }

  public void acquire() throws TimeoutException {
    acquire(LOWEST_PRIORITY);
  }

  public void acquire(int priority) throws TimeoutException {
    priorityQueue.register(priority);
    long initialTime = nanoTime();
    do {
      long currentTime = nanoTime();
      cycleTracker.reset(currentTime);
      if (acquired(priority)) return;
      await(currentTime);
    } while (!timedOut(initialTime));
    throw new TimeoutException();
  }

  private synchronized void await(long currentTime) {
    long nextCycle = 1000 - NANOSECONDS.toMillis(currentTime - cycleTracker.lapsed());
    try {
      wait(max(1, nextCycle));
    } catch (InterruptedException e) {
      currentThread().interrupt();
    }
  }

  private boolean acquired(Integer priority) {
    if (cycleTracker.exceeded()) {
      return false;
    } else {
      return allowed(priority);
    }
  }

  private boolean allowed(Integer priority) {
    boolean allowed = false;
    if (priorityQueue.noPriority() && cycleTracker.priorityPresent(false)) {
      priorityQueue.removeLowestPriority();
      allowed = true;
    } else if (priorityQueue.isAmongFirst(priority, cycleTracker.leftover()) && cycleTracker.priorityPresent(true)) {
      priorityQueue.removeFirstOccurrence(priority);
      allowed = true;
    }
    return allowed;
  }

  private boolean timedOut(long initialTime) {
    return (nanoTime() - initialTime) >= timeout;
  }
}