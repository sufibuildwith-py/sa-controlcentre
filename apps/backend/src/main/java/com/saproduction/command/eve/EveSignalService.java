package com.saproduction.command.eve;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.shared.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists and coordinates the lifecycle of typed EVE signals.
 * Guarantees transaction-bounded signal processing and auditable observability.
 */
@Service
public class EveSignalService {

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final EveObserverService observerService;

  public EveSignalService(
      JdbcTemplate jdbc,
      ObjectMapper json,
      @Lazy EveObserverService observerService) {
    this.jdbc = jdbc;
    this.json = json;
    this.observerService = observerService;
  }

  @Transactional
  public EveDtos.SignalView emit(EveDtos.EmitSignalRequest request) {
    if (!EveSignalTypes.isValid(request.signalType())) {
      throw ApiException.badRequest("INVALID_SIGNAL_TYPE", "Signal type is not recognized in allowlist: " + request.signalType());
    }

    UUID signalId = UUID.randomUUID();
    Instant now = Instant.now();
    String metadataJson = writeJson(request.metadata() != null ? request.metadata() : Map.of());

    // Idempotency: If correlation_id provided, check if already recorded
    if (request.correlationId() != null && !request.correlationId().isBlank()) {
      var existing = jdbc.query(
          "SELECT id, signal_type, source_domain, canonical_entity_type, canonical_entity_id, "
              + "canonical_version, actor_id, correlation_id, metadata, status, occurred_at, processed_at, failure_reason "
              + "FROM eve_signals WHERE correlation_id = ? LIMIT 1",
          this::mapSignalRow,
          request.correlationId().trim());
      if (!existing.isEmpty()) {
        return existing.getFirst();
      }
    }

    jdbc.update(
        """
        INSERT INTO eve_signals (
          id, signal_type, source_domain, canonical_entity_type, canonical_entity_id,
          canonical_version, actor_id, correlation_id, metadata, status, occurred_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'RECEIVED', ?)
        """,
        signalId,
        request.signalType(),
        request.sourceDomain(),
        request.canonicalEntityType(),
        request.canonicalEntityId(),
        request.canonicalVersion(),
        "system",
        request.correlationId(),
        metadataJson,
        Timestamp.from(now));

    EveDtos.SignalView signal = new EveDtos.SignalView(
        signalId,
        request.signalType(),
        request.sourceDomain(),
        request.canonicalEntityType(),
        request.canonicalEntityId(),
        request.canonicalVersion(),
        "system",
        request.correlationId(),
        request.metadata() != null ? request.metadata() : Map.of(),
        "RECEIVED",
        now,
        null,
        null);

    // Dispatch to observer for bounded observation
    if (observerService != null) {
      try {
        observerService.observeSignal(signal);
        jdbc.update("UPDATE eve_signals SET status = 'EVALUATED', processed_at = now() WHERE id = ?", signalId);
      } catch (Exception e) {
        jdbc.update("UPDATE eve_signals SET status = 'FAILED', failure_reason = ?, processed_at = now() WHERE id = ?",
            e.getMessage(), signalId);
      }
    }

    return getSignal(signalId);
  }

  @Transactional(readOnly = true)
  public EveDtos.SignalView getSignal(UUID id) {
    return jdbc.query(
        "SELECT id, signal_type, source_domain, canonical_entity_type, canonical_entity_id, "
            + "canonical_version, actor_id, correlation_id, metadata, status, occurred_at, processed_at, failure_reason "
            + "FROM eve_signals WHERE id = ?",
        this::mapSignalRow,
        id).stream().findFirst().orElseThrow(() -> ApiException.notFound("SIGNAL_NOT_FOUND", "Signal not found: " + id));
  }

  @Transactional(readOnly = true)
  public List<EveDtos.SignalView> listRecentSignals(int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 100));
    return jdbc.query(
        "SELECT id, signal_type, source_domain, canonical_entity_type, canonical_entity_id, "
            + "canonical_version, actor_id, correlation_id, metadata, status, occurred_at, processed_at, failure_reason "
            + "FROM eve_signals ORDER BY occurred_at DESC LIMIT ?",
        this::mapSignalRow,
        safeLimit);
  }

  private EveDtos.SignalView mapSignalRow(ResultSet rs, int rowNum) throws SQLException {
    UUID id = rs.getObject("id", UUID.class);
    String signalType = rs.getString("signal_type");
    String sourceDomain = rs.getString("source_domain");
    String entityType = rs.getString("canonical_entity_type");
    UUID entityId = rs.getObject("canonical_entity_id", UUID.class);
    Integer version = (Integer) rs.getObject("canonical_version");
    String actorId = rs.getString("actor_id");
    String correlationId = rs.getString("correlation_id");
    String metadataRaw = rs.getString("metadata");
    String status = rs.getString("status");
    Instant occurredAt = rs.getTimestamp("occurred_at").toInstant();
    Timestamp processedTs = rs.getTimestamp("processed_at");
    Instant processedAt = processedTs != null ? processedTs.toInstant() : null;
    String failureReason = rs.getString("failure_reason");

    Map<String, Object> metadata = parseJson(metadataRaw);
    return new EveDtos.SignalView(
        id, signalType, sourceDomain, entityType, entityId, version,
        actorId, correlationId, metadata, status, occurredAt, processedAt, failureReason);
  }

  private String writeJson(Object obj) {
    try {
      return json.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  private Map<String, Object> parseJson(String raw) {
    if (raw == null || raw.isBlank()) return Map.of();
    try {
      return json.readValue(raw, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }
}
