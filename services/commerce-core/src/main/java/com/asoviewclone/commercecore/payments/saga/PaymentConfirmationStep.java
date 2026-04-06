package com.asoviewclone.commercecore.payments.saga;

import java.time.Instant;

public record PaymentConfirmationStep(
    String stepId,
    String paymentId,
    String orderItemId,
    String holdId,
    String slotId,
    long quantity,
    PaymentConfirmationStepStatus status,
    Instant attemptedAt,
    Instant updatedAt) {}
