package com.andre.limiter;

/**
 * Represents a Node in a priority-based linked list. Each node tracks a priority level, a count of
 * occurrences, and a reference to the next node in the list.
 *
 * <p> Designed to be used in thread-safe contexts.
 */
final class PriorityNode {
  final int priority;
  PriorityNode next;
  int count;

  PriorityNode(final int priority) {
    this(priority, null);
  }

  PriorityNode(final int priority, PriorityNode next) {
    this.count = 1;
    this.next = next;
    this.priority = priority;
  }
}
