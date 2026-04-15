package com.asoviewclone.ticketing.exception;

public class RateLimitedException extends RuntimeException {

  public RateLimitedException(String message) {
    super(message);
  }
}
