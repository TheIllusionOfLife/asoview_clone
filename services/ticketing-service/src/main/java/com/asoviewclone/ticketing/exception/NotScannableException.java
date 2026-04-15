package com.asoviewclone.ticketing.exception;

/**
 * Thrown for every scan-path failure whose external response MUST be masked to a generic 404
 * TICKET_NOT_SCANNABLE — NOT_FOUND, TENANT_MISMATCH, VENUE_MISMATCH. The internal {@code outcome}
 * remains distinct in the audit log.
 */
public class NotScannableException extends RuntimeException {

  public NotScannableException(String message) {
    super(message);
  }
}
