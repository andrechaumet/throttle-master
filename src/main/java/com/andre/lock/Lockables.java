package com.andre.lock;

/**
 * Utility factory class for creating {@link Lockable} instances
 * from common value types.
 * <p>
 * Simplifies the creation of lockable keys without requiring
 * explicit implementations.
 */
public final class Lockables {

  private Lockables() { }

  /**
   * Creates a {@link Lockable} from a {@link String} value.
   */
  public static Lockable from(String value) {
    return () -> value;
  }

  /**
   * Creates a {@link Lockable} from an {@code int} value.
   */
  public static Lockable from(int value) {
    return () -> value;
  }

  /**
   * Creates a {@link Lockable} from a {@code double} value.
   */
  public static Lockable from(double value) {
    return () -> value;
  }
}
