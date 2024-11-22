package com.andre.limiter;

import java.util.ArrayList;

import static com.andre.limiter.RateLimiter.LOWEST_PRIORITY;

/**
 * thread-safe PriorityQueue for managing queue operations with multithreaded access.
 *
 * @author André Chaumet
 * @date 2024-09-24
 * @version 0.2
 */
public final class PriorityQueue {

  private final ArrayList<Integer> queue = new ArrayList<>();

  void register(int priority) {
    if (priority == LOWEST_PRIORITY) {
      queue.add(priority);
    } else {
      allocate(priority);
    }
  }

  private synchronized void allocate(int priority) {
    int left = 0, right = queue.size() - 1;
    while (left <= right) {
      int mean = (left + right) / 2;
      if (queue.get(mean) > priority) {
        left = mean + 1;
      } else {
        right = mean - 1;
      }
    }
    queue.add(left, priority);
  }

  boolean isAmongFirst(int priority, int first) {
    for (int i = 0; i <= first; i++) {
      if (queue.get(i) == priority) {
        return true;
      }
    }
    return false;
  }

  int first() {
    return queue.get(0);
  }

  int size() {
    return queue.size();
  }

  synchronized void removeFirstOccurrence(Integer element) {
    queue.remove(element);
  }

}
