package com.andre.limiter;

import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.nanoTime;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

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
    if (currentTime - lapsed >= SECONDS.toNanos(1)) {
      requestCount[0].set(0);
      lapsed = currentTime;
    }
    if (currentTime - lapsed >= MINUTES.toNanos(1)) {
      requestCount[1].set(0);
      lapsed = currentTime;
    }
    if (currentTime - lapsed >= HOURS.toNanos(1)) {
      requestCount[2].set(0);
      lapsed = currentTime;
    }
  }

  boolean priorityPresent() {
    return requestCount[0].getAndIncrement() <= throughput[0];
  }

  int leftover() {
    return throughput[0] - requestCount[0].get();
  }

  boolean exceeded() { // wut??
    return requestCount[0].get() > throughput[0];
  }

  public long lapsed() {
    return lapsed;
  }
}
