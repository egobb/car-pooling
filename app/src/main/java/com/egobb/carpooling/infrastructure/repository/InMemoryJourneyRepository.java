package com.egobb.carpooling.infrastructure.repository;

import com.egobb.carpooling.domain.model.Journey;
import com.egobb.carpooling.domain.port.JourneyRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Repository;

/**
 * Thread-safe in-memory implementation of {@link JourneyRepository}.
 *
 * <p>Concurrency strategy: global journey storage uses {@link ConcurrentHashMap} for thread-safe
 * access; pending journeys are stored in a lock-free FIFO queue of journey IDs, plus a membership
 * map to avoid duplicates and support O(1) "is pending" checks.
 *
 * <p>Important: the pending queue is tolerant to stale IDs. A journey may be assigned or deleted
 * while its ID is still present in the queue, so callers must re-validate state. This trade-off
 * keeps the implementation simple and scalable for the challenge scope.
 */
@Repository
public class InMemoryJourneyRepository implements JourneyRepository {

  /** All journeys (both assigned and pending), keyed by id. */
  private final ConcurrentHashMap<Integer, Journey> journeys = new ConcurrentHashMap<>();

  /** FIFO order of pending journey ids. */
  private final ConcurrentLinkedQueue<Integer> pendingOrder = new ConcurrentLinkedQueue<>();

  /** Membership map to avoid inserting the same pending id multiple times. */
  private final ConcurrentHashMap<Integer, Boolean> pendingIndex = new ConcurrentHashMap<>();

  @Override
  public List<Journey> findAll() {
    return List.copyOf(new ArrayList<>(this.journeys.values()));
  }

  @Override
  public List<Journey> findPending() {
    // Build a stable snapshot in insertion order. Skip stale ids.
    final List<Journey> pending = new ArrayList<>();
    for (final Integer id : this.pendingOrder) {
      final Journey j = this.journeys.get(id);
      if (j == null) {
        continue;
      }
      if (j.getAssignedTo() != null) {
        continue;
      }
      // Only include if still considered pending.
      if (this.pendingIndex.containsKey(id)) {
        pending.add(j);
      }
    }
    return List.copyOf(pending);
  }

  @Override
  public Optional<Journey> findById(int id) {
    return Optional.ofNullable(this.journeys.get(id));
  }

  @Override
  public void save(Journey journey) {
    // Replace existing instance (by id) with the updated one.
    this.journeys.put(journey.getId(), journey);

    // If it is assigned, ensure it is no longer pending.
    if (journey.getAssignedTo() != null) {
      this.removePending(journey.getId());
    }
  }

  @Override
  public void savePending(Journey journey) {
    this.journeys.put(journey.getId(), journey);

    // Only enqueue once.
    if (this.pendingIndex.putIfAbsent(journey.getId(), Boolean.TRUE) == null) {
      this.pendingOrder.add(journey.getId());
    }
  }

  @Override
  public void deleteById(int id) {
    this.journeys.remove(id);
    this.removePending(id);
  }

  @Override
  public void clearAll() {
    this.journeys.clear();
    this.pendingOrder.clear();
    this.pendingIndex.clear();
  }

  /**
   * Removes a journey id from pending structures.
   *
   * <p>We remove from queue using {@link ConcurrentLinkedQueue#remove(Object)} which is O(n), but
   * acceptable for the coding challenge and keeps the repository interface unchanged.
   */
  private void removePending(int journeyId) {
    this.pendingIndex.remove(journeyId);
    this.pendingOrder.remove(journeyId);
  }
}
