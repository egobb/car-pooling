package com.egobb.carpooling.domain.service;

import com.egobb.carpooling.domain.model.Car;
import com.egobb.carpooling.domain.model.Journey;
import com.egobb.carpooling.domain.port.CarRepository;
import com.egobb.carpooling.domain.port.JourneyRepository;
import com.egobb.carpooling.domain.service.concurrency.concurrency.CarLockRegistry;
import com.egobb.carpooling.domain.service.exception.DuplicatedIdException;
import com.egobb.carpooling.domain.service.exception.InvalidCarSeatsException;
import com.egobb.carpooling.domain.service.exception.InvalidGroupSizeException;
import com.egobb.carpooling.domain.service.exception.JourneyNotFoundException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Service;

/**
 * Core domain service implementing the Car Pooling business logic.
 *
 * <p>Concurrency strategy: cars and journeys are stored in concurrent in-memory repositories;
 * mutations to a specific car state (available seats) are guarded by a per-car lock; a global
 * read/write lock prevents concurrent traffic during administrative resets; method signatures and
 * parameter names are kept compatible with the original v1.0.0 API.
 */
@Service
public class CarPoolingService {

  private final CarRepository carRepository;
  private final JourneyRepository journeyRepository;
  private final CarLockRegistry carLocks;

  /**
   * Guards administrative operations versus runtime operations. resetCars(...) takes the write
   * lock; newJourney, dropoff, reassign and locate take the read lock.
   */
  private final ReadWriteLock stateLock = new ReentrantReadWriteLock();

  /** Serializes journey ID creation. */
  private final Object journeyCreationLock = new Object();

  public CarPoolingService(CarRepository carRepository, JourneyRepository journeyRepository) {
    this.carRepository = carRepository;
    this.journeyRepository = journeyRepository;
    this.carLocks = new CarLockRegistry();
  }

  /**
   * Replaces the current fleet of cars with the provided list.
   *
   * <p>This is treated as an administrative operation. It clears the car fleet and all journeys.
   */
  public void resetCars(List<Car> newCars) {
    final Lock writeLock = this.stateLock.writeLock();
    writeLock.lock();
    try {
      this.validateCars(newCars);
      this.carRepository.reset(newCars);
      this.journeyRepository.clearAll();
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * Processes a new journey request.
   *
   * <p>Validates group size; checks for duplicate IDs; tries to assign the journey to a suitable
   * car using a best-fit strategy; otherwise stores it as pending.
   */
  public void newJourney(Journey journey) {
    final Lock readLock = this.stateLock.readLock();
    readLock.lock();
    try {
      this.validateGroupSize(journey.getPassengers());

      // Ensure ID uniqueness even under concurrency.
      synchronized (this.journeyCreationLock) {
        this.ensureJourneyIdIsUnique(journey.getId());

        // Try best-fit assignment using optimistic scan + per-car lock.
        final Optional<Car> selectedCar = this.findBestFitCar(journey.getPassengers());
        if (selectedCar.isPresent()) {
          final Car car = selectedCar.get();
          final ReentrantLock carLock = this.carLocks.lockFor(car.getId());
          carLock.lock();
          try {
            // Re-check under lock.
            if (car.getAvailableSeats() >= journey.getPassengers()) {
              this.assignJourneyToCar(journey, car);
              this.journeyRepository.save(journey);
              return;
            }
          } finally {
            carLock.unlock();
          }
        }

        // No car available right now -> mark as pending.
        this.journeyRepository.savePending(journey);
      }
    } finally {
      readLock.unlock();
    }
  }

  /**
   * Drops off a journey.
   *
   * <p>* Frees the seats in its assigned car (if any), removes the journey from the * repository
   * and attempts to reassign pending journeys into the freed car.
   *
   * @return the car that the journey was assigned to, or null if it was pending.
   */
  public Car dropoff(int journeyId) {
    final Lock readLock = this.stateLock.readLock();
    readLock.lock();
    try {
      final Journey journey =
          this.journeyRepository
              .findById(journeyId)
              .orElseThrow(() -> new JourneyNotFoundException("journey not found"));

      final Car car = journey.getAssignedTo();
      this.journeyRepository.deleteById(journeyId);

      if (car == null) {
        return null;
      }

      final ReentrantLock carLock = this.carLocks.lockFor(car.getId());
      carLock.lock();
      try {
        // Free seats.
        car.setAvailableSeats(car.getAvailableSeats() + journey.getPassengers());

        // Try to reassign pending journeys that now fit.
        this.reassignUnderCarLock(car);
      } finally {
        carLock.unlock();
      }

      return car;
    } finally {
      readLock.unlock();
    }
  }

  /**
   * Returns the car currently assigned to a given journey.
   *
   * @return the assigned car, or null if the journey is pending.
   */
  public Car locate(int journeyId) {
    final Lock readLock = this.stateLock.readLock();
    readLock.lock();
    try {
      return this.journeyRepository
          .findById(journeyId)
          .orElseThrow(() -> new JourneyNotFoundException("journey not found"))
          .getAssignedTo();
    } finally {
      readLock.unlock();
    }
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /**
   * Best-fit strategy: among all cars that can seat the group, picks the one with the least
   * remaining available seats to minimize wasted capacity.
   *
   * <p>Concurrency note: an optimistic scan without locks is used to find a candidate; locking is
   * only applied when mutating a specific car.
   */
  private Optional<Car> findBestFitCar(int requiredSeats) {
    return this.carRepository.findAll().stream()
        .filter(c -> c.getAvailableSeats() >= requiredSeats)
        .min(Comparator.comparingInt(c -> c.getAvailableSeats() - requiredSeats));
  }

  /**
   * Reassigns at most one pending journey into the given car.
   *
   * <p>Precondition: the caller must already hold the car lock.
   */
  private void reassignUnderCarLock(Car car) {
    // Preserve "oldest pending" semantics by iterating the pending snapshot in
    // order.
    final List<Journey> pending = this.journeyRepository.findPending();
    for (final Journey j : pending) {
      if (j.getPassengers() <= car.getAvailableSeats()) {
        this.assignJourneyToCar(j, car);
        this.journeyRepository.save(j); // will also remove it from pending in the repository
        return;
      }
    }
  }

  /**
   * Validates cars before resetting. Ensures seat counts are within allowed bounds and that car IDs
   * are unique.
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

  /**
   * Assigns the journey to the given car and updates seat availability. Precondition: The caller
   * must hold the car lock.
   */
  private void assignJourneyToCar(Journey journey, Car car) {
    journey.setAssignedTo(car);
    car.setAvailableSeats(car.getAvailableSeats() - journey.getPassengers());
  }
}
