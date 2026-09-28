package com.saproduction.command.eve;

import com.saproduction.command.shared.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Small, explicit memory service for EVE Phase 1.
 *
 * Invariant: Memory is NOT business truth. It contains operator vocabulary,
 * aliases, and preference hints (e.g. "Raju" -> "Raj Kumar").
 * Canonical SA Command database records always supersede memory hints.
 */
@Service
public class EveMemoryService {

  private final JdbcTemplate jdbc;

  public EveMemoryService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public EveDtos.MemoryView remember(EveDtos.MemoryRequest request, String source) {
    if (request == null || request.term() == null || request.term().isBlank()) {
      throw ApiException.badRequest("EVE_INVALID_MEMORY", "Memory term cannot be blank.");
    }

    String cleanTerm = request.term().trim();
    String cleanType = request.memoryType() != null ? request.memoryType().trim().toUpperCase() : "VOCABULARY";
    String cleanCanonType = request.canonicalType() != null ? request.canonicalType().trim().toUpperCase() : "EMPLOYEE";
    String cleanSource = source != null && !source.isBlank() ? source : "OPERATOR_EXPLICIT";

    UUID id = UUID.randomUUID();
    Instant now = Instant.now();

    jdbc.update(
        """
        INSERT INTO eve_memory (id, memory_type, term, canonical_type, canonical_id, canonical_name, confidence, source, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, 1.0, ?, ?, ?)
        ON CONFLICT (memory_type, term) DO UPDATE SET
          canonical_type = EXCLUDED.canonical_type,
          canonical_id = EXCLUDED.canonical_id,
          canonical_name = EXCLUDED.canonical_name,
          confidence = EXCLUDED.confidence,
          source = EXCLUDED.source,
          updated_at = EXCLUDED.updated_at
        """,
        id,
        cleanType,
        cleanTerm,
        cleanCanonType,
        request.canonicalId(),
        request.canonicalName(),
        cleanSource,
        Timestamp.from(now),
        Timestamp.from(now));

    return recall(cleanTerm).orElseThrow();
  }

  @Transactional(readOnly = true)
  public Optional<EveDtos.MemoryView> recall(String term) {
    if (term == null || term.isBlank()) {
      return Optional.empty();
    }

    List<EveDtos.MemoryView> list = jdbc.query(
        "SELECT id, memory_type, term, canonical_type, canonical_id, canonical_name, confidence, source, created_at, updated_at FROM eve_memory WHERE lower(term) = lower(?) LIMIT 1",
        (rs, rowNum) -> new EveDtos.MemoryView(
            (UUID) rs.getObject("id"),
            rs.getString("memory_type"),
            rs.getString("term"),
            rs.getString("canonical_type"),
            (UUID) rs.getObject("canonical_id"),
            rs.getString("canonical_name"),
            rs.getDouble("confidence"),
            rs.getString("source"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()),
        term.trim());

    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  @Transactional(readOnly = true)
  public List<EveDtos.MemoryView> listMemories() {
    return jdbc.query(
        "SELECT id, memory_type, term, canonical_type, canonical_id, canonical_name, confidence, source, created_at, updated_at FROM eve_memory ORDER BY updated_at DESC LIMIT 100",
        (rs, rowNum) -> new EveDtos.MemoryView(
            (UUID) rs.getObject("id"),
            rs.getString("memory_type"),
            rs.getString("term"),
            rs.getString("canonical_type"),
            (UUID) rs.getObject("canonical_id"),
            rs.getString("canonical_name"),
            rs.getDouble("confidence"),
            rs.getString("source"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()));
  }

  @Transactional
  public boolean deleteMemory(UUID id) {
    int rows = jdbc.update("DELETE FROM eve_memory WHERE id = ?", id);
    return rows > 0;
  }
}
