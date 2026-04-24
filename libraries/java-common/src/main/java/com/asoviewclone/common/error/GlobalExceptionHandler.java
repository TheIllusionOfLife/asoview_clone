package com.asoviewclone.common.error;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
    return buildDomainResponse(HttpStatus.NOT_FOUND, ex);
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
    return buildDomainResponse(HttpStatus.FORBIDDEN, ex);
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
    return buildDomainResponse(HttpStatus.CONFLICT, ex);
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(ValidationException ex) {
    return buildDomainResponse(HttpStatus.BAD_REQUEST, ex);
  }

  // ---- Framework exceptions that MUST be handled explicitly ----
  //
  // If these fall through to DefaultHandlerExceptionResolver, Spring MVC
  // resolves them to the correct HTTP status but the servlet container
  // then re-dispatches to /error with DispatcherType.ERROR. That dispatch
  // re-enters the SecurityFilterChain; on services whose chain ends with
  // .anyRequest().authenticated(), the unauthenticated re-dispatch turns
  // the original 4xx into an empty-body 403. Handling them here short-
  // circuits the re-dispatch entirely: we write the response body from
  // the advice and the container has nothing left to render via /error.
  //
  // The SecurityFilterChain also permits DispatcherType.ERROR as a second
  // line of defence (see SecurityConfig.java). This advice gives the
  // client a JSON body with the same shape as domain errors so the
  // frontend can branch on `error` / `message` uniformly.

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(
      MethodArgumentTypeMismatchException ex) {
    String required = ex.getRequiredType() == null ? "?" : ex.getRequiredType().getSimpleName();
    String message =
        "Parameter '"
            + ex.getName()
            + "' has invalid value '"
            + ex.getValue()
            + "'; expected "
            + required;
    return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex) {
    // getAllErrors() includes class-level cross-field errors (global errors)
    // that getFieldErrors() silently drops, e.g. @AssertTrue on a record.
    String message =
        ex.getBindingResult().getAllErrors().stream()
            .map(
                err -> {
                  String field =
                      err instanceof org.springframework.validation.FieldError fe
                          ? fe.getField()
                          : err.getObjectName();
                  return field + ": " + err.getDefaultMessage();
                })
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
    return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Map<String, Object>> handleConstraintViolation(
      ConstraintViolationException ex) {
    // Thrown when @Validated sits at the controller-class level and a method
    // parameter fails a jakarta.validation constraint (@NotBlank on @PathVariable,
    // @Min on @RequestParam, etc.). Different from MethodArgumentNotValidException,
    // which is for @Valid on @RequestBody DTOs. Handle explicitly so it doesn't
    // fall through to DefaultHandlerExceptionResolver and trip the ERROR
    // re-dispatch path.
    String message =
        ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Constraint violation");
    return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Map<String, Object>> handleMissingParam(
      MissingServletRequestParameterException ex) {
    return buildResponse(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "Missing required parameter '" + ex.getParameterName() + "'");
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<Map<String, Object>> handleMissingPart(
      MissingServletRequestPartException ex) {
    return buildResponse(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "Missing required multipart part '" + ex.getRequestPartName() + "'");
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
    return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Malformed request body");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex) {
    return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex) {
    return buildResponse(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", ex.getMessage());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
    // Spring Boot 3.2+ throws this for any unmatched request path instead
    // of rendering the static-resource handler's 404. Translating it here
    // keeps the JSON body consistent with NotFoundException and prevents
    // the default /error view from rendering in content-negotiated form.
    return buildResponse(
        HttpStatus.NOT_FOUND, "NOT_FOUND", "No handler for " + ex.getResourcePath());
  }

  private ResponseEntity<Map<String, Object>> buildDomainResponse(
      HttpStatus status, DomainException ex) {
    log.warn("{}: {}", ex.getErrorCode(), ex.getMessage());
    return buildResponse(status, ex.getErrorCode(), ex.getMessage());
  }

  private ResponseEntity<Map<String, Object>> buildResponse(
      HttpStatus status, String errorCode, String message) {
    // Every 4xx that goes through this advice gets a debug-level breadcrumb
    // so operators can correlate "client saw X" with server-side state.
    // Warn is reserved for domain exceptions (see buildDomainResponse) and
    // 5xx paths handled elsewhere; framework 400/404/405/415/etc. are
    // client errors and do not warrant log noise at info level.
    if (log.isDebugEnabled()) {
      log.debug("{} ({}): {}", errorCode, status.value(), message);
    }
    // Map.of throws NullPointerException on null values. DomainException
    // subclasses can construct with a null message (and framework
    // exceptions occasionally emit null getMessage()), so coerce before
    // handing to the immutable factory. HashMap-wrapped unmodifiableMap
    // keeps the same serialization shape as Map.of.
    Map<String, Object> body = new HashMap<>(3);
    body.put("error", Objects.requireNonNullElse(errorCode, "ERROR"));
    body.put("message", Objects.requireNonNullElse(message, ""));
    body.put("timestamp", Instant.now().toString());
    return ResponseEntity.status(status).body(Map.copyOf(body));
  }
}
