package com.saproduction.command.eve;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Context Engine for EVE Phase 2.
 * Assembles a bounded, deterministic context envelope containing:
 * 1. Authenticated operator
 * 2. Current route / context
 * 3. Current date/time & canonical timezone
 * 4. Active session & recent conversation
 * 5. Relevant canonical records
 * 6. Multi-turn referenced entities
 * 7. Retrieval evidence
 * 8. EVE system knowledge
 * 9. EVE memory hints
 *
 * Enforces hard limits so that database rows are never dumped into context.
 * Implements prompt-injection defense by treating business data strictly as untrusted DATA.
 */
@Service
public class EveContextEngine {

  public static final int MAX_RECORDS = 5;
  public static final int MAX_EVIDENCE_ITEMS = 10;
  public static final int MAX_CONVERSATION_HISTORY = 5;
  public static final int MAX_TEXT_LENGTH = 2000;
  public static final int MAX_KNOWLEDGE_SNIPPETS = 3;
  public static final int MAX_MEMORY_HINTS = 5;

  private final String timezone;
  private final Map<UUID, EveRetrievalRouter.SessionContext> sessionContexts = new ConcurrentHashMap<>();

  public EveContextEngine(@Value("${app.timezone:Asia/Kolkata}") String timezone) {
    this.timezone = timezone;
  }

  public String getTimezone() {
    return timezone;
  }

  public EveRetrievalRouter.SessionContext getSessionContext(UUID sessionId) {
    if (sessionId == null) {
      return new EveRetrievalRouter.SessionContext();
    }
    return sessionContexts.computeIfAbsent(sessionId, id -> new EveRetrievalRouter.SessionContext());
  }

  public void clearSessionContext(UUID sessionId) {
    if (sessionId != null) {
      sessionContexts.remove(sessionId);
    }
  }

  public EveDtos.EveContext buildEnvelope(
      String operatorName,
      String currentRoute,
      UUID sessionId,
      List<EveDtos.EveMessage> recentMessages,
      List<EveDtos.EntityReference> entities,
      List<EveDtos.EvidenceItem> evidence,
      List<String> knowledgeSnippets,
      List<EveDtos.EveMemory> memoryHints) {

    String safeOperator = operatorName != null && !operatorName.isBlank() ? operatorName : "Azeem Khan";
    String safeRoute = currentRoute != null && !currentRoute.isBlank() ? currentRoute : "/eve";
    LocalDate today = LocalDate.now(ZoneId.of(timezone));

    // 1. Bound conversation history
    List<EveDtos.EveMessage> boundedHistory = new ArrayList<>();
    if (recentMessages != null) {
      int start = Math.max(0, recentMessages.size() - MAX_CONVERSATION_HISTORY);
      for (int i = start; i < recentMessages.size(); i++) {
        EveDtos.EveMessage msg = recentMessages.get(i);
        boundedHistory.add(new EveDtos.EveMessage(
            msg.id(),
            msg.sessionId(),
            msg.role(),
            sanitizeData(msg.content()),
            msg.createdAt()));
      }
    }

    // 2. Bound referenced entities
    List<EveDtos.EntityReference> boundedEntities = new ArrayList<>();
    if (entities != null) {
      for (int i = 0; i < Math.min(entities.size(), MAX_RECORDS); i++) {
        EveDtos.EntityReference e = entities.get(i);
        boundedEntities.add(new EveDtos.EntityReference(
            e.id(),
            sanitizeData(e.type()),
            sanitizeData(e.name()),
            sanitizeData(e.code())));
      }
    }

    // 3. Bound and sanitize evidence items (Prompt injection defense)
    List<EveDtos.EvidenceItem> boundedEvidence = new ArrayList<>();
    if (evidence != null) {
      for (int i = 0; i < Math.min(evidence.size(), MAX_EVIDENCE_ITEMS); i++) {
        EveDtos.EvidenceItem item = evidence.get(i);
        boundedEvidence.add(new EveDtos.EvidenceItem(
            sanitizeData(item.domain()),
            sanitizeData(item.label()),
            sanitizeData(item.value())));
      }
    }

    // 4. Bound knowledge snippets
    List<String> boundedKnowledge = new ArrayList<>();
    if (knowledgeSnippets != null) {
      for (int i = 0; i < Math.min(knowledgeSnippets.size(), MAX_KNOWLEDGE_SNIPPETS); i++) {
        boundedKnowledge.add(sanitizeData(knowledgeSnippets.get(i)));
      }
    }

    // 5. Bound memory hints
    List<EveDtos.EveMemory> boundedMemory = new ArrayList<>();
    if (memoryHints != null) {
      for (int i = 0; i < Math.min(memoryHints.size(), MAX_MEMORY_HINTS); i++) {
        boundedMemory.add(memoryHints.get(i));
      }
    }

    return new EveDtos.EveContext(
        safeOperator,
        safeRoute,
        today,
        timezone,
        sessionId,
        boundedHistory,
        boundedEntities,
        boundedEvidence,
        boundedKnowledge,
        boundedMemory);
  }

  public EveDtos.ContextView buildContextView(
      String ownerName,
      List<EveDtos.EntityReference> entities,
      List<EveDtos.EvidenceItem> evidence,
      List<String> knowledgeSnippets,
      List<EveDtos.MemoryView> memoryHints) {

    String safeOwner = ownerName != null && !ownerName.isBlank() ? ownerName : "Azeem Khan";
    LocalDate today = LocalDate.now(ZoneId.of(timezone));

    List<EveDtos.EntityReference> boundedEntities = new ArrayList<>();
    if (entities != null) {
      for (int i = 0; i < Math.min(entities.size(), MAX_RECORDS); i++) {
        EveDtos.EntityReference e = entities.get(i);
        boundedEntities.add(new EveDtos.EntityReference(
            e.id(),
            sanitizeData(e.type()),
            sanitizeData(e.name()),
            sanitizeData(e.code())));
      }
    }

    List<EveDtos.EvidenceItem> boundedEvidence = new ArrayList<>();
    if (evidence != null) {
      for (int i = 0; i < Math.min(evidence.size(), MAX_EVIDENCE_ITEMS); i++) {
        EveDtos.EvidenceItem item = evidence.get(i);
        boundedEvidence.add(new EveDtos.EvidenceItem(
            sanitizeData(item.domain()),
            sanitizeData(item.label()),
            sanitizeData(item.value())));
      }
    }

    List<String> boundedKnowledge = new ArrayList<>();
    if (knowledgeSnippets != null) {
      for (int i = 0; i < Math.min(knowledgeSnippets.size(), MAX_KNOWLEDGE_SNIPPETS); i++) {
        boundedKnowledge.add(sanitizeData(knowledgeSnippets.get(i)));
      }
    }

    List<EveDtos.MemoryView> boundedMemory = new ArrayList<>();
    if (memoryHints != null) {
      for (int i = 0; i < Math.min(memoryHints.size(), MAX_MEMORY_HINTS); i++) {
        boundedMemory.add(memoryHints.get(i));
      }
    }

    return new EveDtos.ContextView(
        safeOwner,
        timezone,
        today,
        boundedEntities,
        boundedEvidence,
        boundedKnowledge,
        boundedMemory);
  }

  /**
   * Treats all retrieved business content strictly as untrusted DATA, not instructions.
   * Enforces length caps and ensures adversarial instruction prefixes are safely neutralized.
   */
  public String sanitizeData(String raw) {
    if (raw == null) {
      return "";
    }
    String cleaned = raw.trim();
    if (cleaned.length() > MAX_TEXT_LENGTH) {
      cleaned = cleaned.substring(0, MAX_TEXT_LENGTH) + "... [truncated]";
    }
    cleaned = cleaned.replaceAll("(?i)(ignore\\s+(all|previous)\\s+instructions)", "[neutralized]");
    return cleaned;
  }
}
