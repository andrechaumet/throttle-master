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

  private Node highest;
  private Node lowest;

  synchronized void register(int priority) {
    if (highest == null) {
      highest = new Node(priority, 1, null);
      lowest = highest;
      return;
    }

    Node current = highest;
    Node previous = null;

    while (current != null) {
      if (current.priority > priority) {
        previous = current;
        current = current.next;
      } else if (current.priority == priority) {
        current.count++;
        return;
      } else { // current.priority < priority
        Node newNode = new Node(priority, 1, current);
        if (previous != null) {
          previous.next = newNode;
        } else {
          highest = newNode;
        }
        return;
      }
    }

    Node newNode = new Node(priority, 1, null);
    if (lowest != null) {
      lowest.next = newNode;
    }
    lowest = newNode;
  }

  synchronized boolean isAmongFirst(int priority, int first) {
    Node current = highest;
    for (int i = 0; i < first; i++) {
      if (current == null) break;
      if (current.priority == priority) {
        return true;
      } else {
        i += current.count;
        current = current.next;
      }
    }
    return false;
  }

  synchronized boolean removeFirstOccurrence(int priority) {
    Node current = highest;
    Node previous = null;

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

  private static class Node {
    int priority;
    int count;
    Node next;

    public Node(int priority, int count, Node next) {
      this.priority = priority;
      this.count = count;
      this.next = next;
    }
  }
}