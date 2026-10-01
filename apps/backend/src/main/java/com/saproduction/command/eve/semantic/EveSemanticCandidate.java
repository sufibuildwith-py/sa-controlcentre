package com.saproduction.command.eve.semantic;

import com.saproduction.command.eve.EveRetrievalService;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Clean, bounded canonical candidate representation for semantic retrieval and reranking.
 *
 * Invariants:
 * - Contains only non-sensitive, resolution-relevant fields.
 * - Never includes secrets, tokens, credentials, or private financial records.
 * - Binds strictly to a canonical UUID and entity type.
 */
public record EveSemanticCandidate(
    UUID id,
    String type,
    String displayName,
    String code,
    String searchableText,
    Map<String, Object> metadata,
    Instant updatedAt) {

  public EveSemanticCandidate {
    Objects.requireNonNull(id, "Canonical candidate id cannot be null");
    Objects.requireNonNull(type, "Canonical candidate type cannot be null");
    Objects.requireNonNull(displayName, "Candidate displayName cannot be null");
    if (metadata == null) {
      metadata = Map.of();
    }
    if (updatedAt == null) {
      updatedAt = Instant.EPOCH;
    }
  }

  public static EveSemanticCandidate forProduction(
      UUID id,
      String title,
      String clientName,
      String eventDate,
      String venue,
      String status,
      Instant updatedAt) {
    StringBuilder sb = new StringBuilder();
    sb.append("Production: ").append(title != null ? title : "Untitled");
    if (clientName != null && !clientName.isBlank()) {
      sb.append(" | Client: ").append(clientName);
    }
    if (eventDate != null && !eventDate.isBlank()) {
      sb.append(" | Date: ").append(eventDate);
    }
    if (venue != null && !venue.isBlank()) {
      sb.append(" | Venue: ").append(venue);
    }
    if (status != null && !status.isBlank()) {
      sb.append(" | Status: ").append(status);
    }

    Map<String, Object> meta = new LinkedHashMap<>();
    if (clientName != null) meta.put("clientName", clientName);
    if (eventDate != null) meta.put("eventDate", eventDate);
    if (venue != null) meta.put("venue", venue);
    if (status != null) meta.put("status", status);

    return new EveSemanticCandidate(
        id,
        "PRODUCTION",
        title != null ? title : "Untitled Production",
        null,
        sb.toString(),
        Collections.unmodifiableMap(meta),
        updatedAt);
  }

  public static EveSemanticCandidate forEmployee(
      UUID id,
      String displayName,
      String firstName,
      String code,
      String role,
      String status,
      Instant updatedAt) {
    StringBuilder sb = new StringBuilder();
    sb.append("Employee: ").append(displayName);
    if (firstName != null && !firstName.isBlank() && !displayName.equalsIgnoreCase(firstName)) {
      sb.append(" | First Name: ").append(firstName);
    }
    if (code != null && !code.isBlank()) {
      sb.append(" | Code: ").append(code);
    }
    if (role != null && !role.isBlank()) {
      sb.append(" | Role: ").append(role);
    }
    if (status != null && !status.isBlank()) {
      sb.append(" | Status: ").append(status);
    }

    Map<String, Object> meta = new LinkedHashMap<>();
    if (firstName != null) meta.put("firstName", firstName);
    if (role != null) meta.put("role", role);
    if (status != null) meta.put("status", status);

    return new EveSemanticCandidate(
        id,
        "EMPLOYEE",
        displayName,
        code,
        sb.toString(),
        Collections.unmodifiableMap(meta),
        updatedAt);
  }

  public EveRetrievalService.Candidate toRetrievalCandidate() {
    String detail = metadata.containsKey("role")
        ? String.valueOf(metadata.get("role"))
        : (metadata.containsKey("status") ? String.valueOf(metadata.get("status")) : "");
    return new EveRetrievalService.Candidate(id, type, displayName, code, detail);
  }
}
