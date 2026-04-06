package com.asoviewclone.commercecore.payments.saga;

public enum PaymentConfirmationStepStatus {
  /** Step row inserted but {@code confirmHold} has not yet been attempted (or is in progress). */
  PENDING,
  /** {@code confirmHold} succeeded and the reservation is durable in Spanner. */
  CONFIRMED,
  /**
   * Step was previously {@link #CONFIRMED} but rolled back via {@code releaseConfirmedHold} as part
   * of saga compensation. Terminal: the saga refuses to resume any payment whose steps include a
   * COMPENSATED entry, because the underlying hold rows have been deleted and cannot be re-used.
   */
  COMPENSATED,
  /**
   * Terminal failure for a step that could not be confirmed even after compensation. The recovery
   * job will pick these up alongside PENDING for retry.
   */
  FAILED
}
