package com.asoviewclone.common.error;

public abstract class DomainException extends RuntimeException {

  private final String errorCode;

  protected DomainException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
