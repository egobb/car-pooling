package com.egobb.carpooling.domain.service.exception;

public class InvalidCarSeatsException extends RuntimeException {
  private static final long serialVersionUID = 339051695916287369L;

  public InvalidCarSeatsException(String message) {
    super(message);
  }
}
