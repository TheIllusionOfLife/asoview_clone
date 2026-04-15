package com.asoviewclone.ticketing.controller;

import com.asoviewclone.ticketing.controller.dto.RedeemRequest;
import com.asoviewclone.ticketing.controller.dto.RedeemResponse;
import com.asoviewclone.ticketing.exception.NotScannableException;
import com.asoviewclone.ticketing.exception.RateLimitedException;
import com.asoviewclone.ticketing.exception.TerminalConflictException;
import com.asoviewclone.ticketing.model.RedeemResult;
import com.asoviewclone.ticketing.service.TicketRedemptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class TicketScannerController {

  private final TicketRedemptionService service;

  public TicketScannerController(TicketRedemptionService service) {
    this.service = service;
  }

  @PostMapping("/v1/op/tickets/redeem")
  public ResponseEntity<RedeemResponse> redeem(
      @RequestHeader(value = "Idempotency-Key", required = false)
          @Pattern(regexp = "^[0-9a-fA-F-]{32,64}$")
          String idempotencyKey,
      @Valid @RequestBody RedeemRequest req,
      HttpServletRequest http) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return ResponseEntity.badRequest()
          .body(new RedeemResponse(null, null, null, "IDEMPOTENCY_REQUIRED"));
    }
    RedeemResult r =
        service.redeem(req.qrCodePayload(), req.scannerDeviceId(), clientIp(http), idempotencyKey);
    return ResponseEntity.ok(
        new RedeemResponse(r.ticketPassId(), r.status().name(), r.usedAt(), r.outcome().name()));
  }

  @PostMapping("/v1/op/tickets/{passId}/revoke")
  public ResponseEntity<Void> revoke(
      @PathVariable @NotBlank String passId,
      @RequestBody(required = false) Map<String, Object> body) {
    service.revoke(passId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Extract the client IP from {@code X-Forwarded-For} when behind a trusted gateway (GKE ingress
   * sets this), falling back to {@code getRemoteAddr()} for direct calls. {@code getRemoteAddr()}
   * alone returns the gateway pod IP in production, which would make every scanner share one
   * rate-limit bucket.
   */
  static String clientIp(HttpServletRequest http) {
    String xff = http.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      int comma = xff.indexOf(',');
      String first = (comma > 0 ? xff.substring(0, comma) : xff).trim();
      if (!first.isEmpty()) {
        return first;
      }
    }
    return http.getRemoteAddr();
  }

  // ---- Unified ProblemDetail-style error mapping ----

  @ExceptionHandler(NotScannableException.class)
  public ResponseEntity<Map<String, Object>> handleNotScannable(NotScannableException e) {
    // Tenant mismatch, venue mismatch, not found all collapse to this single 404 shape.
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("code", "TICKET_NOT_SCANNABLE", "detail", "Ticket not valid at this gate"));
  }

  @ExceptionHandler(TerminalConflictException.class)
  public ResponseEntity<Map<String, Object>> handleTerminal(TerminalConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", e.getOutcome(), "detail", e.getMessage()));
  }

  @ExceptionHandler(RateLimitedException.class)
  public ResponseEntity<Map<String, Object>> handleRateLimited(RateLimitedException e) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .body(Map.of("code", "RATE_LIMITED", "detail", "Too many requests"));
  }

  @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(
      jakarta.validation.ConstraintViolationException e) {
    return ResponseEntity.badRequest()
        .body(Map.of("code", "VALIDATION_ERROR", "detail", e.getMessage()));
  }

  @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleBodyValidation(
      org.springframework.web.bind.MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest()
        .body(Map.of("code", "VALIDATION_ERROR", "detail", "Invalid request body"));
  }
}
