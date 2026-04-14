package com.asoviewclone.reservation.model;

import java.time.Instant;

public record AuditLog(
    String logId,
    String reservationId,
    String action,
    String actorUserId,
    String reason,
    Instant createdAt) {}
