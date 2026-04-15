package com.asoviewclone.ticketing.controller.dto;

import java.time.Instant;

public record RedeemResponse(String ticketPassId, String status, Instant usedAt, String outcome) {}
