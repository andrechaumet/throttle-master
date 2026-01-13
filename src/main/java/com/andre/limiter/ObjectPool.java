package com.andre.limiter;

import static java.lang.Integer.MAX_VALUE;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

public class ObjectPool<T> {

  private final Supplier<T> instantiator;
  private final Deque<T> pool;
  private final int sizeLimit;

  public ObjectPool(Supplier<T> instantiator, int sizeLimit) {
    this.instantiator = instantiator;
    this.pool = new ArrayDeque<>();
    this.sizeLimit = sizeLimit;
  }

  public ObjectPool(Supplier<T> instantiator) {
    this.instantiator = instantiator;
    this.pool = new ArrayDeque<>();
    this.sizeLimit = MAX_VALUE;
  }

  public T request() {
    T entry = pool.poll();
    return (entry != null) ? entry : instantiator.get();
  }

  public void release(T entry) {
    if (pool.size() < sizeLimit) pool.add(entry);
  }
}