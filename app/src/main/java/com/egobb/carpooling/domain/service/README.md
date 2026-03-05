# Car Pooling Service – Refactor & Concurrency Evolution Summary

This README documents the refactor applied to the `CarPoolingService` to improve code quality, correctness, and
maintainability.

The goal of this refactor is not only to pass the acceptance tests, but to deliver a clean, robust, and production-grade
implementation that reflects good engineering practices and explicit design trade-offs.

---

## Overview of Improvements

The service was refactored with the following objectives:

- Remove duplicated logic and unnecessary complexity
- Improve validation and error handling
- Use a clearer domain vocabulary
- Make behaviour predictable and easier to test
- Replace arbitrary car selection with a deterministic strategy
- Improve readability through decomposition and expressive private methods
- Provide proper domain-specific exceptions
- Document the service through meaningful comments
- Isolate persistence concerns behind domain repositories
- Make in-memory state updates **atomic and safe under concurrent access**
- Evolve the concurrency model from a coarse-grained lock to a **fine-grained, scalable strategy**

---

## Key Changes

### 1. Introduced Domain-Specific Exceptions

Previously, the service mixed generic exceptions such as `IllegalArgumentException` and `NoSuchElementException` with
domain-specific ones.

New exceptions:

- `InvalidGroupSizeException`
- `InvalidCarSeatsException`
- `JourneyNotFoundException`
- `DuplicatedIdException`

This provides clarity, improves controller error mapping, and ensures consistent API behaviour.

---

### 2. Refactored `resetCars` With Proper Validation

Improvements:

- Validation extracted into a dedicated method
- Use of a `Set<Integer>` for detecting duplicate IDs
- Exceptions thrown early and clearly
- Service state reset exactly once in a predictable location

---

### 3. Extracted Validation Logic Into Clear Private Methods

Key private helpers:

- `validateGroupSize`
- `validateCars`
- `ensureJourneyIdIsUnique`
- `assignJourneyToCar`

These reduce cognitive load and centralise business rules.

---

### 4. Implemented a Best-Fit Car Selection Strategy

Instead of choosing the first available car, the service now chooses the one with the least free seats among the
suitable ones.

This increases determinism and optimizes seat usage:

```java
private Optional<Car> findCar(int requiredSeats) {
    return this.carRepository.findAll().stream()
            .filter(c -> c.getAvailableSeats() >= requiredSeats)
            .min(Comparator.comparingInt(Car::getAvailableSeats));
}
```

---

### 5. Improved Pending Journey Management

Enhancements:

- `dropoff` automatically attempts reassignment
- No duplicated assignment logic
- Clear distinction between active and pending journeys
- Pending journeys preserve insertion order

This ensures the “first pending that fits” rule is respected.

---

### 6. Added Comments for Clarity

The service contains concise comments explaining:

- Purpose
- Behaviour
- Interaction with the domain
- Concurrency assumptions

---

### 7. Deterministic Behaviour

Assignment, pending handling, and seat updates follow deterministic rules, which is critical for testing and debugging.

---

## 8. Persistence and In-Memory Repositories

State is kept fully in memory, as required by the challenge.

To keep the design aligned with hexagonal / DDD principles, persistence concerns are isolated behind:

- `CarRepository`
- `JourneyRepository`

Implementation details:

- `ConcurrentHashMap<Integer, Car>`
- `ConcurrentHashMap<Integer, Journey>`
- `ConcurrentLinkedQueue` for pending journeys

Repositories provide thread-safe access at the data-structure level, while business-level atomicity is enforced by the
service layer.

---

## 9. Atomicity and Concurrency Model (Evolution)

### Previous Version: Coarse-Grained Lock

The initial refactor used a single service-level lock:

```java
private final Object lock = new Object();
```

All public operations executed under this lock.

**Pros**
- Very simple model
- Strong correctness guarantees

**Cons**
- No parallelism
- Limited throughput

This was a safe and conservative baseline.

---

### Current Version: Fine-Grained Concurrency

The concurrency model was evolved to allow parallelism while preserving correctness.

#### a) Per-Car Locks

- Each car has its own `ReentrantLock`
- Seat mutations happen under the specific car lock
- Unrelated cars can be updated in parallel

#### b) Concurrent Repositories

- Prevent low-level data races
- Service enforces business invariants

#### c) Read-Write Lock for Reset

- `resetCars` takes a write lock
- Runtime operations take a read lock
- Prevents traffic during resets without penalising normal flow

#### d) Atomic Journey Creation

- Journey ID uniqueness guaranteed under concurrency
- Minimal synchronized section, localised and explicit

---

### Properties of the Fine-Grained Model

**Pros**
- Parallel updates across cars
- Correct under concurrent load
- No overbooking
- Deterministic reassignment

**Cons**
- More complex than a single lock
- Requires dedicated concurrency tests

This represents a pragmatic balance between simplicity and scalability.

---

## 10. Concurrency Testing

Dedicated concurrency tests validate invariants:

- No seat overbooking
- Consistent seat accounting
- Correct dropoff and reassignment
- Parallel progress on multiple cars

Tests focus on invariants rather than execution order.

---

## 11. Possible Future Improvements

- Remove remaining global synchronization using atomic repository operations
- Retry-based best-fit assignment under contention
- Lock-free CAS-based seat management
- External persistence with transactional guarantees

Each option trades simplicity for performance and would be justified only by real load requirements.

---

## Design Philosophy

- Keep domain logic explicit
- Make invalid states unrepresentable
- Centralize business rules
- Preserve acceptance-test constraints
- Write production-level code
- Make concurrency assumptions explicit
- Evolve concurrency deliberately, not accidentally
