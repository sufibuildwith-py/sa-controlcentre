package com.saproduction.command.communication;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MessageStatusMachineTest {
  private final MessageStatusMachine machine = new MessageStatusMachine();

  @Test
  void readIgnoresStaleDeliveredAndSent() {
    OutboundMessage message = message(OutboundMessage.Status.READ);
    assertThat(machine.apply(message, OutboundMessage.Status.DELIVERED, null, Instant.now()))
        .isFalse();
    assertThat(machine.apply(message, OutboundMessage.Status.SENT, null, Instant.now())).isFalse();
    assertThat(message.status).isEqualTo(OutboundMessage.Status.READ);
  }

  @Test
  void deliveredIgnoresStaleSentAndLateFailure() {
    OutboundMessage message = message(OutboundMessage.Status.DELIVERED);
    assertThat(machine.apply(message, OutboundMessage.Status.SENT, null, Instant.now())).isFalse();
    assertThat(machine.apply(message, OutboundMessage.Status.FAILED, "late", Instant.now()))
        .isFalse();
    assertThat(message.status).isEqualTo(OutboundMessage.Status.DELIVERED);
  }

  @Test
  void sentProgressesToDeliveredAndRead() {
    OutboundMessage message = message(OutboundMessage.Status.SENT);
    assertThat(machine.apply(message, OutboundMessage.Status.DELIVERED, null, Instant.now()))
        .isTrue();
    assertThat(machine.apply(message, OutboundMessage.Status.READ, null, Instant.now())).isTrue();
    assertThat(message.status).isEqualTo(OutboundMessage.Status.READ);
  }

  private OutboundMessage message(OutboundMessage.Status status) {
    OutboundMessage message = new OutboundMessage();
    message.status = status;
    return message;
  }
}
