package src.main.java.limiter;

import org.example.RateLimiter;
import org.example.limiter.StandardRateLimiter;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static java.lang.Math.ceil;
import static java.lang.Thread.currentThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StandardRateLimiterTest {

  RateLimiter rateLimiter;

  @Order(2)
  @ParameterizedTest
  @CsvSource({"11, 3", "100, 10", "22, 2", "94, 7", "50, 2", "231, 25"})
  void rateLimiterShouldHandleAverageInTime(int calls, int throughput) {
    // GIVEN: A RateLimiter with a limit of n transactions per second
    rateLimiter = new StandardRateLimiter(throughput);
    double expected = ceil((double) (calls - throughput) / throughput);
    double margin = expected * 0.01;
    // WHEN: Invoking n concurrent calls at the same instant
    Runnable execution = invokeRateLimiter(calls, rateLimiter);
    double actual = nanoSecondsToSeconds(measureTime(execution));
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
    Runnable execution = invokeRateLimiter(calls, rateLimiter);
    double actual = nanoSecondsToSeconds(measureTime(execution));
    // THEN: As close to zero as possible is expected
    assertEquals(0, actual, 0.2, "Execution time is not within the allowed margin.");
    assertTrue(1 > actual, "Rate limiter did not enforce the agreed speed limit.");
  }

  private Runnable invokeRateLimiter(int concurrentCalls, RateLimiter rateLimiter) {
    Thread[] threads = new Thread[concurrentCalls];
    return () -> {
      for (int i = 0; i < concurrentCalls; i++) {
        threads[i] = acquireSafely(rateLimiter);
        threads[i].start();
      }
      joinAll(threads);
    };
  }

  private Thread acquireSafely(RateLimiter rateLimiter) {
    return new Thread(
        () -> {
          try {
            rateLimiter.acquire();
          } catch (InterruptedException e) {
            currentThread().interrupt();
          }
        });
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

  private double nanoSecondsToSeconds(long nanoSeconds) {
    return nanoSeconds / 1_000_000_000.0;
  }
}
