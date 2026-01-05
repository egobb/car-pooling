package com.egobb.carpooling.contract.controller.mapper;

import com.egobb.carpooling.contract.controller.dto.CarLocateResponseDTO;
import com.egobb.carpooling.domain.model.Car;

public class CarLocateResponseMapper {

  private CarLocateResponseMapper() {
    // A private constructor to hide implicit public one
  }

  public static CarLocateResponseDTO toLocateResponse(Car car) {
    if (car == null) {
      return null;
    }
    return new CarLocateResponseDTO(car.getId(), car.getMaxSeats(), car.getAvailableSeats());
  }
}
