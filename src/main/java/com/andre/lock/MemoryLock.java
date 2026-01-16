package com.andre.lock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Locking mechanism for {@link Lockable} resources.
 * <p>
 * Provides locking and unlocking for resources that implement {@code Lockable}, ensuring mutual exclusion when
 * performing operations on shared entities.
 *
 * @param <T> the type of lockable resource
 */
public final class MemoryLock<T extends Lockable> {

  private final ConcurrentHashMap<Object, ReentrantLock> locks;
  private final int maxCapacity;

  public MemoryLock() {
    this(64);
  }

  public MemoryLock(int minCapacity) {
    this.locks = new ConcurrentHashMap<>(minCapacity);
    this.maxCapacity = Integer.MAX_VALUE;
  }

  public MemoryLock(int minCapacity, int maxCapacity) {
    this.locks = new ConcurrentHashMap<>(minCapacity);
    this.maxCapacity = maxCapacity;
  }

  /**
   * Executes the given {@code Runnable} while holding the lock associated with the provided {@code lockable} key.
   */
  public void locked(T lockable, Runnable runnable) {
    locked(lockable, runnable, false);
  }

  /**
   * Executes the given {@code Runnable} while holding the lock associated with the provided {@code lockable} key, using
   * the specified fairness policy.
   *
   * @param fair Determines whether
   */
  public void locked(T lockable, Runnable runnable, boolean fair) {
    var key = lockable.getKey();
    if (tryAcquireLock(key, fair)) {
      try {
        runnable.run();
      } finally {
        releaseLock(key);
      }
    }
  }

  private boolean tryAcquireLock(Object key, boolean fair) {
    ReentrantLock lock = locks.computeIfAbsent(
        key, k -> new ReentrantLock(fair)
    );
    return lock.tryLock();
  }

  private void releaseLock(Object key) {
    ReentrantLock lock = locks.get(key);
    if (lock != null) {
      lock.unlock();
      if (!lock.isLocked() && !lock.hasQueuedThreads()) {
        locks.remove(key, lock);
      }
    }
  }
}
