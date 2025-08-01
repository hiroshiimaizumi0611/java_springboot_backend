package com.capgemini.estimate.poc.estimate_api.exception;

public class UserNotFoundException extends RuntimeException {
  public UserNotFoundException(String username) {
    super("User Not Found.");
  }
}
