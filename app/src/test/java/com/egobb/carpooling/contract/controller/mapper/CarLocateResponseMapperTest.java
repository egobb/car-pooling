package com.egobb.carpooling.contract.controller.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.egobb.carpooling.contract.controller.dto.CarLocateResponseDTO;
import com.egobb.carpooling.domain.model.Car;
import org.junit.jupiter.api.Test;

class CarLocateResponseMapperTest {

  @Test
  void toLocateResponse_returnsNull_whenCarIsNull() {
    assertNull(
        CarLocateResponseMapper.toLocateResponse(null),
        "Mapper should return null when input car is null");
  }

  @Test
  void toLocateResponse_mapsFieldsCorrectly_forValidCar() {
    final Car car = new Car(10, 6);
    car.setAvailableSeats(3); // Simulate partial availability

    final CarLocateResponseDTO dto = CarLocateResponseMapper.toLocateResponse(car);

    assertNotNull(dto, "DTO should not be null");
    assertEquals(10, dto.getId(), "ID should match");
    assertEquals(6, dto.getSeats(), "Seats should match car.maxSeats");
    assertEquals(3, dto.getAvailableSeats(), "Available seats should match");
  }

  @Test
  void toLocateResponse_returnsFullAvailability_whenCarIsUnused() {
    final Car car = new Car(7, 4); // All seats available

    final CarLocateResponseDTO dto = CarLocateResponseMapper.toLocateResponse(car);

    assertNotNull(dto);
    assertEquals(7, dto.getId());
    assertEquals(4, dto.getSeats());
    assertEquals(4, dto.getAvailableSeats());
  }
}
