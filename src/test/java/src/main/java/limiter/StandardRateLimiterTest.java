package src.main.java.limiter;

import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andre.RateLimiter;
import com.andre.limiter.StandardRateLimiter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StandardRateLimiterTest {

  RateLimiter rateLimiter;
  double allowedMargin = 0.1;

  @Order(3)
  @ParameterizedTest
  @CsvSource({"11, 3", "100, 10", "22, 2", "94, 7", "50, 2", "231, 25"})
  void rateLimiterShouldHandleAverageInTime(int calls, int throughput) {
    // GIVEN: A RateLimiter with a limit of n transactions per second
    rateLimiter = new StandardRateLimiter(throughput);
    double expected = ceil((double) (calls - throughput) / throughput);
    double margin = expected * allowedMargin;
    // WHEN: Invoking n concurrent calls at the same instant
    Runnable execution = invokeRateLimiter(calls, rateLimiter, e -> currentThread().interrupt());
    double actual = NANOSECONDS.toSeconds(measureTime(execution));
    // THEN: Agreed rate limit value should be respected
    assertTrue(expected <= actual, "Rate limiter did not enforce the agreed speed limit.");
    assertEquals(expected, actual, margin, "Execution time is not within the allowed margin.");
  }

  @Order(1)
  @ParameterizedTest
  @CsvSource({"3, 20", "10, 100", "7, 8", "1111, 11111", "1, 2", "666, 999", "1000, 2000"})
  void rateLimiterShouldHandleMicroExecutionTimeValues(int calls, int throughput) {
    // GIVEN: A RateLimiter with a limit larger than transactions per second
    rateLimiter = new StandardRateLimiter(throughput);
    // WHEN: Invoking n concurrent calls at the same instant
    Runnable execution = invokeRateLimiter(calls, rateLimiter, e -> currentThread().interrupt());
    double actual = NANOSECONDS.toSeconds(measureTime(execution));
    // THEN: As close to zero as possible is expected
    assertTrue(1 > actual, "Rate limiter did not enforce the agreed speed limit.");
    assertEquals(allowedMargin, actual, allowedMargin, "Execution time is not within the allowed margin.");
  }

  @Order(2)
  @ParameterizedTest
  @CsvSource({"2, 5, 20", "1, 5, 6", "1, 10, 30"})
  void rateLimiterShouldTimeOutWhenExceedingTimeConstraints(int throughput, long timeout, int calls) {
    // GIVEN: A RateLimiter with timeout lesser than the throughput
    rateLimiter = new StandardRateLimiter(throughput, SECONDS.toNanos(timeout));
    AtomicInteger timeouts = new AtomicInteger();
    Runnable execution = invokeRateLimiter(calls, rateLimiter,
        e -> {
          currentThread().interrupt();
          timeouts.incrementAndGet();
        });
    // WHEN: Invoking n concurrent calls
    execution.run();
    // THEN: Out of time invocations should throw
    long maxAllowedInvocations = throughput * timeout;
    int expectedTimeouts = (int) max(0, calls - maxAllowedInvocations);
    assertEquals(expectedTimeouts, timeouts.get(), 1, "The number of timeouts does not match the expected value.");
  }

  private Runnable invokeRateLimiter(
      int calls, RateLimiter rateLimiter, Consumer<Exception> handler) {
    Thread[] threads = new Thread[calls];
    return () -> {
      for (int i = 0; i < calls; i++) {
        threads[i] = acquireSafely(rateLimiter, handler);
      }
      startAll(threads);
      joinAll(threads);
    };
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
