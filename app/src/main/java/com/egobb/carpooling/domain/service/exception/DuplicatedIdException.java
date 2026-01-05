package com.egobb.carpooling.domain.service.exception;

public class DuplicatedIdException extends RuntimeException {
  private static final long serialVersionUID = -6778775424564816265L;

  public DuplicatedIdException(String message) {
    super(message);
  }
}
