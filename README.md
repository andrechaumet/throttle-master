# Throttle Master

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

```java

@Bean
public RateLimiter rateLimiter() {
  // Limits up to 5 requests per second and 60 requests per minute. 
  // Timeouts a request after 29 seconds of waiting
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
  rateLimiter.acquire(5, 10000); // Priority level 5, waits for up to 10 seconds
  // Perform operations after successful acquisition
} catch (TimeoutException e) {
  // Handles timeout if acquisition fails due to race conditions (e.g., RateLimiter bottlenecked by higher-priority threads)
}
```

In case any of these timeout constraints are exceeded, a **TimedOutException** from the *
*java.util.concurrent.TimeoutException** package will be thrown.

### Token Bucket Mechanism

Utilizes a token-based algorithm for fair and efficient request management.