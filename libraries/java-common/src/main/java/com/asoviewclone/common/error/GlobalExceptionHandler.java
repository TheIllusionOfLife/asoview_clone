package com.asoviewclone.common.error;

import java.time.Instant;
import java.util.Map;
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
    return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION", message);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
    return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION", message);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Map<String, Object>> handleMissingParam(
      MissingServletRequestParameterException ex) {
    return buildResponse(
        HttpStatus.BAD_REQUEST,
        "VALIDATION",
        "Missing required parameter '" + ex.getParameterName() + "'");
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<Map<String, Object>> handleMissingPart(
      MissingServletRequestPartException ex) {
    return buildResponse(
        HttpStatus.BAD_REQUEST,
        "VALIDATION",
        "Missing required multipart part '" + ex.getRequestPartName() + "'");
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
    return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION", "Malformed request body");
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
    Map<String, Object> body =
        Map.of("error", errorCode, "message", message, "timestamp", Instant.now().toString());
    return ResponseEntity.status(status).body(body);
  }
}
