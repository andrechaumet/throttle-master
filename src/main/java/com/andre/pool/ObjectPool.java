package com.andre.pool;

import static java.lang.Integer.MAX_VALUE;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

public class ObjectPool<T> {

  private final Supplier<T> instantiator;
  private final Deque<T> pool;
  private final int sizeLimit;
  private final boolean overflow;

  private ObjectPool(Supplier<T> instantiator, int sizeLimit, boolean overflow) {
    this.pool = new ConcurrentLinkedDeque<>();
    this.instantiator = instantiator;
    this.sizeLimit = sizeLimit;
    this.overflow = overflow;
  }

  public T request() throws PoolExhaustedException {
    T entry = pool.poll();
    if (entry != null) return entry;
    else if (pool.size() < sizeLimit || overflow) return instantiator.get();
    else throw new PoolExhaustedException("ObjectPool exhausted: sizeLimit=" + sizeLimit);
  }

  public void release(T entry) {
    if (pool.size() < sizeLimit) pool.add(entry);
  }

  /**
   * Creates a new {@link Builder} for {@link ObjectPool}.
   *
   * @param <T> the type of objects managed by the pool
   * @return a new builder instance
   */
  public static <T> Builder<T> anObjectPool() {
    return new Builder<>();
  }

  /**
   * Builder for {@link ObjectPool}.
   *
   * @param <T> the type of objects managed by the pool
   */
  public static final class Builder<T> {

    private Supplier<T> instantiator;
    private int sizeLimit = MAX_VALUE;
    private boolean overflow = true;

    /**
     * Sets the supplier used to create new instances.
     *
     * @param instantiator object factory
     * @return this builder
     */
    public Builder<T> withInstantiator(Supplier<T> instantiator) {
      this.instantiator = instantiator;
      return this;
    }

    /**
     * Sets the maximum number of objects retained in the pool.
     *
     * @param sizeLimit pool size limit
     * @return this builder
     */
    public Builder<T> withSizeLimit(int sizeLimit) {
      this.sizeLimit = sizeLimit;
      return this;
    }

    public Builder<T> withOverflow(boolean overflow) {
      this.overflow = overflow;
      return this;
    }

    /**
     * Builds the {@link ObjectPool}.
     *
     * @return a configured {@link ObjectPool}
     * @throws IllegalStateException if instantiator was not provided
     */
    public ObjectPool<T> build() {
      if (instantiator == null) {
        throw new IllegalStateException("Instantiator must be provided");
      }
      return new ObjectPool<>(instantiator, sizeLimit, overflow);
    }
  }
}