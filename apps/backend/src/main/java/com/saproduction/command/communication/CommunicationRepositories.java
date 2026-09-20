package com.saproduction.command.communication;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.*;

interface OutboxEventRepository extends JpaRepository<OutboxEvent,UUID>{}
interface NotificationRuleRepository extends JpaRepository<NotificationRule,UUID>{Optional<NotificationRule> findByEventType(String eventType);List<NotificationRule> findAllByOrderByEventType();}
interface OutboundMessageRepository extends JpaRepository<OutboundMessage,UUID>,JpaSpecificationExecutor<OutboundMessage>{Optional<OutboundMessage> findByProviderMessageId(String id);Optional<OutboundMessage> findByIdempotencyKey(String key);List<OutboundMessage> findAllByEmployeeIdOrderByQueuedAtDesc(UUID employeeId);}
interface OutboundMessageEventRepository extends JpaRepository<OutboundMessageEvent,UUID>{List<OutboundMessageEvent> findAllByOutboundMessageIdOrderByCreatedAt(UUID messageId);}
