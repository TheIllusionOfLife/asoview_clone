package com.asoviewclone.ticketing.exception;

/**
 * Terminal conflict: the pass cannot be redeemed and the scanner must NOT retry. Examples:
 * ALREADY_USED, EXPIRED, REVOKED, ENTITLEMENT_NOT_ACTIVE, OUTSIDE_VALIDITY_WINDOW.
 */
public class TerminalConflictException extends RuntimeException {

  private final String outcome;

  public TerminalConflictException(String outcome, String message) {
    super(message);
    this.outcome = outcome;
  }

  public String getOutcome() {
    return outcome;
  }
}
