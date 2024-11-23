package com.andre.limiter;

import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.nanoTime;

final class CycleTracker {

  private final AtomicInteger requestCount;
  private final int throughput;
  long lapsed;

  CycleTracker(int throughput) {
    this.requestCount = new AtomicInteger();
    this.throughput = throughput;
    this.lapsed = nanoTime();
  }

  synchronized void reset(long currentTime) {
    if (currentTime - lapsed >= 1_000_000_000.0) {
      requestCount.set(0);
      lapsed = currentTime;
    }
  }

  boolean withoutPriority() {
    return requestCount.incrementAndGet() <= throughput;
  }

  boolean withPriority() {
    return requestCount.getAndIncrement() <= throughput;
  }

  synchronized int leftover() {
    return throughput - requestCount.get();
  }

  boolean exceeded() {
    return requestCount.get() > throughput;
  }

  public long lapsed() {
    return lapsed;
  }
}
