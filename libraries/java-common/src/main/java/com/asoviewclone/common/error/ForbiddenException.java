package com.asoviewclone.common.error;

public class ForbiddenException extends DomainException {

  public ForbiddenException(String message) {
    super("FORBIDDEN", message);
  }
}
