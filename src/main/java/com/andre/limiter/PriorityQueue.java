package com.andre.limiter;

import static com.andre.limiter.RateLimiter.LOWEST_PRIORITY;

/**
 * thread-safe PriorityQueue for managing queue insertions and retrievals with multithreaded access.
 *
 * @author André Chaumet
 * @date 2024-09-24
 * @version 0.4
 */
final class PriorityQueue {

  private PriorityNode highest;
  private PriorityNode lowest;

  synchronized void register(int priority) { // pending
    if (highest == null) {
      highest = new PriorityNode(priority);
      lowest = highest;
      return;
    }

    PriorityNode current = highest;
    PriorityNode previous = null;

    while (current != null) {
      if (current.priority > priority) {
        previous = current;
        current = current.next;
      } else if (current.priority == priority) {
        current.count++;
        return;
      } else { // current.priority < priority
        PriorityNode newPriorityNode = new PriorityNode(priority, current);
        if (previous != null) {
          previous.next = newPriorityNode;
        } else {
          highest = newPriorityNode;
        }
        return;
      }
    }

    PriorityNode newPriorityNode = new PriorityNode(priority);
    if (lowest != null) {
      lowest.next = newPriorityNode;
    }
    lowest = newPriorityNode;
  }

  synchronized boolean isAmongFirst(int priority, int first) {
    PriorityNode current = highest;
    int checked = 0;
    while (current != null && checked < first) {
      if (current.priority == priority) return true;
      checked += current.count;
      current = current.next;
    }
    return false;
  }

  synchronized boolean remove(int priority) { // pending
    PriorityNode current = highest;
    PriorityNode previous = null;
    while (current != null) {
      if (current.priority == priority) {
        current.count--;
        if (current.count == 0) {
          if (previous != null) {
            previous.next = current.next;
          } else {
            highest = current.next;
          }
          if (current == lowest) {
            lowest = previous;
          }
        }
        return true;
      } else {
        previous = current;
        current = current.next;
      }
    }
    return false;
  }

  boolean noPriority() {
    return highest != null && highest.priority == LOWEST_PRIORITY;
  }
}
