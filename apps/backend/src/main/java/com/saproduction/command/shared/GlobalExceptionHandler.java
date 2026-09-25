package com.saproduction.command.shared;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  record ErrorBody(String code, String message, String traceId, Map<String, String> fields) {}

  record ErrorEnvelope(ErrorBody error) {}

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorEnvelope> api(ApiException ex) {
    return ResponseEntity.status(ex.status).body(body(ex.code, ex.getMessage(), ex.fields));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorEnvelope> validation(MethodArgumentNotValidException ex) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors())
      fields.putIfAbsent(error.getField(), error.getDefaultMessage());
    return ResponseEntity.badRequest()
        .body(body("VALIDATION_FAILED", "Please review the highlighted fields.", fields));
  }

  @ExceptionHandler(BadCredentialsException.class)
  ResponseEntity<ErrorEnvelope> credentials() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(body("INVALID_CREDENTIALS", "Email or password is incorrect.", Map.of()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorEnvelope> malformedRequest() {
    return ResponseEntity.badRequest()
        .body(body("INVALID_REQUEST_BODY", "Please review the request fields and formats.", Map.of()));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorEnvelope> fallback(Exception ex) {
    log.error("Unhandled request failure", ex);
    return ResponseEntity.internalServerError()
        .body(body("INTERNAL_ERROR", "Something went wrong. Please try again.", Map.of()));
  }

  private ErrorEnvelope body(String code, String message, Map<String, String> fields) {
    return new ErrorEnvelope(new ErrorBody(code, message, MDC.get("traceId"), fields));
  }
}
