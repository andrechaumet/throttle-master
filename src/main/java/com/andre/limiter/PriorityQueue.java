package com.andre.limiter;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;


/**
 * PriorityQueue for managing queue insertions and retrievals on priority-based operations.
 **
 * @author André Chaumet
 * @date 2024-09-24
 * @version 1.0
 */
final class PriorityQueue {

  private static final int DEFAULT_SHARDS = 2;

  private final Shard[] shards;
  private final int mask;

  PriorityQueue() {
    this(DEFAULT_SHARDS);
  }

  PriorityQueue(int shardCount) {
    int size = nextPowerOfTwo(shardCount);
    this.mask = size - 1;
    this.shards = new Shard[size];

    for (int i = 0; i < size; i++) {
      shards[i] = new Shard();
    }
  }

  void register(int priority) {
    shard().register(priority);
  }

  void remove(int priority) {
    shard().remove(priority);
  }

  boolean isAmongFirst(int priority, int first) {
    return shard().isAmongFirst(priority, first);
  }

  private Shard shard() {
    long id = Thread.currentThread().threadId();
    return shards[(int) id & mask];
  }

  private static int nextPowerOfTwo(int value) {
    int highest = Integer.highestOneBit(value);
    return value == highest ? value : highest << 1;
  }

  private static final class Shard {

    private final ReentrantLock lock = new ReentrantLock();
    private final HashMap<Integer, Node> map = new HashMap<>();

    private Node highest;
    private Node lowest;

    void register(int priority) {
      lock.lock();
      try {
        Node node = map.get(priority);
        if (node != null) {
          node.count++;
          return;
        }
        Node newNode = new Node(priority);
        map.put(priority, newNode);
        insertOrdered(newNode);
      } finally {
        lock.unlock();
      }
    }

    void remove(int priority) {
      lock.lock();
      try {
        Node node = map.get(priority);
        if (node == null) return;
        node.count--;
        if (node.count == 0) {
          unlink(node);
          map.remove(priority);
        }
      } finally {
        lock.unlock();
      }
    }

    boolean isAmongFirst(int priority, int first) {
      lock.lock();
      try {
        Node current = highest;
        int checked = 0;
        while (current != null && checked < first) {

          if (current.priority == priority) return true;
          checked += current.count;

          if (checked >= first) return false;
          current = current.next;
        }
        return false;
      } finally {
        lock.unlock();
      }
    }

    private void insertOrdered(Node node) {
      if (highest == null) {
        highest = lowest = node;
        return;
      }
      Node current = highest;
      while (true) {
        if (node.priority > current.priority) {
          node.next = current;
          node.prev = current.prev;
          if (current.prev != null) current.prev.next = node;
          else highest = node;
          current.prev = node;
          return;
        }
        if (current.next == null) {
          current.next = node;
          node.prev = current;
          lowest = node;
          return;
        }
        current = current.next;
      }
    }

    private void unlink(Node node) {
      Node prev = node.prev;
      Node next = node.next;

      if (prev != null) prev.next = next;
      else highest = next;

      if (next != null) next.prev = prev;
      else lowest = prev;
    }
  }

  /**
   * Represents a Node in a priority-based linked list. Each node tracks a priority level, a count of occurrences, and a
   * reference to the next node in the list.
   *
   * <p> Designed to be used in thread-safe contexts.
   */
  private static final class Node {

    final int priority;
    int count;
    Node prev;
    Node next;

    Node(int priority) {
      this.priority = priority;
      this.count = 1;
    }
  }
}