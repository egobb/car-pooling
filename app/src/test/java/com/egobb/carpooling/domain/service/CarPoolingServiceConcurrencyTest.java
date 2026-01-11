package com.egobb.carpooling.domain.service;

import com.egobb.carpooling.domain.model.Car;
import com.egobb.carpooling.domain.model.Journey;
import com.egobb.carpooling.infrastructure.repository.InMemoryCarRepository;
import com.egobb.carpooling.infrastructure.repository.InMemoryJourneyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests for the fine-grained in-memory strategy.
 *
 * Test philosophy: - Avoid brittle timing assertions. - Validate invariants
 * under concurrent load.
 *
 * Synchronization pattern: - We DO NOT use a "ready latch" counting all tasks,
 * because a fixed-size thread pool will only start N tasks at a time, which can
 * deadlock if tasks block waiting for "start". - Instead we use: - start latch:
 * release all tasks at once (as they get scheduled). - done latch : wait for
 * all tasks to finish.
 */
class CarPoolingServiceConcurrencyTest {

	private ExecutorService executor;

	@AfterEach
	void tearDown() {
		if (this.executor != null) {
			this.executor.shutdownNow();
		}
	}

	@Test
	void concurrentNewJourneysDoNotOverbookSeats() throws Exception {
		// Single car with 6 seats. Many concurrent journeys of size 1.
		// Expected:
		// - At most 6 journeys assigned
		// - Remaining journeys pending
		// - Car seats never negative
		// - Seat accounting is consistent with assigned journeys

		final InMemoryCarRepository carRepository = new InMemoryCarRepository();
		final InMemoryJourneyRepository journeyRepository = new InMemoryJourneyRepository();
		final CarPoolingService service = new CarPoolingService(carRepository, journeyRepository);

		final Car car = new Car(1, 6);
		service.resetCars(List.of(car));

		final int threads = 32;
		final int totalJourneys = 200;

		this.executor = Executors.newFixedThreadPool(threads);

		final CountDownLatch start = new CountDownLatch(1);
		final CountDownLatch done = new CountDownLatch(totalJourneys);

		for (int i = 0; i < totalJourneys; i++) {
			final int journeyId = i + 1;
			this.executor.submit(() -> {
				await(start);

				// Each journey size is 1.
				final Journey j = new Journey(journeyId, 1);
				service.newJourney(j);

				done.countDown();
			});
		}

		// All tasks have been submitted at this point. Now release them.
		start.countDown();
		assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();

		// Snapshot of state
		final List<Journey> all = journeyRepository.findAll();
		final List<Journey> pending = journeyRepository.findPending();
		final List<Car> cars = carRepository.findAll();

		assertThat(cars).hasSize(1);
		final Car storedCar = cars.get(0);

		// Invariant 1: car available seats are within bounds
		assertThat(storedCar.getAvailableSeats()).isBetween(0, storedCar.getMaxSeats());

		// Invariant 2: assigned journeys count <= max seats (since each uses 1 seat)
		final long assignedCount = all.stream().filter(j -> j.getAssignedTo() != null).count();
		assertThat(assignedCount).isLessThanOrEqualTo(storedCar.getMaxSeats());

		// Invariant 3: seat accounting matches assigned journeys
		final int assignedSeats = (int) assignedCount; // because each has 1 passenger
		assertThat(storedCar.getAvailableSeats()).isEqualTo(storedCar.getMaxSeats() - assignedSeats);

		// Invariant 4: total journeys = assigned + pending
		assertThat(all.size()).isEqualTo(totalJourneys);
		assertThat(pending.size()).isEqualTo(totalJourneys - (int) assignedCount);
	}

	@Test
	void concurrentDropoffsRestoreSeatsAndRemoveJourneys() throws Exception {
		// Fill a car, then concurrently dropoff all journeys.
		// Expected:
		// - Car seats restored to max
		// - Repository has no journeys

		final InMemoryCarRepository carRepository = new InMemoryCarRepository();
		final InMemoryJourneyRepository journeyRepository = new InMemoryJourneyRepository();
		final CarPoolingService service = new CarPoolingService(carRepository, journeyRepository);

		final Car car = new Car(1, 6);
		service.resetCars(List.of(car));

		// Create exactly 6 journeys with 1 passenger so the car is full.
		for (int i = 1; i <= 6; i++) {
			service.newJourney(new Journey(i, 1));
		}

		// Sanity: car is full
		assertThat(car.getAvailableSeats()).isEqualTo(0);

		final int threads = 12;
		this.executor = Executors.newFixedThreadPool(threads);

		final CountDownLatch start = new CountDownLatch(1);
		final CountDownLatch done = new CountDownLatch(6);

		for (int i = 1; i <= 6; i++) {
			final int journeyId = i;
			this.executor.submit(() -> {
				await(start);

				service.dropoff(journeyId);

				done.countDown();
			});
		}

		start.countDown();
		assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

		// Invariant: all seats are restored
		assertThat(car.getAvailableSeats()).isEqualTo(car.getMaxSeats());

		// Invariant: journeys removed
		assertThat(journeyRepository.findAll()).isEmpty();
		assertThat(journeyRepository.findPending()).isEmpty();
	}

	@Test
	void dropoffReassignsOldestPendingJourneyWhenSeatsBecomeAvailable() {
		// Scenario:
		// - Car with 4 seats
		// - Journey A(4) is assigned (car full)
		// - Journey B(1) is pending
		// - Dropoff A should free seats and trigger reassignment of B

		final InMemoryCarRepository carRepository = new InMemoryCarRepository();
		final InMemoryJourneyRepository journeyRepository = new InMemoryJourneyRepository();
		final CarPoolingService service = new CarPoolingService(carRepository, journeyRepository);

		final Car car = new Car(1, 4);
		service.resetCars(List.of(car));

		final Journey a = new Journey(1, 4);
		final Journey b = new Journey(2, 1);

		service.newJourney(a);
		service.newJourney(b);

		// A should be assigned, B should be pending
		assertThat(a.getAssignedTo()).isNotNull();
		assertThat(b.getAssignedTo()).isNull();
		assertThat(car.getAvailableSeats()).isEqualTo(0);

		service.dropoff(1);

		// After dropoff, B should have been reassigned to the car (oldest pending that
		// fits).
		final Journey bStored = journeyRepository.findById(2).orElseThrow(AssertionError::new);
		assertThat(bStored.getAssignedTo()).isNotNull();
		assertThat(bStored.getAssignedTo().getId()).isEqualTo(1);

		// Seats accounting: max=4, B uses 1 => available=3
		assertThat(car.getAvailableSeats()).isEqualTo(3);
	}

	@Test
	void concurrentNewJourneyAndDropoffDoesNotBreakInvariants() throws Exception {
		// Mix dropoff operations with concurrent new journeys.
		// Expected:
		// - No invalid seat values
		// - Seat accounting stays consistent
		// - No journey is assigned to an unknown car

		final InMemoryCarRepository carRepository = new InMemoryCarRepository();
		final InMemoryJourneyRepository journeyRepository = new InMemoryJourneyRepository();
		final CarPoolingService service = new CarPoolingService(carRepository, journeyRepository);

		final Car car = new Car(1, 6);
		service.resetCars(List.of(car));

		// Pre-fill: 6 journeys of size 1 => car full.
		for (int i = 1; i <= 6; i++) {
			service.newJourney(new Journey(i, 1));
		}
		assertThat(car.getAvailableSeats()).isEqualTo(0);

		final int threads = 24;
		this.executor = Executors.newFixedThreadPool(threads);

		final int extraJourneys = 120;
		final int baseJourneys = 6;
		final int totalTasks = baseJourneys + extraJourneys;

		final CountDownLatch start = new CountDownLatch(1);
		final CountDownLatch done = new CountDownLatch(totalTasks);

		// Dropoffs
		for (int i = 1; i <= baseJourneys; i++) {
			final int journeyId = i;
			this.executor.submit(() -> {
				await(start);
				service.dropoff(journeyId);
				done.countDown();
			});
		}

		// New journeys (size 1)
		for (int i = 1; i <= extraJourneys; i++) {
			final int journeyId = baseJourneys + i;
			this.executor.submit(() -> {
				await(start);
				service.newJourney(new Journey(journeyId, 1));
				done.countDown();
			});
		}

		start.countDown();
		assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();

		// Invariant: availableSeats always within bounds
		assertThat(car.getAvailableSeats()).isBetween(0, car.getMaxSeats());

		// Invariant: seat accounting matches number of assigned journeys (all size 1)
		final List<Journey> all = journeyRepository.findAll();
		final long assignedCount = all.stream().filter(j -> j.getAssignedTo() != null).count();
		assertThat(car.getAvailableSeats()).isEqualTo(car.getMaxSeats() - (int) assignedCount);

		// Invariant: no journey is assigned to an unknown car (only car id=1 exists)
		for (final Journey j : all) {
			if (j.getAssignedTo() != null) {
				assertThat(j.getAssignedTo().getId()).isEqualTo(1);
			}
		}
	}

	@Test
	void twoCarsCanBeUpdatedInParallelWithoutGlobalContention() throws Exception {
		// Two cars, many concurrent journeys.
		// Expected:
		// - At most 12 journeys assigned (two cars * 6 seats, all journeys are size 1)
		// - Seat accounting consistent per car
		// - Both cars receive at least one assigned journey

		final InMemoryCarRepository carRepository = new InMemoryCarRepository();
		final InMemoryJourneyRepository journeyRepository = new InMemoryJourneyRepository();
		final CarPoolingService service = new CarPoolingService(carRepository, journeyRepository);

		final Car car1 = new Car(1, 6);
		final Car car2 = new Car(2, 6);
		service.resetCars(List.of(car1, car2));

		final int threads = 32;
		final int totalJourneys = 200;

		this.executor = Executors.newFixedThreadPool(threads);

		final CountDownLatch start = new CountDownLatch(1);
		final CountDownLatch done = new CountDownLatch(totalJourneys);

		for (int i = 0; i < totalJourneys; i++) {
			final int journeyId = i + 1;
			this.executor.submit(() -> {
				await(start);
				service.newJourney(new Journey(journeyId, 1));
				done.countDown();
			});
		}

		start.countDown();
		assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();

		// Invariants: each car within bounds
		assertThat(car1.getAvailableSeats()).isBetween(0, car1.getMaxSeats());
		assertThat(car2.getAvailableSeats()).isBetween(0, car2.getMaxSeats());

		final List<Journey> all = journeyRepository.findAll();
		final long assignedToCar1 = all.stream()
				.filter(j -> j.getAssignedTo() != null && j.getAssignedTo().getId() == 1).count();
		final long assignedToCar2 = all.stream()
				.filter(j -> j.getAssignedTo() != null && j.getAssignedTo().getId() == 2).count();

		final long assignedTotal = assignedToCar1 + assignedToCar2;
		assertThat(assignedTotal).isLessThanOrEqualTo(12);

		// Seat accounting per car
		assertThat(car1.getAvailableSeats()).isEqualTo(car1.getMaxSeats() - (int) assignedToCar1);
		assertThat(car2.getAvailableSeats()).isEqualTo(car2.getMaxSeats() - (int) assignedToCar2);

		// Expect both cars to get at least one assignment under this load.
		assertThat(assignedToCar1).isGreaterThan(0);
		assertThat(assignedToCar2).isGreaterThan(0);
	}

	/**
	 * Helper to await a latch without cluttering test bodies.
	 */
	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}
}
