package com.egobb.carpooling.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.egobb.carpooling.domain.model.Car;
import com.egobb.carpooling.domain.port.CarRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryCarRepositoryTest {

  @Test
  void resetReplacesExistingCars() {
    final CarRepository repo = new InMemoryCarRepository();

    final Car car1 = new Car(1, 4);
    repo.reset(List.of(car1));

    assertThat(repo.findAll()).containsExactly(car1);

    final Car car2 = new Car(2, 6);
    repo.reset(List.of(car2));

    // After reset, only the new car should be present
    assertThat(repo.findAll()).containsExactly(car2);
  }

  @Test
  void findAllReturnsUnmodifiableView() {
    final CarRepository repo = new InMemoryCarRepository();

    final Car car = new Car(1, 4);
    repo.reset(List.of(car));

    final List<Car> cars = repo.findAll();

    assertThat(cars).containsExactly(car);

    // Attempting to modify the returned list should fail at runtime
    org.junit.jupiter.api.Assertions.assertThrows(
        UnsupportedOperationException.class,
        () -> {
          cars.add(new Car(2, 5));
        });
  }
}
