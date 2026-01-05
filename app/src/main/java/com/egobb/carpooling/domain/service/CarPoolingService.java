package com.egobb.carpooling.domain.service;

import com.egobb.carpooling.domain.model.Car;
import com.egobb.carpooling.domain.model.Journey;
import com.egobb.carpooling.domain.port.CarRepository;
import com.egobb.carpooling.domain.port.JourneyRepository;
import com.egobb.carpooling.domain.service.exception.DuplicatedIdException;
import com.egobb.carpooling.domain.service.exception.InvalidCarSeatsException;
import com.egobb.carpooling.domain.service.exception.InvalidGroupSizeException;
import com.egobb.carpooling.domain.service.exception.JourneyNotFoundException;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CarPoolingService {

  private final CarRepository carRepository;
  private final JourneyRepository journeyRepository;

  /**
   * Simple coarse-grained lock used to guarantee atomicity of operations that read and modify the
   * in-memory state (cars and journeys).
   */
  private final Object lock = new Object();

  public CarPoolingService(CarRepository carRepository, JourneyRepository journeyRepository) {
    this.carRepository = carRepository;
    this.journeyRepository = journeyRepository;
  }

  /**
   * Replaces the current fleet of cars with the provided list. Validates that seat count is within
   * allowed range and IDs are unique. Also clears all existing journeys and pending state.
   */
  public void resetCars(List<Car> newCars) {
    synchronized (this.lock) {
      this.validateCars(newCars);

      this.carRepository.reset(newCars);
      this.journeyRepository.clearAll();
    }
  }

  /**
   * Processes a new journey request. It will: - validate group size, - check for duplicate IDs, -
   * try to assign the journey to a suitable car (best-fit), - otherwise add it to the pending
   * queue.
   */
  public void newJourney(Journey journey) {
    synchronized (this.lock) {
      this.validateGroupSize(journey.getPassengers());
      this.ensureJourneyIdIsUnique(journey.getId());

      final Optional<Car> selectedCar = this.findCar(journey.getPassengers());

      if (selectedCar.isPresent()) {
        this.assignJourneyToCar(journey, selectedCar.get());
        this.journeyRepository.save(journey);
      } else {
        this.journeyRepository.savePending(journey);
      }
    }
  }

  /**
   * Drops off a journey: - Frees the seats in its assigned car (if any), - Removes the journey from
   * the repository, - Attempts to reassign pending journeys.
   */
  public Car dropoff(int journeyId) {
    synchronized (this.lock) {
      final Journey journey =
          this.journeyRepository
              .findById(journeyId)
              .orElseThrow(() -> new JourneyNotFoundException("journey not found"));

      final Car car = journey.getAssignedTo();

      this.journeyRepository.deleteById(journeyId);

      if (car != null) {
        // Free seats
        car.setAvailableSeats(car.getAvailableSeats() + journey.getPassengers());

        // Try to reassign any pending journey that now fits
        this.reassign(car);
      }

      return car;
    }
  }

  /** Tries to assign the first pending journey that fits in the given car. */
  public void reassign(Car car) {
    synchronized (this.lock) {
      this.journeyRepository.findPending().stream()
          .filter(j -> j.getPassengers() <= car.getAvailableSeats())
          .findFirst()
          .ifPresent(
              j -> {
                this.assignJourneyToCar(j, car);
                this.journeyRepository.save(j); // will also remove it from pending
              });
    }
  }

  /**
   * Returns the car currently assigned to a given journey. Throws if the journey does not exist.
   */
  public Car locate(int journeyId) {
    synchronized (this.lock) {
      return this.journeyRepository
          .findById(journeyId)
          .orElseThrow(() -> new JourneyNotFoundException("journey not found"))
          .getAssignedTo();
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /**
   * Best-fit strategy: Among all cars that can seat the group, pick the one with the least
   * remaining available seats (minimizes wasted space).
   */
  private Optional<Car> findCar(int requiredSeats) {
    return this.carRepository.findAll().stream()
        .filter(c -> c.getAvailableSeats() >= requiredSeats)
        .min(Comparator.comparingInt(Car::getAvailableSeats));
  }

  /**
   * Validates cars before resetting: Ensures seats are within allowed bounds and IDs are unique.
   */
  private void validateCars(List<Car> cars) {
    final Set<Integer> ids = new HashSet<>();

    for (final Car car : cars) {
      if (car.getMaxSeats() < 4 || car.getMaxSeats() > 6) {
        throw new InvalidCarSeatsException("invalid seats");
      }
      if (!ids.add(car.getId())) {
        throw new DuplicatedIdException("IDs are duplicated");
      }
    }
  }

  /** Ensures the group size is between 1 and 6 passengers. */
  private void validateGroupSize(int passengers) {
    if (passengers < 1 || passengers > 6) {
      throw new InvalidGroupSizeException("invalid group size");
    }
  }

  /** Ensures that the journey ID is not already used. */
  private void ensureJourneyIdIsUnique(int journeyId) {
    final boolean exists = this.journeyRepository.findById(journeyId).isPresent();
    if (exists) {
      throw new DuplicatedIdException("journey ID is already used");
    }
  }

  /** Assigns the journey to the given car and updates seat availability. */
  private void assignJourneyToCar(Journey journey, Car car) {
    journey.setAssignedTo(car);
    car.setAvailableSeats(car.getAvailableSeats() - journey.getPassengers());
  }
}
