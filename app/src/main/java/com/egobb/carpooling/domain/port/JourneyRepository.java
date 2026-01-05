package com.egobb.carpooling.domain.port;

import com.egobb.carpooling.domain.model.Journey;
import java.util.List;
import java.util.Optional;

public interface JourneyRepository {

  /** Returns all journeys (both assigned and pending). */
  List<Journey> findAll();

  /** Returns all pending journeys. */
  List<Journey> findPending();

  /** Finds a journey by id, if it exists. */
  Optional<Journey> findById(int id);

  /** Saves or updates an active journey (assigned or not pending). */
  void save(Journey journey);

  /**
   * Saves a journey as pending. The journey is kept in the global collection and marked as pending.
   */
  void savePending(Journey journey);

  /** Deletes a journey by id from both active and pending collections. */
  void deleteById(int id);

  /** Clears all journeys and pending state. */
  void clearAll();
}
