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

  void reset(long currentTime) {
    boolean outdated = false;
    for (int i = 0; i < throughput.length; i++) {
      if (currentTime - lapsed >= SUPPORTED_TIME_UNITS[i].toNanos(1)) {
        requestCount[i].set(0);
        outdated = true;
      }
    }
    if (outdated) lapsed = currentTime;
  }

  boolean priorityPresent() {
    boolean present = requestCount[0].get() <= throughput[0];
    if (present) {
      requestCount[0].incrementAndGet();
    }
    return present;
  }

  int leftover() {
    return throughput[0] - requestCount[0].get();
  }

  public long lapsed() {
    return lapsed;
  }
}
