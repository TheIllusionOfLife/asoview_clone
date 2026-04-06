package com.asoviewclone.commercecore.payments.saga;

public enum PaymentConfirmationStepStatus {
  PENDING,
  CONFIRMED,
  COMPENSATED,
  /**
   * Terminal failure for a step that could not be confirmed even after compensation. The recovery
   * job will pick these up alongside PENDING for retry.
   */
  FAILED
}
