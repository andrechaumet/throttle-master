package com.andre.limiter;

final class PriorityNode {
  int count;
  int priority;
  PriorityNode next;

  PriorityNode(int priority) {
    this(priority, null);
  }

  PriorityNode(int priority, PriorityNode next) {
    this.count = 1;
    this.next = next;
    this.priority = priority;
  }
}
