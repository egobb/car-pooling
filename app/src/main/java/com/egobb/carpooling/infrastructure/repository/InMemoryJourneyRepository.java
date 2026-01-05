package com.egobb.carpooling.infrastructure.repository;

import com.egobb.carpooling.domain.model.Journey;
import com.egobb.carpooling.domain.port.JourneyRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryJourneyRepository implements JourneyRepository {

  // All journeys (both assigned and pending), keyed by id
  private final Map<Integer, Journey> journeys = new LinkedHashMap<>();

  // Subset of journeys that are pending, keyed by id to preserve insertion order
  private final Map<Integer, Journey> pending = new LinkedHashMap<>();

  @Override
  public List<Journey> findAll() {
    return List.copyOf(this.journeys.values());
  }

  @Override
  public List<Journey> findPending() {
    return List.copyOf(this.pending.values());
  }

  @Override
  public Optional<Journey> findById(int id) {
    return Optional.ofNullable(this.journeys.get(id));
  }

  @Override
  public void save(Journey journey) {
    // Replace existing instance (by id) with the updated one
    this.journeys.put(journey.getId(), journey);

    // If it was pending but is now assigned, remove from pending
    if (journey.getAssignedTo() != null) {
      this.pending.remove(journey.getId());
    }
  }

  @Override
  public void savePending(Journey journey) {
    // Keep it in the global collection
    this.journeys.put(journey.getId(), journey);

    // And mark it as pending
    this.pending.put(journey.getId(), journey);
  }

  @Override
  public void deleteById(int id) {
    this.journeys.remove(id);
    this.pending.remove(id);
  }

  @Override
  public void clearAll() {
    this.journeys.clear();
    this.pending.clear();
  }
}
