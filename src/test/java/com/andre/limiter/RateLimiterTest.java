package com.andre.limiter;

import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RateLimiterTest {

  RateLimiter rateLimiter;
  double allowedMargin = 0.1;

  @Order(3)
  @ParameterizedTest
  @CsvFileSource(resources = "/rateLimiterShouldHandleAverageInTime.csv")
  void rateLimiterShouldHandleAverageInTime(int calls, int throughput) {
    // GIVEN: A RateLimiter with a limit of n transactions per second
    rateLimiter = new RateLimiter(throughput);
    double expected = ceil((double) (calls - throughput) / throughput);
    double margin = expected * allowedMargin;
    AtomicInteger timeouts = new AtomicInteger();
    // WHEN: Invoking n concurrent calls at the same instant
    Runnable execution = invokeRateLimiter(calls, rateLimiter, e -> {
      timeouts.incrementAndGet();
      currentThread().interrupt();
    });
    double actual = NANOSECONDS.toSeconds(measureTime(execution));
    // THEN: Agreed rate limit value should be respected
     assertEquals(expected, actual, margin, "Execution time is not within the allowed margin.");
  }

  @Order(1)
  @ParameterizedTest
  @CsvFileSource(resources = "/rateLimiterShouldHandleMicroExecutionTimeValues.csv")
  void rateLimiterShouldHandleMicroExecutionTimeValues(int calls, int throughput) {
    // GIVEN: A RateLimiter with a limit larger than transactions per second
    rateLimiter = new RateLimiter(throughput);
    AtomicInteger timeouts = new AtomicInteger();
    // WHEN: Invoking n concurrent calls at the same instant
    Runnable execution = invokeRateLimiter(calls, rateLimiter, e -> {
      currentThread().interrupt();
      timeouts.incrementAndGet();
    });
    double actual = NANOSECONDS.toSeconds(measureTime(execution));
    // THEN: As close to 1 as possible is expected
    assertEquals(0, actual, allowedMargin, "Execution time is not within the allowed margin.");
  }

  // pending to add check so timed out requests are removed from the priority queue
  @Order(2)
  @ParameterizedTest
  @CsvFileSource(resources = "/rateLimiterShouldTimeOutWhenExceedingTimeConstraints.csv")
  void rateLimiterShouldTimeOutWhenExceedingTimeConstraints(int throughput, long timeout, int calls) {
    // GIVEN: A RateLimiter with timeout smaller than the throughput
    rateLimiter = new RateLimiter(throughput, SECONDS.toNanos(timeout));
    AtomicInteger timeouts = new AtomicInteger();
    Runnable execution =
        invokeRateLimiter(calls, rateLimiter,
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
  @CsvFileSource(resources = "/rateLimiterShouldPassWithoutReachingTimeout.csv")
  void rateLimiterShouldPassWithoutReachingTimeout(int throughput, long timeout, int calls) {
    // GIVEN: An amount of calls able to avoid the timeout
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
          } catch (TimeoutException e) {
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
