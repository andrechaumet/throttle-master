package com.andre.limiter;

final class PriorityNode {
  int priority;
  int count;
  PriorityNode next;

  PriorityNode(int priority) {
    this(priority, null);
  }

  PriorityNode(int priority, PriorityNode next) {
    this.priority = priority;
    this.count = 1; // first
    this.next = next;
  }
}
