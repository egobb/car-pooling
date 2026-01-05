package com.egobb.carpooling.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.egobb.carpooling.domain.model.Car;
import com.egobb.carpooling.domain.model.Journey;
import com.egobb.carpooling.domain.port.CarRepository;
import com.egobb.carpooling.domain.port.JourneyRepository;
import com.egobb.carpooling.domain.service.exception.DuplicatedIdException;
import com.egobb.carpooling.domain.service.exception.InvalidCarSeatsException;
import com.egobb.carpooling.domain.service.exception.InvalidGroupSizeException;
import com.egobb.carpooling.domain.service.exception.JourneyNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CarPoolingServiceTest {

  @Mock private CarRepository carRepository;

  @Mock private JourneyRepository journeyRepository;

  private CarPoolingService service;

  @BeforeEach
  void setUp() {
    this.service = new CarPoolingService(this.carRepository, this.journeyRepository);
  }

  @Test
  void assignsJourneyToCarWhenSeatsAreAvailable() {
    final Car car = new Car(1, 4);

    // resetCars only validates and delegates to the repositories
    this.service.resetCars(List.of(car));
    verify(this.carRepository).reset(List.of(car));
    verify(this.journeyRepository).clearAll();

    // newJourney will ask for available cars and check journey id uniqueness
    when(this.carRepository.findAll()).thenReturn(List.of(car));
    when(this.journeyRepository.findById(1)).thenReturn(Optional.empty());

    final Journey journey = new Journey(1, 4);

    this.service.newJourney(journey);

    // The journey should be assigned to the car and all seats consumed
    assertThat(journey.getAssignedTo()).isEqualTo(car);
    assertThat(car.getAvailableSeats()).isEqualTo(0);

    // The journey must be stored as an active journey
    verify(this.journeyRepository).save(journey);
  }

  @Test
  void keepsJourneyWaitingWhenNoCarCanServeIt() {
    final Car car = new Car(1, 4);
    when(this.carRepository.findAll()).thenReturn(List.of(car));
    when(this.journeyRepository.findById(1)).thenReturn(Optional.empty());

    final Journey journey = new Journey(1, 6);

    this.service.newJourney(journey);

    // No car can serve this journey, it must stay pending and unassigned
    assertThat(journey.getAssignedTo()).isNull();

    verify(this.journeyRepository).savePending(journey);
    verify(this.journeyRepository, never()).save(journey);
  }

  @Test
  void reassignsWaitingJourneyWhenSeatsBecomeAvailableAfterDropoff() {
    final Car car = new Car(1, 4);

    // Simulate that car is fully occupied by journeyA
    car.setAvailableSeats(0);

    final Journey journeyA = new Journey(1, 4);
    journeyA.setAssignedTo(car);

    final Journey journeyB = new Journey(2, 4); // pending

    when(this.journeyRepository.findById(1)).thenReturn(Optional.of(journeyA));
    when(this.journeyRepository.findPending()).thenReturn(List.of(journeyB));

    final Car freedCar = this.service.dropoff(1);

    // Journey A is removed from the repository
    verify(this.journeyRepository).deleteById(1);

    // After dropoff + reassignment, all seats should be used again by journeyB
    assertThat(freedCar.getAvailableSeats()).isEqualTo(0);

    // Journey B should now be assigned to the freed car
    assertThat(journeyB.getAssignedTo()).isEqualTo(freedCar);

    // And persisted as an active journey
    verify(this.journeyRepository).save(journeyB);
  }

  @Test
  void allowsSmallerGroupToBeServedBeforeEarlierBiggerGroupWhenNoCarCanServeTheBiggerOne() {
    final Car car = new Car(1, 6);
    when(this.carRepository.findAll()).thenReturn(List.of(car));

    // Existing 2-people journey uses the car first
    final Journey existing = new Journey(100, 2);
    when(this.journeyRepository.findById(100)).thenReturn(Optional.empty());

    this.service.newJourney(existing);

    assertThat(existing.getAssignedTo()).isEqualTo(car);
    assertThat(car.getAvailableSeats()).isEqualTo(4);

    // Big group of 6 cannot be served with only 4 remaining seats
    final Journey bigGroup = new Journey(1, 6);
    when(this.journeyRepository.findById(1)).thenReturn(Optional.empty());

    this.service.newJourney(bigGroup);

    assertThat(bigGroup.getAssignedTo()).isNull();
    verify(this.journeyRepository).savePending(bigGroup);

    // A smaller group can still be served
    final Journey smallGroup = new Journey(2, 2);
    when(this.journeyRepository.findById(2)).thenReturn(Optional.empty());

    this.service.newJourney(smallGroup);

    assertThat(smallGroup.getAssignedTo()).isEqualTo(car);
    assertThat(car.getAvailableSeats()).isEqualTo(2);
    verify(this.journeyRepository).save(smallGroup);
  }

  @Test
  void throwsOnDuplicatedJourneyId() {
    when(this.journeyRepository.findById(1)).thenReturn(Optional.of(new Journey(1, 2)));

    final Journey duplicated = new Journey(1, 3);

    // Registering a journey with an already used id must fail
    assertThatThrownBy(() -> this.service.newJourney(duplicated))
        .isInstanceOf(DuplicatedIdException.class);

    verify(this.journeyRepository, never()).save(any());
    verify(this.journeyRepository, never()).savePending(any());
  }

  @Test
  void rejectsJourneyWithInvalidGroupSize() {
    // Group size must be between 1 and 6
    assertThatThrownBy(() -> this.service.newJourney(new Journey(1, 0)))
        .isInstanceOf(InvalidGroupSizeException.class);

    assertThatThrownBy(() -> this.service.newJourney(new Journey(2, 7)))
        .isInstanceOf(InvalidGroupSizeException.class);

    verifyNoInteractions(this.journeyRepository, this.carRepository);
  }

  @Test
  void rejectsCarsWithInvalidSeatConfigurationOnReset() {
    final Car invalidLow = new Car(1, 3);
    final Car invalidHigh = new Car(2, 7);

    assertThatThrownBy(() -> this.service.resetCars(List.of(invalidLow)))
        .isInstanceOf(InvalidCarSeatsException.class);

    assertThatThrownBy(() -> this.service.resetCars(List.of(invalidHigh)))
        .isInstanceOf(InvalidCarSeatsException.class);

    verifyNoInteractions(this.carRepository, this.journeyRepository);
  }

  @Test
  void rejectsCarsWithDuplicatedIdsOnReset() {
    final Car car1 = new Car(1, 4);
    final Car car2 = new Car(1, 5); // same id

    assertThatThrownBy(() -> this.service.resetCars(List.of(car1, car2)))
        .isInstanceOf(DuplicatedIdException.class);

    verifyNoInteractions(this.carRepository, this.journeyRepository);
  }

  @Test
  void usesBestFitCarWhenMultipleCarsCanServeTheJourney() {
    final Car car1 = new Car(1, 6); // would leave 2 seats
    final Car car2 = new Car(2, 4); // would leave 0 seats

    when(this.carRepository.findAll()).thenReturn(List.of(car1, car2));
    when(this.journeyRepository.findById(1)).thenReturn(Optional.empty());

    final Journey journey = new Journey(1, 4);

    this.service.newJourney(journey);

    // Best-fit strategy must choose car2 (4 seats)
    assertThat(journey.getAssignedTo()).isEqualTo(car2);
    assertThat(car2.getAvailableSeats()).isEqualTo(0);
    assertThat(car1.getAvailableSeats()).isEqualTo(6);

    verify(this.journeyRepository).save(journey);
  }

  @Test
  void locateThrowsWhenJourneyDoesNotExist() {
    when(this.journeyRepository.findById(99)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> this.service.locate(99)).isInstanceOf(JourneyNotFoundException.class);
  }
}
