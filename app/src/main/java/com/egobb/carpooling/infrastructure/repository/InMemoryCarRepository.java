package com.egobb.carpooling.infrastructure.repository;

import com.egobb.carpooling.domain.model.Car;
import com.egobb.carpooling.domain.port.CarRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryCarRepository implements CarRepository {

  /**
   * Cars indexed by id.
   *
   * <p>Concurrency note: ConcurrentHashMap makes individual map operations thread-safe, while
   * business-level atomicity is enforced in the service layer using per-car locks.
   */
  private final ConcurrentHashMap<Integer, Car> cars = new ConcurrentHashMap<>();

  @Override
  public List<Car> findAll() {
    // Expose an unmodifiable snapshot to avoid accidental external mutation.
    // We create a defensive copy because the underlying map is concurrent.
    return List.copyOf(new ArrayList<>(this.cars.values()));
  }

  @Override
  public void reset(List<Car> newCars) {
    // This method is typically called as an administrative operation.
    // The service layer ensures there is no concurrent traffic while resetting.
    this.cars.clear();
    for (final Car car : newCars) {
      this.cars.put(car.getId(), car);
    }
  }
}
