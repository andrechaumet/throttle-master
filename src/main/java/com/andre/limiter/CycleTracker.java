package com.andre.limiter;

import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.max;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class CycleTracker {

  private final AtomicInteger requestCount = new AtomicInteger();
  private long lapsed;
  private int limit;

  public CycleTracker(long lapsed, int limit) {
    this.lapsed = lapsed;
    this.limit = limit;
  }

  synchronized void reset(long currentTime) {
    if (currentTime - lapsed >= 1_000_000_000.0) {
      requestCount.set(0);
      lapsed = currentTime;
      System.out.println("--");
    }
  }

  int leftover() {
    return limit - requestCount.get();
  }

  boolean lesserAllowed() {
    return requestCount.incrementAndGet() <= limit;
  }

  synchronized void skip(long currentTime) {
    long nextCycle = 1000 - NANOSECONDS.toMillis(currentTime - lapsed);
    try {
      wait(max(1, nextCycle));
    } catch (InterruptedException e) {
      currentThread().interrupt();
    }
  }

  boolean exceeded() {
    return requestCount.get() > limit;
  }

  int getRequestCount() {
    return requestCount.get();
  }

  public void setLimit(int limit) {
    this.limit = limit;
  }
}
