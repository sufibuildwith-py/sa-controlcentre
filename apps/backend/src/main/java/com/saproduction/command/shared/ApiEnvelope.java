package com.saproduction.command.shared;

import java.util.Map;

public record ApiEnvelope<T>(T data, Map<String, Object> meta) {
  public static <T> ApiEnvelope<T> of(T data) {
    return new ApiEnvelope<>(data, Map.of());
  }
}
