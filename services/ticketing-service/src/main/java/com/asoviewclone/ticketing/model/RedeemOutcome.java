package com.asoviewclone.ticketing.model;

/**
 * Internal outcome code. Written to {@code scan_audit_log.outcome}. External responses collapse
 * several of these into a single generic code to avoid pass-existence leaks (see controller layer).
 */
public enum RedeemOutcome {
  REDEEMED,
  ALREADY_USED,
  EXPIRED,
  REVOKED,
  NOT_FOUND,
  VENUE_MISMATCH,
  TENANT_MISMATCH,
  ROLE_DENIED,
  FORMAT_INVALID,
  ENTITLEMENT_NOT_ACTIVE,
  OUTSIDE_VALIDITY_WINDOW,
  RATE_LIMITED,
  IDEMPOTENCY_REUSED
}
