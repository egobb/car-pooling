package com.egobb.carpooling.domain.model;

public class Journey {
  private int id;
  private final int passengers;
  private Car assignedTo;

  public Journey(int id, int people) {
    this.id = id;
    this.passengers = people;
    this.assignedTo = null;
  }

  public int getId() {
    return this.id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public int getPassengers() {
    return this.passengers;
  }

  public Car getAssignedTo() {
    return this.assignedTo;
  }

  public void setAssignedTo(Car assignedTo) {
    this.assignedTo = assignedTo;
  }
}
