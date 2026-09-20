package com.saproduction.command.communication;

import java.util.*;

public interface MessagingProvider {
  record MessageCommand(
      UUID messageId,
      String to,
      String templateKey,
      Map<String, Object> variables,
      String bodyPreview,
      String relatedType,
      UUID relatedId) {}

  record SendResult(String providerMessageId) {}

  SendResult send(MessageCommand command);

  class ProviderException extends RuntimeException {
    private final boolean transientFailure;

    public ProviderException(String message, boolean transientFailure) {
      super(message);
      this.transientFailure = transientFailure;
    }

    public boolean transientFailure() {
      return transientFailure;
    }
  }
}
