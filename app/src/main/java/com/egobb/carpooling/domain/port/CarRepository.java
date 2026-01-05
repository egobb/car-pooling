package com.egobb.carpooling.domain.port;

import com.egobb.carpooling.domain.model.Car;
import java.util.List;

public interface CarRepository {

  /** Returns the current list of cars in the system. */
  List<Car> findAll();

  /** Resets the current fleet with the given list of cars. Any previous state is discarded. */
  void reset(List<Car> cars);
}
