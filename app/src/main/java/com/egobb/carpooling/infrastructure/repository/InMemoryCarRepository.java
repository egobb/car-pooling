package com.egobb.carpooling.infrastructure.repository;

import com.egobb.carpooling.domain.model.Car;
import com.egobb.carpooling.domain.port.CarRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryCarRepository implements CarRepository {

  private final Map<Integer, Car> cars = new LinkedHashMap<>();

  @Override
  public List<Car> findAll() {
    // Expose an unmodifiable snapshot to avoid accidental external mutation
    return List.copyOf(this.cars.values());
  }

  @Override
  public void reset(List<Car> newCars) {
    this.cars.clear();
    for (final Car car : newCars) {
      this.cars.put(car.getId(), car);
    }
  }
}
