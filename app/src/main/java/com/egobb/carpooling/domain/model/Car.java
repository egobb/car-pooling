package com.egobb.carpooling.domain.model;

public class Car {
  private final int id;
  private final int maxSeats;
  private int availableSeats;

  public Car(int id, int seats) {
    this.id = id;
    this.maxSeats = seats;
    this.availableSeats = seats;
  }

  public int getId() {
    return this.id;
  }

  public int getMaxSeats() {
    return this.maxSeats;
  }

  public int getAvailableSeats() {
    return this.availableSeats;
  }

  public void setAvailableSeats(int availableSeats) {
    this.availableSeats = availableSeats;
  }
}
