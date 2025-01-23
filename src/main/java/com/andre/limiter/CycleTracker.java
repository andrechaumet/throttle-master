package com.andre.limiter;

import static com.andre.limiter.RateLimiter.SUPPORTED_TIME_UNITS;
import static java.lang.System.nanoTime;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * monitor and regulate the number of requests within a specific time window
 *
 * @author André Chaumet
 * @date 2024-09-24
 * @version 0.3
 */
final class CycleTracker {

  private final AtomicInteger[] requestCount;
  private final int[] throughput;
  private long lapsed;

  CycleTracker(int[] throughput) {
    this.requestCount = new AtomicInteger[throughput.length];
    for (int i = 0; i < throughput.length; i++) {
      requestCount[i] = new AtomicInteger();
    }
    this.throughput = throughput;
    this.lapsed = nanoTime();
  }

  synchronized void reset(long currentTime) { // pending to redo
    boolean outdated = false;
    for (int i = 0; i < throughput.length; i++) {
      if (currentTime - lapsed >= SUPPORTED_TIME_UNITS[i].toNanos(1)) {
        requestCount[i].set(0);
        outdated = true;
      }
    }
    if (outdated) lapsed = currentTime;
  }

  synchronized boolean available() {
    if (allAvailable()) {
      incrementAll();
      return true;
    }
    return false;
  }

  int leftover() {
    int leftover = 0;
    for (int i = 0; i < throughput.length; i++) {
      if (throughput[i] == 0) continue;
      leftover += throughput[i] - requestCount[i].get();
    }
    return leftover;
  }

  long lapsed() {
    return lapsed;
  }

  private boolean allAvailable() {
    for (int i = 0; i < throughput.length; i++) {
      if (throughput[i] == 0) continue;
      if (throughput[i] <= requestCount[i].get()) return false;
    }
    return true;
  }

  private void incrementAll() {
    for (AtomicInteger counter : requestCount) {
      counter.incrementAndGet();
    }
  }
}
