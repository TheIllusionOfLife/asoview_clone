package com.asoviewclone.common.error;

public class NotFoundException extends DomainException {

  public NotFoundException(String entityType, String id) {
    super("NOT_FOUND", entityType + " not found: " + id);
  }
}
