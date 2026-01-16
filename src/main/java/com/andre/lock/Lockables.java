package com.andre.lock;

public final class Lockables {

  private Lockables() { }

  public static Lockable from(String value) {
    return () -> value;
  }

  public static Lockable from(int value) {
    return () -> value;
  }

  public static Lockable from(double value) {
    return () -> value;
  }
}