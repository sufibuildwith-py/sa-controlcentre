package com.saproduction.command.eve;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authoritative typed contracts for the EVE lifecycle.
 * Distinguishes user language, interpreted intent, canonical entity references,
 * retrieval evidence, proposed plan, finite command descriptors, verification, memory,
 * and observable application activity.
 */
public final class EveDtos {
  private EveDtos() {}

  // -------------------------------------------------------------
  // Core Lifecycle Contracts (Architecture Invariant)
  // -------------------------------------------------------------

  public record EveSession(
      UUID id,
      String title,
      String status,
      Instant createdAt,
      Instant updatedAt,
      List<EveMessage> messages) {}

  public record EveMessage(
      UUID id,
      UUID sessionId,
      String role,
      String content,
      Instant createdAt) {}

  public record EveTraceEvent(
      UUID id,
      UUID sessionId,
      UUID messageId,
      int seq,
      String eventType,
      String status,
      String label,
      String detail,
      Instant createdAt) {}

  public record EveContext(
      String operator,
      String currentRoute,
      LocalDate date,
      String timezone,
      UUID sessionId,
      List<EveMessage> recentConversation,
      List<EntityReference> referencedEntities,
      List<EvidenceItem> evidence,
      List<String> knowledgeSnippets,
      List<EveMemory> memoryHints) {}

  public record EveResolution(
      String targetType,
      String spokenValue,
      UUID canonicalId,
      String canonicalName,
      String matchMethod,
      double confidence,
      String status,
      List<CandidateView> candidates) {}

  public record EvePlan(
      UUID planId,
      UUID sessionId,
      String intent,
      String summary,
      RiskTier riskTier,
      boolean confirmationRequired,
      String planHash,
      List<EvePlanAction> actions) {}

  public record EvePlanAction(
      UUID actionId,
      int seq,
      String domain,
      String commandType,
      UUID targetEntityId,
      String targetEntityName,
      Map<String, Object> parameters,
      String estimatedEffect,
      String requiredPermission) {}

  public record EveCommand(
      String commandType,
      String domain,
      String description,
      RiskTier riskTier,
      ConfirmationPolicy confirmationPolicy,
      List<String> requiredParameters,
      boolean idempotent,
      String verificationRule) {}

  public record EveCommandResult(
      String commandType,
      String status,
      Instant executedAt,
      UUID canonicalRecordId,
      String message,
      String idempotencyKey) {}

  public record EveMemory(
      UUID id,
      String memoryType,
      String term,
      String canonicalType,
      UUID canonicalId,
      String canonicalName,
      double confidence,
      String source,
      Instant createdAt,
      Instant updatedAt) {}

  public record EveVerificationResult(
      String status,
      String ruleName,
      String expectedState,
      String actualState,
      Instant verifiedAt,
      String notes) {}

  public enum RiskTier {
    READ,
    SAFE_OPERATION,
    FINANCIAL_WRITE,
    BLOCKED
  }

  public enum ConfirmationPolicy {
    NONE,
    CONFIRMATION_REQUIRED,
    STRICT_DUAL_AUTH
  }

  // -------------------------------------------------------------
  // DTO Views for API & Frontend Integration
  // -------------------------------------------------------------

  public record SessionView(
      UUID id,
      String title,
      String status,
      Instant createdAt,
      Instant updatedAt,
      List<MessageView> messages) {}

  public record MessageView(
      UUID id,
      UUID sessionId,
      String role,
      String content,
      Instant createdAt) {}

  public record TraceEventView(
      UUID id,
      UUID sessionId,
      UUID messageId,
      int seq,
      String eventType,
      String status,
      String label,
      String detail,
      Instant createdAt) {}

  public record QueryRequest(
      @NotBlank(message = "Prompt cannot be blank") String prompt,
      UUID sessionId,
      String currentRoute) {
    public QueryRequest(String prompt, UUID sessionId) {
      this(prompt, sessionId, "/eve");
    }
  }

  public record QueryResponse(
      UUID sessionId,
      MessageView message,
      List<TraceEventView> trace,
      ContextView context,
      String status,
      List<CandidateView> candidates) {}

  public record CandidateView(
      UUID id,
      String type,
      String displayName,
      String code,
      String detail) {}

  public record ContextView(
      String owner,
      String timezone,
      LocalDate date,
      List<EntityReference> referencedEntities,
      List<EvidenceItem> evidence,
      List<String> knowledgeSnippets,
      List<MemoryView> memoryHints) {
    public ContextView(
        String owner,
        String timezone,
        LocalDate date,
        List<EntityReference> referencedEntities,
        List<EvidenceItem> evidence) {
      this(owner, timezone, date, referencedEntities, evidence, List.of(), List.of());
    }
  }

  public record EntityReference(
      UUID id,
      String type,
      String name,
      String code) {}

  public record EvidenceItem(
      String domain,
      String label,
      String value) {}

  public record CreateSessionRequest(String title) {}

  public record MemoryRequest(
      @NotBlank String memoryType,
      @NotBlank String term,
      @NotBlank String canonicalType,
      UUID canonicalId,
      String canonicalName) {}

  public record MemoryView(
      UUID id,
      String memoryType,
      String term,
      String canonicalType,
      UUID canonicalId,
      String canonicalName,
      double confidence,
      String source,
      Instant createdAt,
      Instant updatedAt) {}
}
