package com.egobb.carpooling.domain.service.exception;

public class JourneyNotFoundException extends RuntimeException {
  private static final long serialVersionUID = -3432515309646914438L;

  public JourneyNotFoundException(String message) {
    super(message);
  }
}
