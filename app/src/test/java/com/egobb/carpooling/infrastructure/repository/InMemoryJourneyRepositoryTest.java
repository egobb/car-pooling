package com.egobb.carpooling.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.egobb.carpooling.domain.model.Car;
import com.egobb.carpooling.domain.model.Journey;
import com.egobb.carpooling.domain.port.JourneyRepository;
import org.junit.jupiter.api.Test;

class InMemoryJourneyRepositoryTest {

  @Test
  void savesAndFindsJourney() {
    final JourneyRepository repo = new InMemoryJourneyRepository();
    final Journey journey = new Journey(1, 3);

    repo.save(journey);

    // Repository should contain exactly this journey
    assertThat(repo.findById(1)).contains(journey);
    assertThat(repo.findAll()).containsExactly(journey);
  }

  @Test
  void savesPendingJourneySeparately() {
    final JourneyRepository repo = new InMemoryJourneyRepository();
    final Journey pending = new Journey(1, 4);

    repo.savePending(pending);

    // Pending journey should appear in both collections
    assertThat(repo.findAll()).containsExactly(pending);
    assertThat(repo.findPending()).containsExactly(pending);
  }

  @Test
  void saveRemovesPendingFlagWhenJourneyIsAssigned() {
    final JourneyRepository repo = new InMemoryJourneyRepository();

    final Journey pending = new Journey(1, 4);
    repo.savePending(pending);

    // Assign the journey to a car and save as active
    final Car car = new Car(10, 6);
    pending.setAssignedTo(car);

    repo.save(pending);

    assertThat(repo.findAll()).containsExactly(pending);
    assertThat(repo.findPending()).isEmpty();
  }

  @Test
  void deleteRemovesFromAllCollections() {
    final JourneyRepository repo = new InMemoryJourneyRepository();

    final Journey active = new Journey(1, 2);
    final Journey pending = new Journey(2, 3);

    repo.save(active);
    repo.savePending(pending);

    repo.deleteById(1);
    repo.deleteById(2);

    assertThat(repo.findAll()).isEmpty();
    assertThat(repo.findPending()).isEmpty();
  }

  @Test
  void clearAllRemovesAllJourneys() {
    final JourneyRepository repo = new InMemoryJourneyRepository();

    repo.save(new Journey(1, 2));
    repo.savePending(new Journey(2, 3));

    repo.clearAll();

    assertThat(repo.findAll()).isEmpty();
    assertThat(repo.findPending()).isEmpty();
  }
}
