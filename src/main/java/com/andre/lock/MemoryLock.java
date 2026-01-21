package com.andre.lock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Locking mechanism for {@link Lockable} resources.
 * <p>
 * Provides locking and unlocking for resources that implement {@code Lockable}, ensuring mutual exclusion when
 * performing operations on shared entities.
 *
y * @param <T> the type of lockable resource
 * @author André Chaumet
 *
 */
public final class MemoryLock<T extends Lockable> {

  private final ConcurrentHashMap<Object, ReentrantLock> locks;
  private final Semaphore capacity;
  private final boolean waitOnOverload;
  private final boolean fair;

  private MemoryLock(int minCapacity, int maxCapacity, boolean waitOnOverload, boolean fair) {
    this.locks = new ConcurrentHashMap<>(minCapacity);
    this.capacity = new Semaphore(maxCapacity, fair);
    this.waitOnOverload = waitOnOverload;
    this.fair = fair;
  }

  /**
   * Executes the given {@code Runnable} while holding the lock associated with the provided {@code lockable} key, using
   * the specified fairness policy.
   *
   */
  public void locked(T lockable, Runnable runnable) throws InterruptedException {
    var key = lockable.getKey();
    if (tryAcquireLock(key)) {
      try {
        runnable.run();
      } finally {
        releaseLock(key);
      }
    }
  }

  private boolean tryAcquireLock(Object key) throws InterruptedException {
    if (waitOnOverload) {
      capacity.acquire();
    } else if (!capacity.tryAcquire()) {
      return false;
    }
    ReentrantLock lock = locks.computeIfAbsent(
        key, k -> new ReentrantLock(fair)
    );
    boolean acquired = lock.tryLock();
    if (!acquired) {
      capacity.release();
    }
    return acquired;
  }

  private void releaseLock(Object key) {
    ReentrantLock lock = locks.get(key);
    if (lock != null) {
      lock.unlock();
      capacity.release();
      if (!lock.isLocked() && !lock.hasQueuedThreads()) {
        locks.remove(key, lock);
      }
    }
  }

  /**
   * Creates a builder for configuring and creating {@link MemoryLock} instances.
   *
   * @param <T> the type of lockable resource
   * @return a new {@link Builder} instance
   */
  public static <T extends Lockable> Builder<T> aMemoryLock() {
    return new Builder<>();
  }

  /**
   * Builder for {@link MemoryLock}.
   *
   * @param <T> the type of lockable resource
   */
  public static final class Builder<T extends Lockable> {

    private int minCapacity = 64;
    private int maxCapacity = Integer.MAX_VALUE;
    private boolean waitOnOverload = true;
    private boolean fair = true;

    private Builder() {
    }

    /**
     * Sets the initial capacity of the internal lock map.
     *
     * <p>This value is used as the initial sizing hint for the underlying
     * {@link ConcurrentHashMap} holding per-key locks.
     *
     * @param minCapacity the initial capacity of the lock map
     */
    public Builder<T> withMinCapacity(int minCapacity) {
      this.minCapacity = minCapacity;
      return this;
    }

    /**
     * Sets the maximum number of concurrent locks allowed.
     *
     * <p>This value defines the maximum number of permits managed by the internal
     * {@link Semaphore}, effectively limiting concurrent lock acquisition.
     *
     * @param maxCapacity the maximum number of concurrent locks
     */
    public Builder<T> withMaxCapacity(int maxCapacity) {
      this.maxCapacity = maxCapacity;
      return this;
    }

    /**
     * Configures whether lock acquisition should wait when the maximum capacity is reached.
     *
     * <p>If enabled, threads will block until capacity becomes available. If disabled,
     * lock acquisition will fail immediately when capacity is exhausted.
     *
     * @param waitOnOverload {@code true} to wait for capacity, {@code false} to fail fast
     */
    public Builder<T> withWaitOnOverload(boolean waitOnOverload) {
      this.waitOnOverload = waitOnOverload;
      return this;
    }

    /**
     * Enables or disables fair locking semantics.
     *
     * <p>This flag controls fairness for both the internal {@link Semaphore} and
     * per-key {@link ReentrantLock} instances.
     *
     * @param fair {@code true} to enable fair ordering, {@code false} otherwise
     */
    public Builder<T> withFairness(boolean fair) {
      this.fair = fair;
      return this;
    }

    /**
     * Builds a new {@link MemoryLock} instance with the configured options.
     *
     * @return a configured {@link MemoryLock} instance
     */
    public MemoryLock<T> build() {
      return new MemoryLock<>(minCapacity, maxCapacity, waitOnOverload, fair);
    }
  }
}