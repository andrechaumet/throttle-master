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
 * @param <T> the type of lockable resource
 */
public final class MemoryLock<T extends Lockable> {

  private final ConcurrentHashMap<Object, ReentrantLock> locks;
  private final Semaphore capacity;
  private final boolean waitOnOverload;
  private final boolean fair;


  public MemoryLock() {
    this(64);
  }

  public MemoryLock(int minCapacity) {
    this(minCapacity, Integer.MAX_VALUE, true, true);
  }

  public MemoryLock(int minCapacity, int maxCapacity, boolean waitOnOverload, boolean fair) {
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
    if (waitOnOverload) capacity.acquire();
    else if (!capacity.tryAcquire()) return false;
    ReentrantLock lock = locks.computeIfAbsent(
        key, k -> new ReentrantLock(fair)
    );
    boolean acquired = lock.tryLock();
    if (!acquired) capacity.release();
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
}
