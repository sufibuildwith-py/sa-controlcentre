package com.saproduction.command.eve;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.saproduction.command.eve.cognitive.EveReasoningStep;

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
      int version,
      String status,
      List<EvePlanAction> actions) {
    public EvePlan(
        UUID planId,
        UUID sessionId,
        String intent,
        String summary,
        RiskTier riskTier,
        boolean confirmationRequired,
        String planHash,
        List<EvePlanAction> actions) {
      this(planId, sessionId, intent, summary, riskTier, confirmationRequired, planHash, 1, "PROPOSED", actions);
    }
  }

  public record EvePlanAction(
      UUID actionId,
      int seq,
      String domain,
      String commandType,
      UUID targetEntityId,
      String targetEntityName,
      Map<String, Object> parameters,
      String estimatedEffect,
      String requiredPermission,
      String status,
      UUID canonicalRecordId,
      Map<String, Object> executionResult,
      EveVerificationResult verificationResult) {
    public EvePlanAction(
        UUID actionId,
        int seq,
        String domain,
        String commandType,
        UUID targetEntityId,
        String targetEntityName,
        Map<String, Object> parameters,
        String estimatedEffect,
        String requiredPermission) {
      this(
          actionId,
          seq,
          domain,
          commandType,
          targetEntityId,
          targetEntityName,
          parameters,
          estimatedEffect,
          requiredPermission,
          "PENDING",
          null,
          null,
          null);
    }
  }

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
      List<CandidateView> candidates,
      EvePlan plan,
      List<EveReasoningStep> reasoning) {
    public QueryResponse(
        UUID sessionId,
        MessageView message,
        List<TraceEventView> trace,
        ContextView context,
        String status,
        List<CandidateView> candidates) {
      this(sessionId, message, trace, context, status, candidates, null, List.of());
    }

    public QueryResponse(
        UUID sessionId,
        MessageView message,
        List<TraceEventView> trace,
        ContextView context,
        String status,
        List<CandidateView> candidates,
        EvePlan plan) {
      this(sessionId, message, trace, context, status, candidates, plan, List.of());
    }
  }

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

  public record ConfirmPlanRequest(
      @NotNull UUID sessionId,
      @NotNull UUID planId,
      int planVersion,
      @NotBlank String planHash,
      String note) {}

  public record CancelPlanRequest(
      @NotNull UUID sessionId,
      @NotNull UUID planId,
      String reason) {}

  public record PlanExecutionResponse(
      UUID planId,
      UUID sessionId,
      String status,
      String summary,
      List<TraceEventView> trace,
      List<EvePlanAction> actions,
      MessageView message) {}

  // -------------------------------------------------------------
  // Phase 4 Continuous Intelligence & Proactive Suggestions
  // -------------------------------------------------------------

  public record SignalView(
      UUID id,
      String signalType,
      String sourceDomain,
      String canonicalEntityType,
      UUID canonicalEntityId,
      Integer canonicalVersion,
      String actorId,
      String correlationId,
      Map<String, Object> metadata,
      String status,
      Instant occurredAt,
      Instant processedAt,
      String failureReason) {}

  public record EmitSignalRequest(
      @NotBlank(message = "Signal type is required") String signalType,
      @NotBlank(message = "Source domain is required") String sourceDomain,
      @NotBlank(message = "Canonical entity type is required") String canonicalEntityType,
      @NotNull(message = "Canonical entity ID is required") UUID canonicalEntityId,
      Integer canonicalVersion,
      String correlationId,
      Map<String, Object> metadata) {}

  public record SuggestionEvidenceItem(
      String domain,
      String entityType,
      UUID entityId,
      String label,
      String value,
      Instant observedAt) {}

  public record SuggestionView(
      UUID id,
      String type,
      String status,
      String priority,
      String title,
      String summary,
      UUID sourceSignalId,
      String targetDomain,
      String canonicalEntityType,
      UUID canonicalEntityId,
      String canonicalEntityName,
      List<SuggestionEvidenceItem> evidence,
      String dedupeKey,
      Instant createdAt,
      Instant updatedAt,
      Instant expiresAt,
      Instant dismissedAt,
      Instant resolvedAt,
      String dismissedBy,
      Map<String, Object> metadata) {}

  public record DismissSuggestionRequest(String reason) {}

  public record ResolveSuggestionRequest(String note) {}

  public record EveStatusView(
      String modelProvider,
      String status,
      String modelName,
      String modelVersion,
      String details) {}
}
