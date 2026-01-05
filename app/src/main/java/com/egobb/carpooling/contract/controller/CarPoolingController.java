package com.egobb.carpooling.contract.controller;

import com.egobb.carpooling.contract.controller.dto.CarLocateResponseDTO;
import com.egobb.carpooling.contract.controller.mapper.CarLocateResponseMapper;
import com.egobb.carpooling.domain.model.Car;
import com.egobb.carpooling.domain.model.Journey;
import com.egobb.carpooling.domain.service.CarPoolingService;
import com.egobb.carpooling.domain.service.exception.DuplicatedIdException;
import com.egobb.carpooling.domain.service.exception.InvalidCarSeatsException;
import com.egobb.carpooling.domain.service.exception.InvalidGroupSizeException;
import com.egobb.carpooling.domain.service.exception.JourneyNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
public class CarPoolingController {

  private final CarPoolingService carJourneyService;

  public CarPoolingController(CarPoolingService carJourneyService) {
    this.carJourneyService = carJourneyService;
  }

  @GetMapping("/status")
  @ResponseStatus(HttpStatus.OK)
  public void getStatus() {
    // Simple health-check endpoint required by the acceptance tests.
  }

  @PutMapping(value = "/cars", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> putCars(@RequestBody List<Car> cars) {
    try {
      this.carJourneyService.resetCars(cars);
      return new ResponseEntity<>(HttpStatus.OK);
    } catch (final InvalidCarSeatsException | DuplicatedIdException e) {
      // Invalid car definitions (wrong seat configuration or duplicated IDs)
      // must be treated as a bad client request.
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
  }

  @PostMapping("/journey")
  public ResponseEntity<Void> postJourney(@RequestBody Journey journey) {
    // Basic guard for invalid or missing id in the payload.
    if (journey.getId() <= 0) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    try {
      this.carJourneyService.newJourney(journey);
      // For both assigned and pending journeys the API responds with 202 Accepted.
      return new ResponseEntity<>(HttpStatus.ACCEPTED);
    } catch (final InvalidGroupSizeException | DuplicatedIdException e) {
      // Invalid group size or duplicated journey id are client errors.
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
  }

  @PostMapping("/dropoff")
  public ResponseEntity<Void> postDropoff(@RequestParam("ID") int journeyID) {
    if (journeyID <= 0) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    try {
      // The service will remove the journey and, if needed, trigger reassignment.
      this.carJourneyService.dropoff(journeyID);
      return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    } catch (final JourneyNotFoundException e) {
      // Dropping off a non-existing journey must return 404.
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
  }

  @PostMapping(
      value = "/locate",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<CarLocateResponseDTO> postLocate(@RequestParam("ID") int journeyID) {
    if (journeyID <= 0) {
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
    try {
      final Car car = this.carJourneyService.locate(journeyID);

      // If the journey exists but has no assigned car yet,
      // the API responds with 204 No Content.
      if (car == null) {
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
      }

      final CarLocateResponseDTO response = CarLocateResponseMapper.toLocateResponse(car);
      return new ResponseEntity<>(response, HttpStatus.OK);

    } catch (final JourneyNotFoundException e) {
      // When the journey does not exist at all,
      // the API responds with 404
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
  }
}
