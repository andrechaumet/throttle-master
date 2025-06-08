# Throttle Master

> [!WARNING]
> **This project is in its early stages of development, and is not yet ready for production. Not all features are implemented yet. But, plenty of them are.**

RateLimiter designed to manage and control the flow of requests with the following features

### Synchronous Limiting

Restricts up to a specified number of simultaneous invocations.

```java
public CreateUserResponse createUser(CreateRequest createUser) {
  rateLimiter.acquire(); // waits here until it can acquire, or times out depending on the configuration
  return userService.post(createUser);
}
```

### Priority Handling

Allows certain requests to be prioritized for faster processing. Can always add a request with
higher priority by increasing the int used to track priority

#### Parameters:

- `priority` (int): The priority level for the acquisition. Higher values indicate higher priority.

```java
public CreateUserResponse createUser(CreateRequest createUser, boolean highPriority) {
  if (highPriority) {
    // in case of a bottleneck, will process before any request with a lower int
    rateLimiter.acquire(2);
  } else {
    // this is the same as using 'rateLimiter.acquire(1)'
    // will process only if no higher priority requests are pending
    rateLimiter.acquire();
  }
  return userService.post(createUser);
}
```

### Hierarchical Rate Limiting

Supports multi-level rate control (e.g., second, minute, hour).
allowing fine-grained control over resource consumption, ensuring adherence to both short-term and
long-term usage quotas.

#### withRate(rate, timeUnit):

- `rate` (int): The rate at which resources are acquired (e.g., permits per second).
- `timeUnit` (TimeUnit): The unit of time for the rate (e.g., seconds, minutes).

#### withTimeout(timeout, timeUnit):

- `timeout` (int): The maximum time to wait for the resource.
- `timeUnit` (TimeUnit): The unit of time for the timeout (e.g., seconds, milliseconds).


```java

@Bean
public RateLimiter rateLimiter() {
  // limits up to 5 requests per second and 60 requests per minute. 
  // timeouts a request after 29 seconds of waiting
  return RateLimiter.RateLimiterBuilder
      .withRate(5, SECONDS)
      .withRate(60, MINUTES)
      .withTimeout(29, SECONDS)
      .build();
}
```

### Overriding special cases

#### Parameters:

- `priority` (int): The priority level for the acquisition. Higher values indicate higher priority.
- `timeout` (long): The maximum time to wait for the resource in milliseconds.

```java
try {
  rateLimiter.acquire(5, 10000); // priority level 5, waits for up to 10 seconds
  // perform operations after successful acquisition
} catch (TimeoutException e) {
  // handles timeout if acquisition fails due to race conditions (e.g., RateLimiter bottlenecked by higher-priority threads)
}
```

In case any of these timeout constraints are exceeded, a **TimedOutException** from the *
*java.util.concurrent.TimeoutException** package will be thrown.

### MemoryLock

MemoryLock is a lightweight utility for applying in-memory mutual exclusion over key-identified resources.
Automatic cleanup of inactive locks, works with any type that implements `Lockable`

```java
MemoryLock<Order> orderLock = new MemoryLock<>();

Order order = new Order(123); // Order.class must implement Lockable interface

// ensures that only one order with the same ID is executed at a time,
// while allowing multiple orders with different IDs to be processed concurrently
orderLock.locked(order, () -> {
  processOrder(order); // executed with mutual exclusion for this order ID
});
```

### Token Bucket Mechanism

Utilizes a token-based algorithm for fair and efficient request management.