package com.saproduction.command.shared;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  public final String code;
  public final HttpStatus status;
  public ApiException(HttpStatus status, String code, String message){ super(message); this.status=status; this.code=code; }
  public static ApiException notFound(String code, String message){ return new ApiException(HttpStatus.NOT_FOUND, code, message); }
  public static ApiException conflict(String code, String message){ return new ApiException(HttpStatus.CONFLICT, code, message); }
  public static ApiException badRequest(String code, String message){ return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
}

