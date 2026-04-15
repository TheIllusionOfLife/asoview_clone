package com.asoviewclone.ticketing.model;

import java.time.Instant;

/**
 * Result of a successful redemption path (REDEEMED or an idempotency-replayed success). Terminal
 * failures throw {@link com.asoviewclone.ticketing.exception.TerminalConflictException} instead.
 */
public record RedeemResult(
    String ticketPassId,
    TicketPassStatus status,
    Instant usedAt,
    RedeemOutcome outcome,
    boolean replayed) {}
