package com.asoviewclone.common.error;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
    return buildResponse(HttpStatus.NOT_FOUND, ex);
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
    return buildResponse(HttpStatus.FORBIDDEN, ex);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
    return buildResponse(HttpStatus.CONFLICT, ex);
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(ValidationException ex) {
    return buildResponse(HttpStatus.BAD_REQUEST, ex);
  }

  private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, DomainException ex) {
    log.warn("{}: {}", ex.getErrorCode(), ex.getMessage());
    Map<String, Object> body =
        Map.of(
            "error", ex.getErrorCode(),
            "message", ex.getMessage(),
            "timestamp", Instant.now().toString());
    return ResponseEntity.status(status).body(body);
  }
}
