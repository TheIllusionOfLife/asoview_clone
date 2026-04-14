package com.asoviewclone.reservation.model;

public enum ReservationStatus {
  PENDING_APPROVAL,
  APPROVED,
  WAITLISTED,
  REJECTED,
  CANCELLED,
  COMPLETED;

  public boolean isTerminal() {
    return this == REJECTED || this == CANCELLED || this == COMPLETED;
  }
}
