package com.andre.lock;

import static java.lang.Thread.currentThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemoryLockTest {

  @Order(1)
  @Test
  void memoryLockShouldPreventConcurrentExecutionForSameKey() throws InterruptedException {
    // GIVEN: A MemoryLock and multiple concurrent calls for the same key
    MemoryLock<Lockable> memoryLock = MemoryLock.aMemoryLock()
        .withMaxCapacity(100)
        .withMaxCapacityByKey(100)
        .withWaitOnOverload(true)
        .build();

    int calls = 20;

    AtomicInteger running = new AtomicInteger();
    AtomicInteger maxRunning = new AtomicInteger();
    AtomicInteger executed = new AtomicInteger();
    CountDownLatch latch = new CountDownLatch(calls);

    Runnable execution = invokeLockConcurrently(
        calls,
        Lockables.from("same-key"),
        memoryLock,
        () -> {
          try {
            int current = running.incrementAndGet();
            maxRunning.updateAndGet(previous -> Math.max(previous, current));
            sleepSilently(250);
            executed.incrementAndGet();
            running.decrementAndGet();
          } finally {
            latch.countDown();
          }
        });

    // WHEN: Invoking all calls at the same instant
    execution.run();
    latch.await();
    // THEN: Only one execution at a time should happen for the same key
    assertEquals(calls, executed.get(), "All calls should have been executed.");
    assertEquals(1, maxRunning.get(), "Same-key executions should never overlap.");
  }

  @Order(2)
  @Test
  void memoryLockShouldAllowConcurrentExecutionForDifferentKeys() throws InterruptedException {
    // GIVEN: A MemoryLock and two concurrent calls for different keys
    MemoryLock<Lockable> memoryLock = MemoryLock.aMemoryLock().build();

    CountDownLatch started = new CountDownLatch(3);
    CountDownLatch release = new CountDownLatch(1);
    AtomicBoolean overlapped = new AtomicBoolean(false);
    AtomicInteger running = new AtomicInteger();

    Thread first = lockInThread(memoryLock, Lockables.from("key-1"), () -> {
      started.countDown();
      int current = running.incrementAndGet();
      if (current > 1) {
        overlapped.set(true);
      }
      awaitSilently(release);
      running.decrementAndGet();
    });

    Thread second = lockInThread(memoryLock, Lockables.from("key-2"), () -> {
      started.countDown();
      int current = running.incrementAndGet();
      if (current > 1) {
        overlapped.set(true);
      }
      awaitSilently(release);
      running.decrementAndGet();
    });

    Thread third = lockInThread(memoryLock, Lockables.from(123), () -> {
      started.countDown();
      int current = running.incrementAndGet();
      if (current > 1) {
        overlapped.set(true);
      }
      awaitSilently(release);
      running.decrementAndGet();
    });

    // WHEN: Starting both calls concurrently
    first.start();
    second.start();
    third.start();
    started.await();
    release.countDown();
    first.join();
    second.join();
    third.join();

    // THEN: Different keys should be able to execute in parallel
    assertTrue(overlapped.get(), "Different-key executions should be allowed to overlap.");
  }

  @Order(3)
  @Test
  void memoryLockShouldFailFastWhenOverloadedAndWaitingIsDisabled() throws InterruptedException {
    // GIVEN: A MemoryLock with single overall capacity and fail-fast behavior
    MemoryLock<Lockable> memoryLock = MemoryLock.aMemoryLock()
        .withMaxCapacity(1)
        .withMaxCapacityByKey(1)
        .withWaitOnOverload(false)
        .build();

    CountDownLatch firstInside = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    AtomicBoolean firstResult = new AtomicBoolean(false);

    Thread first = new Thread(() -> {
      try {
        firstResult.set(memoryLock.locked(Lockables.from("key-1"), () -> {
          firstInside.countDown();
          awaitSilently(releaseFirst);
        }));
      } catch (InterruptedException e) {
        currentThread().interrupt();
      }
    });

    // WHEN: Occupying capacity and attempting another lock while overloaded
    first.start();
    firstInside.await();

    boolean secondResult = memoryLock.locked(Lockables.from("key-2"), () -> {
      throw new AssertionError("Second runnable should not be executed when acquisition fails.");
    });

    releaseFirst.countDown();
    first.join();

    // THEN: The first call should succeed and the second should fail immediately
    assertTrue(firstResult.get(), "First lock acquisition should succeed.");
    assertFalse(secondResult, "Second lock acquisition should fail fast when overloaded.");
  }

  @Order(4)
  @Test
  void memoryLockShouldWaitUntilCapacityIsAvailableWhenConfiguredToWait() throws InterruptedException {
    // GIVEN: A MemoryLock with single overall capacity and waiting enabled
    MemoryLock<Lockable> memoryLock = MemoryLock.aMemoryLock()
        .withMaxCapacity(1)
        .withWaitOnOverload(true)
        .build();

    CountDownLatch firstInside = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    AtomicBoolean firstExecuted = new AtomicBoolean(false);
    AtomicBoolean secondExecuted = new AtomicBoolean(false);
    AtomicBoolean secondResult = new AtomicBoolean(false);

    Thread first = new Thread(() -> {
      try {
        memoryLock.locked(Lockables.from("key-1"), () -> {
          firstExecuted.set(true);
          firstInside.countDown();
          awaitSilently(releaseFirst);
        });
      } catch (InterruptedException e) {
        currentThread().interrupt();
      }
    });

    Thread second = new Thread(() -> {
      try {
        secondResult.set(memoryLock.locked(Lockables.from("key-2"), () -> {
          secondExecuted.set(true);
        }));
      } catch (InterruptedException e) {
        currentThread().interrupt();
      }
    });

    // WHEN: Starting one call that occupies capacity and another that must wait
    first.start();
    firstInside.await();
    second.start();

    sleepSilently(100);
    assertFalse(secondExecuted.get(), "Second execution should still be waiting for capacity.");

    releaseFirst.countDown();
    first.join();
    second.join();

    // THEN: The waiting call should eventually execute successfully
    assertTrue(firstExecuted.get(), "First execution should have run.");
    assertTrue(secondExecuted.get(), "Second execution should run after capacity is released.");
    assertTrue(secondResult.get(), "Second lock acquisition should eventually succeed.");
  }

  @Order(4)
  @Test
  void memoryLockShouldWaitUntilCapacityIsAvailableWhenConfiguredToWait2() throws InterruptedException {
    // GIVEN: A MemoryLock with single overall capacity and waiting enabled
    MemoryLock<Lockable> memoryLock = MemoryLock.aMemoryLock()
        .withMaxCapacity(1)
        .withMaxCapacityByKey(1)
        .build();

    CountDownLatch firstInside = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    AtomicBoolean firstExecuted = new AtomicBoolean(false);
    AtomicBoolean secondExecuted = new AtomicBoolean(false);
    AtomicBoolean secondResult = new AtomicBoolean(false);

    Thread first = new Thread(() -> {
      try {
        memoryLock.locked(Lockables.from("key-1"), () -> {
          firstExecuted.set(true);
          firstInside.countDown();
          awaitSilently(releaseFirst);
        });
      } catch (InterruptedException e) {
        currentThread().interrupt();
      }
    });

    Thread second = new Thread(() -> {
      try {
        secondResult.set(memoryLock.locked(Lockables.from("key-2"), () -> {
          secondExecuted.set(true);
        }));
      } catch (InterruptedException e) {
        currentThread().interrupt();
      }
    });

    // WHEN: Starting one call that occupies capacity and another that must wait
    first.start();
    firstInside.await();
    second.start();

    sleepSilently(100);
    assertFalse(secondExecuted.get(), "Second execution should still be waiting for capacity.");

    releaseFirst.countDown();
    first.join();
    second.join();

    // THEN: The waiting call should eventually execute successfully
    assertTrue(firstExecuted.get(), "First execution should have run.");
    assertTrue(secondExecuted.get(), "Second execution should run after capacity is released.");
    assertTrue(secondResult.get(), "Second lock acquisition should eventually succeed.");
  }

  @Order(5)
  @Test
  void memoryLockShouldFailForDifferentKeysWhenOverallCapacityIsReached() throws InterruptedException {
    // GIVEN: A MemoryLock with global capacity limited to one execution and no waiting on overload
    MemoryLock<Lockable> memoryLock = MemoryLock.aMemoryLock()
        .withMaxCapacity(1)
        .withMaxCapacityByKey(10)
        .withWaitOnOverload(false)
        .build();

    CountDownLatch firstInside = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    AtomicBoolean firstResult = new AtomicBoolean(false);

    Thread first = new Thread(() -> {
      try {
        firstResult.set(memoryLock.locked(Lockables.from("key-1"), () -> {
          firstInside.countDown();
          awaitSilently(releaseFirst);
        }));
      } catch (InterruptedException e) {
        currentThread().interrupt();
      }
    });

    // WHEN: One key occupies the only global slot and another key tries to enter
    first.start();
    firstInside.await();

    boolean secondResult = memoryLock.locked(Lockables.from("key-2"), () -> {
      throw new AssertionError("Second runnable should not execute when global capacity is exhausted.");
    });

    releaseFirst.countDown();
    first.join();

    // THEN: Different keys should still be limited by overall capacity
    assertTrue(firstResult.get(), "First lock acquisition should succeed.");
    assertFalse(secondResult, "Second lock acquisition should fail due to overall capacity.");
  }

  @Order(6)
  @Test
  void memoryLockShouldWaitForDifferentKeysWhenOverallCapacityIsReached() throws InterruptedException {
    // GIVEN: A MemoryLock with global capacity limited to one execution and waiting enabled
    MemoryLock<Lockable> memoryLock = MemoryLock.aMemoryLock()
        .withMaxCapacity(1)
        .withMaxCapacityByKey(10)
        .withWaitOnOverload(true)
        .build();

    CountDownLatch firstInside = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    AtomicBoolean firstExecuted = new AtomicBoolean(false);
    AtomicBoolean secondExecuted = new AtomicBoolean(false);
    AtomicBoolean secondResult = new AtomicBoolean(false);

    Thread first = new Thread(() -> {
      try {
        memoryLock.locked(Lockables.from("key-1"), () -> {
          firstExecuted.set(true);
          firstInside.countDown();
          awaitSilently(releaseFirst);
          sleepSilently(500L);
        });
      } catch (InterruptedException e) {
        currentThread().interrupt();
      }
    });

    Thread second = new Thread(() -> {
      try {
        secondResult.set(memoryLock.locked(Lockables.from("key-2"), () -> {
          secondExecuted.set(true);
        }));
      } catch (InterruptedException e) {
        currentThread().interrupt();
      }
    });

    // WHEN: One key occupies the only global slot and another key waits
    first.start();
    firstInside.await();
    second.start();

    sleepSilently(100);

    releaseFirst.countDown();
    first.join();
    second.join();

    // THEN: The second key should execute only after global capacity is released
    assertTrue(firstExecuted.get(), "First execution should have run.");
    assertTrue(secondExecuted.get(), "Second execution should run after global capacity is released.");
    assertTrue(secondResult.get(), "Second lock acquisition should eventually succeed.");
  }

  private Runnable invokeLockConcurrently(
      int calls,
      Lockable lockable,
      MemoryLock<Lockable> memoryLock,
      Runnable runnable) {

    Thread[] threads = new Thread[calls];
    return () -> {
      createAll(threads, lockable, memoryLock, runnable);
      startAll(threads);
      joinAll(threads);
    };
  }

  private void createAll(
      Thread[] threads,
      Lockable lockable,
      MemoryLock<Lockable> memoryLock,
      Runnable runnable) {

    for (int i = 0; i < threads.length; i++) {
      threads[i] = lockInThread(memoryLock, lockable, runnable);
    }
  }

  private Thread lockInThread(
      MemoryLock<Lockable> memoryLock,
      Lockable lockable,
      Runnable runnable) {

    return new Thread(() -> {
      try {
        memoryLock.locked(lockable, runnable);
      } catch (InterruptedException e) {
        currentThread().interrupt();
      }
    });
  }

  private void startAll(Thread[] threads) {
    for (Thread thread : threads) {
      thread.start();
    }
  }

  private void joinAll(Thread[] threads) {
    for (Thread thread : threads) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        thread.interrupt();
      }
    }
  }

  private void awaitSilently(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      currentThread().interrupt();
    }
  }

  private void sleepSilently(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      currentThread().interrupt();
    }
  }
}