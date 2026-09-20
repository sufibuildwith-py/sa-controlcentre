package com.saproduction.command.communication;

import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {}

interface NotificationRuleRepository extends JpaRepository<NotificationRule, UUID> {
  Optional<NotificationRule> findByEventType(String eventType);

  List<NotificationRule> findAllByOrderByEventType();
}

interface CommunicationSummaryProjection {
  long getDelivered();

  long getReadCount();

  long getAwaitingResponse();

  long getFailed();

  long getQueued();

  long getSentToday();
}

interface OutboundMessageRepository
    extends JpaRepository<OutboundMessage, UUID>, JpaSpecificationExecutor<OutboundMessage> {
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from OutboundMessage m where m.id = :id")
  Optional<OutboundMessage> lockById(UUID id);

  Optional<OutboundMessage> findByProviderMessageId(String id);

  Optional<OutboundMessage> findByIdempotencyKey(String key);

  List<OutboundMessage> findAllByEmployeeIdOrderByQueuedAtDesc(UUID employeeId);

  @Query(
      value =
          "select count(*) filter (where status in ('DELIVERED','READ')) delivered,count(*) filter (where status='READ') read_count,count(*) filter (where requires_response and response is null) awaiting_response,count(*) filter (where status='FAILED') failed,count(*) filter (where status in ('QUEUED','SENDING')) queued,count(*) filter (where sent_at>=:todayStart) sent_today from outbound_messages",
      nativeQuery = true)
  CommunicationSummaryProjection summarize(Instant todayStart);
}

interface OutboundMessageEventRepository extends JpaRepository<OutboundMessageEvent, UUID> {
  List<OutboundMessageEvent> findAllByOutboundMessageIdOrderByCreatedAt(UUID messageId);
}

interface OutboundDeliveryAttemptRepository extends JpaRepository<OutboundDeliveryAttempt, UUID> {
  Optional<OutboundDeliveryAttempt> findByProviderMessageId(String id);

  List<OutboundDeliveryAttempt> findAllByOutboundMessageIdOrderByAttemptNumber(UUID messageId);
}
