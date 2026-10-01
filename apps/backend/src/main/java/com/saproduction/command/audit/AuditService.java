package com.saproduction.command.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuditService {
  private final AuditRepository repository;
  private final ObjectMapper json;
  private final ApplicationEventPublisher events;

  public AuditService(AuditRepository repository, ObjectMapper json) {
    this(repository, json, null);
  }

  @Autowired
  public AuditService(
      AuditRepository repository,
      ObjectMapper json,
      @Nullable ApplicationEventPublisher events) {
    this.repository = repository;
    this.json = json;
    this.events = events;
  }

  public void record(
      String entityType, String action, String entityId, Object before, Object after) {
    AuditLog log = new AuditLog();
    log.actorType = "OWNER";
    var auth = SecurityContextHolder.getContext().getAuthentication();
    log.actorId = auth == null ? "system" : auth.getName();
    log.entityType = entityType;
    log.entityId = entityId;
    log.action = action;
    log.beforeJson = write(before);
    log.afterJson = write(after);
    repository.save(log);

    if (events != null) {
      events.publishEvent(
          new DomainMutationEvent(
              entityType,
              action,
              entityId,
              log.actorId,
              before,
              after,
              Instant.now()));
    }
  }

  private String write(Object value) {
    if (value == null) return null;
    try {
      return json.writeValueAsString(value);
    } catch (Exception ignored) {
      return "{}";
    }
  }
}
