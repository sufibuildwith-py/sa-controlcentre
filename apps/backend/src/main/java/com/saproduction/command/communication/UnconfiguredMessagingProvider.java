package com.saproduction.command.communication;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.provider", havingValue = "unconfigured")
public class UnconfiguredMessagingProvider implements MessagingProvider {
  public SendResult send(MessageCommand command) {
    throw new ProviderException("WhatsApp is not configured.", false);
  }
}
