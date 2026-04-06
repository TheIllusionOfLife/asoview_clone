package com.asoviewclone.common.error;

public class ConflictException extends DomainException {

  public ConflictException(String message) {
    super("CONFLICT", message);
  }
}
