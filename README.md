# Throttle Master

> [!WARNING]
> **This project is in its early stages of development, and is not yet ready for production. Not all features are implemented yet. But, plenty of them are.**

RateLimiter designed to manage and control the flow of requests with the following features

### Synchronous Limiting

Restricts up to a specified number of simultaneous invocations.

```java
public CreateUserResponse createUser(CreateRequest createUser) {
  if (rateLimiter.acquire()) {
    return userService.post(createUser); // permit acquired, proceed normally
  }
  // rate limit timed out — handle accordingly (e.g. reject, retry, fallback)
}
```

### Priority Handling

Allows certain requests to be prioritized for faster processing. Can always add a request with
higher priority by increasing the int used to track priority

#### Parameters:

- `priority` (int): The priority level for the acquisition. Higher values indicate higher priority.

```java
public CreateUserResponse createUser(CreateRequest createUser, boolean highPriority) {
  boolean acquired = highPriority
      ? rateLimiter.acquire(2)  // in case of a bottleneck, will process before any request with a lower int
      : rateLimiter.acquire();  // will process only if no higher priority requests are pending

  if (acquired) {
    return userService.post(createUser); // permit acquired, proceed normally
  }
  // rate limit timed out — handle accordingly (e.g. reject, retry, fallback)
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

- `timeout` (long): The maximum time to wait for the resource.
- `timeUnit` (TimeUnit): The unit of time for the timeout (e.g., seconds, milliseconds).


```java

@Bean
public RateLimiter rateLimiter() {
  // limits up to 5 requests per second and 60 requests per minute. 
  // timeouts a request after 29 seconds of waiting
  return aRateLimiter()
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
if (rateLimiter.acquire(5, 10000)) { // priority level 5, waits for up to 10 seconds
  // permit acquired, proceed normally
} else {
  // timed out — could not acquire within the given window (e.g. bottlenecked by higher-priority threads)
  // handle accordingly (e.g. reject, retry, fallback)
}
```

---

### TokenBucket

A lower-level primitive where the available token count decreases with each acquisition and
increases with each supply, allowing the rate of access to fluctuate dynamically.

#### Supplying a token

```java
tokenBucket.supply(); // increases available tokens, unblocking any pending acquisitions
```

#### Basic acquisition

```java
if (tokenBucket.acquire()) {
  // token acquired — proceed normally
} else {
  // timed out — no token became available in time, handle accordingly
}
```

#### Acquisition with explicit timeout

```java
if (tokenBucket.acquire(500, MILLISECONDS)) {
  // token acquired — proceed normally
} else {
  // no token became available within 500ms, handle accordingly
}
```

---

### MemoryLock

MemoryLock is a lightweight utility for applying **mutual exclusion** over
**key-identified resources**.  
It ensures that only one thread can operate on the same logical resource at a time,
while allowing maximum concurrency across different keys.

Inactive locks are **automatically cleaned up**, preventing unbounded memory growth.

### Basic usage

```java
MemoryLock<Order> orderLock = aMemoryLock().build();

Order order = new Order(123); // Order must implement Lockable

// Ensures that only one order with the same ID is processed at a time,
// while allowing different orders to be processed concurrently
orderLock.locked(order, () -> {
    // your mutex logic here
    processOrder(order);
});
```

### Global and per-key concurrency limiting

MemoryLock can limit the total number of concurrent executions across all keys,
as well as the number of concurrent executions allowed for the same key,
providing protection against overload.

```java
MemoryLock<Order> orderLock = aMemoryLock()
    .withMaxCapacity(100)
    .withMaxCapacityByKey(10)
    .build();
```

### Overload behavior

By default, lock acquisition waits until capacity becomes available.

You can enable fail-fast behavior to immediately reject new attempts when capacity
is exhausted:

```java
MemoryLock<Order> failFastLock = aMemoryLock()
    .withWaitOnOverload(false)
    .build();
```

### Fair locking

Fair locking enforces first-come, first-served, ensuring that threads acquire locks in a FIFO order.
```java
MemoryLock<Order> fairLock = aMemoryLock()
    .withFairness(true)
    .build();
```

### Lock acquisition timeout

MemoryLock can time out while waiting to acquire a lock, allowing callers to avoid
waiting indefinitely under contention. If not specified, it waits up to `Long.MAX_VALUE`.

```java
MemoryLock<Order> orderLock = aMemoryLock()
    .withTimeout(SECONDS, 5)
    .build();
```


### Primitive and value-based locking

MemoryLock supports locking on primitive or value-based keys without requiring
objects implementing the **Lockable** interface, using the **Lockables** utility.

```java
MemoryLock<Lockable> lock = aMemoryLock().build();

lock.locked(Lockables.from(123), () -> {
    processById(123);
});

lock.locked(Lockables.from("order-123"), () -> {
    processByOrderKey("order-123");
});
```