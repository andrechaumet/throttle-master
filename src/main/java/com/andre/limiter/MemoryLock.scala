package com.andre.limiter

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * Represents a resource that can be locked.
 *
 * Implementations provide a unique key that identifies the resource to be locked.
 * This key is used to ensure mutual exclusion when accessing or modifying resources
 * of the same type.
 *
 * @version 0.2
 */
trait Lockable {

  /**
   * Returns the key used for locking.
   * This key must uniquely identify the resource.
   *
   * @return the lock key
   */
  def getKey: AnyRef
}

extension (s: String)
  def asLockable: Lockable = new Lockable {
    def getKey: AnyRef = s
  }

extension (i: Int)
  def asLockable: Lockable = new Lockable {
    def getKey: AnyRef = Integer.valueOf(i)
  }

/**
 * Locking mechanism for {@link Lockable} resources.
 *
 * Provides locking and unlocking for resources that implement {@code Lockable},
 * ensuring mutual exclusion when performing operations on shared entities.
 *
 * @param initialCapacity initial capacity of the internal lock map
 * @tparam T the type of lockable resource
 */
final class MemoryLock[T <: Lockable](initialCapacity: Int = 64) {

  private val locks = new ConcurrentHashMap[AnyRef, ReentrantLock](initialCapacity)

  /**
   * Executes the given {@code Runnable} while holding the lock associated with the provided
   * {@code lockable} key.
   *
   * If the lock is successfully acquired, the runnable is executed, and the lock is released
   * afterward. If the lock cannot be acquired immediately, the runnable is not executed.
   *
   * @param lockable the lockable resource providing the lock key
   * @param runnable the code to execute while holding the lock
   */
  def locked(lockable: T, runnable: Runnable): Unit = {
    locked(lockable, runnable, false)
  }

  /**
   * Executes the given {@code Runnable} while holding the lock associated with the provided
   * {@code lockable} key, using the specified fairness policy.
   */
  def locked(lockable: T, runnable: Runnable, fair: Boolean): Unit = {
    val key = lockable.getKey
    if (tryAcquireLock(key, fair)) try runnable.run() finally releaseLock(key)
  }

  private def tryAcquireLock(key: AnyRef, fair: Boolean) = {
    val lock = locks.computeIfAbsent(key, _ => new ReentrantLock(fair))
    lock.tryLock()
  }

  private def releaseLock(key: AnyRef): Unit = {
    val lock = locks.get(key)
    if (lock != null) {
      lock.unlock()
      if (!lock.isLocked && !lock.hasQueuedThreads) locks.remove(key, lock)
    }
  }
}
