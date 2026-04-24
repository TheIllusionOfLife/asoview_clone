package com.asoviewclone.reservation.controller;

import com.asoviewclone.reservation.exception.ConflictException;
import com.asoviewclone.reservation.exception.NotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Reservation-service-local advice for its own domain exceptions ({@link NotFoundException}, {@link
 * ConflictException}) plus {@link AccessDeniedException} and {@link IllegalArgumentException}.
 * These are different classes from the ones handled by {@code
 * com.asoviewclone.common.error.GlobalExceptionHandler}, so the shared advice cannot cover them —
 * but the response envelope MUST match ({@code {error, message, timestamp}}) so the Next.js client
 * can branch on status alone without caring which advice fired.
 */
@RestControllerAdvice
public class ReservationExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
    return response(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
    return response(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
    return response(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
    // Matches the VALIDATION_ERROR code emitted by the shared advice for
    // MethodArgumentTypeMismatchException etc.; clients that branch on
    // `error` see one value for any 400-class validation/input failure.
    return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
  }

  private ResponseEntity<Map<String, Object>> response(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "error",
                code,
                "message",
                Objects.requireNonNullElse(message, ""),
                "timestamp",
                Instant.now().toString()));
  }
}
