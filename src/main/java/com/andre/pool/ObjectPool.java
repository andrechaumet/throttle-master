package com.andre.pool;

import static java.lang.Integer.MAX_VALUE;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

// wip
public class ObjectPool<T> {

  private final Supplier<T> instantiator;
  private final Deque<T> pool;
  private final int sizeLimit;

  public ObjectPool(Supplier<T> instantiator, int sizeLimit) {
    this.pool = new ConcurrentLinkedDeque<>();
    this.instantiator = instantiator;
    this.sizeLimit = sizeLimit;
  }

  public ObjectPool(Supplier<T> instantiator) {
    this.pool = new ConcurrentLinkedDeque<>();
    this.instantiator = instantiator;
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