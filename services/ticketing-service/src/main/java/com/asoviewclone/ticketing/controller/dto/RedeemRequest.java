package com.asoviewclone.ticketing.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * The {@code qrCodePayload} carries the raw string embedded in the QR (format {@code TKT-<16 HEX>}
 * — see {@code QrCodeGenerator}). The scanner app sends this verbatim; the server looks up the
 * {@code ticket_passes} row by {@code qr_code_payload}. {@code scannerDeviceId} is required for
 * audit; {@code venueId} is advisory (the authoritative venue allow-list comes from the token
 * claim), so it stays optional.
 */
public record RedeemRequest(
    @NotBlank @Pattern(regexp = "^TKT-[0-9A-Fa-f]{16}$") String qrCodePayload,
    @NotBlank String scannerDeviceId,
    String venueId) {}
