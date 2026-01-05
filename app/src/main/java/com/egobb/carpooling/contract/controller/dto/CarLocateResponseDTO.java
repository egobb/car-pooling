package com.egobb.carpooling.contract.controller.dto;

public class CarLocateResponseDTO {

  private final int id;
  private final int seats;
  private final int availableSeats;

  public CarLocateResponseDTO(int id, int seats, int availableSeats) {
    this.id = id;
    this.seats = seats;
    this.availableSeats = availableSeats;
  }

  public int getId() {
    return this.id;
  }

  public int getSeats() {
    return this.seats;
  }

  public int getAvailableSeats() {
    return this.availableSeats;
  }
}
