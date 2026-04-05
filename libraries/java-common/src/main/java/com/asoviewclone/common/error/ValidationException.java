package com.asoviewclone.common.error;

public class ValidationException extends DomainException {

  public ValidationException(String message) {
    super("VALIDATION_ERROR", message);
  }
}
