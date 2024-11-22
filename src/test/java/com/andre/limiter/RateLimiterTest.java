package com.andre.limiter;

import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RateLimiterTest {

  RateLimiter rateLimiter;
  double allowedMargin = 0.2;

  @Order(3)
  @ParameterizedTest
  @CsvSource({"100, 10", "22, 2", "94, 7", "50, 2", "231, 25", "134, 21", "519, 4"})
  void rateLimiterShouldHandleAverageInTime(int calls, int throughput) {
    // GIVEN: A RateLimiter with a limit of n transactions per second
    rateLimiter = new RateLimiter(throughput);
    double expected = ceil((double) (calls) / throughput);
    double margin = expected * allowedMargin;
    // WHEN: Invoking n concurrent calls at the same instant
    Runnable execution = invokeRateLimiter(calls, rateLimiter, e -> currentThread().interrupt());
    double actual = NANOSECONDS.toSeconds(measureTime(execution));
    // THEN: Agreed rate limit value should be respected
    assertEquals(expected, actual, margin, "Execution time is not within the allowed margin.");
  }


  @Order(1)
  @ParameterizedTest
  @CsvSource({"3, 20", "10, 100", "7, 8", "1111, 11111", "1, 2", "666, 999", "1000, 2000"})
  void rateLimiterShouldHandleMicroExecutionTimeValues(int calls, int throughput) {
    // GIVEN: A RateLimiter with a limit larger than transactions per second
    rateLimiter = new RateLimiter(throughput);
    // WHEN: Invoking n concurrent calls at the same instant
    Runnable execution = invokeRateLimiter(calls, rateLimiter, e -> currentThread().interrupt());
    double actual = NANOSECONDS.toSeconds(measureTime(execution));
    // THEN: As close to 1 as possible is expected
    assertEquals(0, actual, allowedMargin, "Execution time is not within the allowed margin.");
  }

  @Order(2)
  @ParameterizedTest
  @CsvSource({
    "1, 5, 6",
    "1, 10, 30",
    "5, 2, 15",
    "10, 1, 20",
    "3, 10, 25",
    "4, 8, 32",
    "4, 8, 40",
    "10, 2, 30",
    "20, 10, 250"
  })
  void rateLimiterShouldTimeOutWhenExceedingTimeConstraints(
      int throughput, long timeout, int calls) {
    // GIVEN: A RateLimiter with timeout smaller than the throughput
    rateLimiter = new RateLimiter(throughput, SECONDS.toNanos(timeout));
    AtomicInteger timeouts = new AtomicInteger();
    Runnable execution = invokeRateLimiter(calls, rateLimiter,
        e -> {
          currentThread().interrupt();
          timeouts.incrementAndGet();
        });
    // WHEN: Invoking n concurrent calls
    execution.run();
    // THEN: Expected timed out invocations should throw
    long maxAllowedInvocations = throughput * timeout;
    int expectedTimeouts = (int) max(0, calls - maxAllowedInvocations);
    assertEquals(expectedTimeouts, timeouts.get(), throughput, "The number of timeouts does not match the expected value.");
  }

  @Order(4)
  @ParameterizedTest
  @CsvSource({"2, 10, 18", "1, 6, 5", "2, 20, 8", "15, 10, 4", "3, 5, 10"})
  void rateLimiterShouldPassWithoutReachingTimeout(int throughput, long timeout, int calls) {
    // GIVEN: A RateLimiter with throughput able to avoid the timeout
    rateLimiter = new RateLimiter(throughput, SECONDS.toNanos(timeout));
    AtomicInteger timeouts = new AtomicInteger();
    Runnable execution = invokeRateLimiter(calls, rateLimiter,
        e -> {
          currentThread().interrupt();
          timeouts.incrementAndGet();
        });
    // WHEN: Invoking n concurrent calls
    execution.run();
    // THEN: No timeouts should happen
    int expectedTimeouts = 0;
    assertEquals(expectedTimeouts, timeouts.get(), "The operation experienced unexpected timeouts.");
  }

  // TODO:
  @Test
  void  rateLimiterShouldAllowCallsWithHigherPriorityFirst() {
    Thread[] threads = new Thread[100];
    RateLimiter limiter = new RateLimiter(5);
    AtomicInteger count = new AtomicInteger();
    Runnable r =
            () -> {
              try {
                int priorityLevel = count.incrementAndGet();
                limiter.acquire(priorityLevel);
              } catch (Exception e) {
                Thread.currentThread().interrupt();
              }
            };
    // GIVEN: A RateLimiter with a limit larger than transactions per second
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new Thread(r);
    }
    startAll(threads);
    joinAll(threads);
    // WHEN: Invoking n concurrent with diverse priority values
    // THEN: Higher priority values should acquire and pass first
  }

  private Runnable invokeRateLimiter(int calls, RateLimiter limiter, Consumer<Exception> handler) {
    Thread[] threads = new Thread[calls];
    return () -> {
      createAll(threads, limiter, handler);
      startAll(threads);
      joinAll(threads);
    };
  }

  private void createAll(Thread[] threads, RateLimiter limiter, Consumer<Exception> handler) {
    for (int i = 0; i < threads.length; i++) {
      threads[i] = acquireSafely(limiter, handler);
    }
  }

  private Thread acquireSafely(RateLimiter rateLimiter, Consumer<Exception> handler) {
    return new Thread(
        () -> {
          try {
            rateLimiter.acquire();
          } catch (Exception e) {
            handler.accept(e);
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

  private long measureTime(Runnable runnable) {
    long start = System.nanoTime();
    runnable.run();
    long end = System.nanoTime();
    return end - start;
  }
}
