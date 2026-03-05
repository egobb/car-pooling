package com.egobb.carpooling.domain.service.concurrency.concurrency;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Registry providing a dedicated lock per car.
 *
 * <p>Why this exists: - We want fine-grained concurrency: unrelated cars should be updatable in
 * parallel. - A per-car lock keeps the critical section small (only the car state mutation).
 */
public final class CarLockRegistry {

  private final ConcurrentHashMap<Integer, ReentrantLock> locks = new ConcurrentHashMap<>();

  /** Returns the lock for the given car id. Created lazily if needed. */
  public ReentrantLock lockFor(int carId) {
    return this.locks.computeIfAbsent(carId, ignored -> new ReentrantLock());
  }

  /** Optional cleanup hook. Not required for the coding challenge. */
  public void remove(int carId) {
    this.locks.remove(carId);
  }
}
