package com.saproduction.command.shared;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  public final String code;
  public final HttpStatus status;
  public final Map<String, String> fields;

  public ApiException(HttpStatus status, String code, String message) {
    this(status, code, message, Map.of());
  }

  public ApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields;
  }

  public static ApiException conflict(String code, String message, Map<String, String> fields) {
    return new ApiException(HttpStatus.CONFLICT, code, message, fields);
  }

  public static ApiException notFound(String code, String message) {
    return new ApiException(HttpStatus.NOT_FOUND, code, message);
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }

  public static ApiException badRequest(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }
}
