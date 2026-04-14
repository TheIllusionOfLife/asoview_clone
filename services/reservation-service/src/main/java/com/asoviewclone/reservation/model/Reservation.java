package com.asoviewclone.reservation.model;

import java.time.Instant;

public record Reservation(
    String reservationId,
    String tenantId,
    String venueId,
    String slotId,
    String consumerUserId,
    ReservationStatus status,
    String idempotencyKey,
    String guestName,
    String guestEmail,
    int guestCount,
    String rejectReason,
    String cancelReason,
    Instant createdAt,
    Instant updatedAt) {}
