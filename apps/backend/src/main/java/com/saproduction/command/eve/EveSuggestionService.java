package com.saproduction.command.eve;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.shared.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for querying, dismissing, and resolving owner-visible EVE suggestions.
 */
@Service
public class EveSuggestionService {

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final EveObserverService observerService;

  public EveSuggestionService(
      JdbcTemplate jdbc,
      ObjectMapper json,
      EveObserverService observerService) {
    this.jdbc = jdbc;
    this.json = json;
    this.observerService = observerService;
  }

  @Transactional(readOnly = true)
  public List<EveDtos.SuggestionView> listSuggestions(String status, String targetDomain, String priority) {
    StringBuilder sql = new StringBuilder(
        "SELECT id, type, status, priority, title, summary, source_signal_id, target_domain, "
            + "canonical_entity_type, canonical_entity_id, canonical_entity_name, evidence, "
            + "dedupe_key, created_at, updated_at, expires_at, dismissed_at, resolved_at, dismissed_by, metadata "
            + "FROM eve_suggestions WHERE 1=1 ");
    List<Object> params = new ArrayList<>();

    if (status != null && !status.isBlank()) {
      sql.append("AND status = ? ");
      params.add(status.trim().toUpperCase(Locale.ROOT));
    } else {
      sql.append("AND status = 'ACTIVE' ");
    }

    if (targetDomain != null && !targetDomain.isBlank()) {
      sql.append("AND target_domain = ? ");
      params.add(targetDomain.trim().toUpperCase(Locale.ROOT));
    }

    if (priority != null && !priority.isBlank()) {
      sql.append("AND priority = ? ");
      params.add(priority.trim().toUpperCase(Locale.ROOT));
    }

    sql.append("ORDER BY CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END, created_at DESC");

    return jdbc.query(sql.toString(), this::mapSuggestionRow, params.toArray());
  }

  @Transactional(readOnly = true)
  public EveDtos.SuggestionView getSuggestion(UUID id) {
    return jdbc.query(
        "SELECT id, type, status, priority, title, summary, source_signal_id, target_domain, "
            + "canonical_entity_type, canonical_entity_id, canonical_entity_name, evidence, "
            + "dedupe_key, created_at, updated_at, expires_at, dismissed_at, resolved_at, dismissed_by, metadata "
            + "FROM eve_suggestions WHERE id = ?",
        this::mapSuggestionRow,
        id).stream().findFirst().orElseThrow(() -> ApiException.notFound("SUGGESTION_NOT_FOUND", "Suggestion not found: " + id));
  }

  @Transactional
  public EveDtos.SuggestionView dismissSuggestion(UUID id, String reason) {
    EveDtos.SuggestionView existing = getSuggestion(id);
    if (!"ACTIVE".equals(existing.status())) {
      return existing;
    }

    String actor = actor();
    jdbc.update(
        "UPDATE eve_suggestions SET status = 'DISMISSED', dismissed_at = now(), dismissed_by = ?, updated_at = now() "
            + "WHERE id = ?",
        actor,
        id);

    return getSuggestion(id);
  }

  @Transactional
  public EveDtos.SuggestionView resolveSuggestion(UUID id, String note) {
    EveDtos.SuggestionView existing = getSuggestion(id);
    if (!"ACTIVE".equals(existing.status())) {
      return existing;
    }

    jdbc.update(
        "UPDATE eve_suggestions SET status = 'RESOLVED', resolved_at = now(), updated_at = now() WHERE id = ?",
        id);

    return getSuggestion(id);
  }

  @Transactional
  public List<EveDtos.SuggestionView> evaluateAll() {
    observerService.evaluateAll();
    return listSuggestions("ACTIVE", null, null);
  }

  private EveDtos.SuggestionView mapSuggestionRow(ResultSet rs, int rowNum) throws SQLException {
    UUID id = rs.getObject("id", UUID.class);
    String type = rs.getString("type");
    String status = rs.getString("status");
    String priority = rs.getString("priority");
    String title = rs.getString("title");
    String summary = rs.getString("summary");
    UUID sourceSignalId = rs.getObject("source_signal_id", UUID.class);
    String targetDomain = rs.getString("target_domain");
    String canonicalEntityType = rs.getString("canonical_entity_type");
    UUID canonicalEntityId = rs.getObject("canonical_entity_id", UUID.class);
    String canonicalEntityName = rs.getString("canonical_entity_name");
    String evidenceRaw = rs.getString("evidence");
    String dedupeKey = rs.getString("dedupe_key");
    Instant createdAt = rs.getTimestamp("created_at").toInstant();
    Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
    Timestamp expiresTs = rs.getTimestamp("expires_at");
    Instant expiresAt = expiresTs != null ? expiresTs.toInstant() : null;
    Timestamp dismissedTs = rs.getTimestamp("dismissed_at");
    Instant dismissedAt = dismissedTs != null ? dismissedTs.toInstant() : null;
    Timestamp resolvedTs = rs.getTimestamp("resolved_at");
    Instant resolvedAt = resolvedTs != null ? resolvedTs.toInstant() : null;
    String dismissedBy = rs.getString("dismissed_by");
    String metadataRaw = rs.getString("metadata");

    List<EveDtos.SuggestionEvidenceItem> evidence = parseEvidence(evidenceRaw);
    Map<String, Object> metadata = parseMetadata(metadataRaw);

    return new EveDtos.SuggestionView(
        id, type, status, priority, title, summary, sourceSignalId,
        targetDomain, canonicalEntityType, canonicalEntityId, canonicalEntityName,
        evidence, dedupeKey, createdAt, updatedAt, expiresAt, dismissedAt, resolvedAt, dismissedBy, metadata);
  }

  private List<EveDtos.SuggestionEvidenceItem> parseEvidence(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    try {
      return json.readValue(raw, new TypeReference<List<EveDtos.SuggestionEvidenceItem>>() {});
    } catch (Exception e) {
      return List.of();
    }
  }

  private Map<String, Object> parseMetadata(String raw) {
    if (raw == null || raw.isBlank()) return Map.of();
    try {
      return json.readValue(raw, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      return Map.of();
    }
  }

  private static String actor() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth == null || auth.getName() == null ? "system" : auth.getName();
  }
}
