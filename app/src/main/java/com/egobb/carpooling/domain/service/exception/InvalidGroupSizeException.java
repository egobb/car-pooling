package com.egobb.carpooling.domain.service.exception;

public class InvalidGroupSizeException extends RuntimeException {
  private static final long serialVersionUID = 5040968722453100472L;

  public InvalidGroupSizeException(String message) {
    super(message);
  }
}
