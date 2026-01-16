package com.andre.lock;

/**
 * Represents a resource that can be locked.
 * <p>
 * Implementations provide a unique key that identifies the resource to be locked. This key is used to ensure mutual
 * exclusion when accessing or modifying resources of the same type.
 *
 */
public interface Lockable {

  /**
   * Returns the key used for locking. This key must uniquely identify the resource.
   *
   * @return the lock key
   */
  Object getKey();
}