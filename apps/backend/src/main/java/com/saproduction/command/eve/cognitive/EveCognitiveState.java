package com.saproduction.command.eve.cognitive;

import com.saproduction.command.eve.EveDtos;
import com.saproduction.command.eve.EveRetrievalRouter;
import com.saproduction.command.eve.EveRetrievalService;
import java.time.Instant;
import java.util.*;

/**
 * Strongly-typed Conversational & Cognitive State for EVE 2.0.
 * Preserves context continuity, tracks active entities, and isolates orthogonal domains.
 */
public class EveCognitiveState {

  private String currentSubject;
  private String currentDomain;
  private EveOperation currentOperation;
  private EveRetrievalService.Candidate activeProduction;
  private EveRetrievalService.Candidate activeEmployee;
  private EveRetrievalService.Candidate activeEquipment;
  private EveCandidateSet activeCandidateSet = EveCandidateSet.empty();
  private final Map<String, Object> activeConstraints = new HashMap<>();
  private final List<EveDtos.EvidenceItem> recentEvidence = new ArrayList<>();
  private final List<String> unresolvedReferences = new ArrayList<>();
  private EveRetrievalRouter.SessionContext.PendingClarification pendingClarification;
  private double confidence = 1.0;
  private String lastCapability;
  private Object lastCapabilityResult;
  private Instant lastUpdatedAt = Instant.now();

  public EveCognitiveState() {}

  public synchronized String getCurrentSubject() {
    return currentSubject;
  }

  public synchronized void setCurrentSubject(String currentSubject) {
    this.currentSubject = currentSubject;
    this.lastUpdatedAt = Instant.now();
  }

  public synchronized String getCurrentDomain() {
    return currentDomain;
  }

  public synchronized void setCurrentDomain(String currentDomain) {
    this.currentDomain = currentDomain;
    this.lastUpdatedAt = Instant.now();
  }

  public synchronized EveOperation getCurrentOperation() {
    return currentOperation;
  }

  public synchronized void setCurrentOperation(EveOperation currentOperation) {
    this.currentOperation = currentOperation;
    this.lastUpdatedAt = Instant.now();
  }

  public synchronized EveRetrievalService.Candidate getActiveProduction() {
    return activeProduction;
  }

  public synchronized void setActiveProduction(EveRetrievalService.Candidate activeProduction) {
    this.activeProduction = activeProduction;
    if (activeProduction != null) {
      this.currentSubject = activeProduction.displayName();
    }
    this.lastUpdatedAt = Instant.now();
  }

  public synchronized EveRetrievalService.Candidate getActiveEmployee() {
    return activeEmployee;
  }

  public synchronized void setActiveEmployee(EveRetrievalService.Candidate activeEmployee) {
    this.activeEmployee = activeEmployee;
    if (activeEmployee != null) {
      this.currentSubject = activeEmployee.displayName();
    }
    this.lastUpdatedAt = Instant.now();
  }

  public synchronized EveRetrievalService.Candidate getActiveEquipment() {
    return activeEquipment;
  }

  public synchronized void setActiveEquipment(EveRetrievalService.Candidate activeEquipment) {
    this.activeEquipment = activeEquipment;
    this.lastUpdatedAt = Instant.now();
  }

  public synchronized EveCandidateSet getActiveCandidateSet() {
    return activeCandidateSet;
  }

  public synchronized void setActiveCandidateSet(EveCandidateSet set) {
    this.activeCandidateSet = set != null ? set : EveCandidateSet.empty();
    this.lastUpdatedAt = Instant.now();
  }

  public synchronized void clearCandidateSet() {
    this.activeCandidateSet = EveCandidateSet.empty();
    this.lastUpdatedAt = Instant.now();
  }

  public synchronized boolean hasActiveCandidates() {
    return activeCandidateSet != null && !activeCandidateSet.isEmpty();
  }

  public synchronized Map<String, Object> getActiveConstraints() {
    return Collections.unmodifiableMap(activeConstraints);
  }

  public synchronized void setConstraints(Map<String, Object> constraints) {
    this.activeConstraints.clear();
    if (constraints != null) {
      this.activeConstraints.putAll(constraints);
    }
    this.lastUpdatedAt = Instant.now();
  }

  public synchronized List<EveDtos.EvidenceItem> getRecentEvidence() {
    return Collections.unmodifiableList(recentEvidence);
  }

  public synchronized void setRecentEvidence(List<EveDtos.EvidenceItem> evidence) {
    this.recentEvidence.clear();
    if (evidence != null) {
      this.recentEvidence.addAll(evidence);
    }
    this.lastUpdatedAt = Instant.now();
  }

  public synchronized EveRetrievalRouter.SessionContext.PendingClarification getPendingClarification() {
    return pendingClarification;
  }

  public synchronized void setPendingClarification(EveRetrievalRouter.SessionContext.PendingClarification pendingClarification) {
    this.pendingClarification = pendingClarification;
    this.lastUpdatedAt = Instant.now();
  }

  public synchronized void clearPendingClarification() {
    this.pendingClarification = null;
    this.lastUpdatedAt = Instant.now();
  }

  public synchronized boolean hasPendingClarification() {
    return pendingClarification != null;
  }

  public synchronized double getConfidence() {
    return confidence;
  }

  public synchronized void setConfidence(double confidence) {
    this.confidence = confidence;
  }

  public synchronized String getLastCapability() {
    return lastCapability;
  }

  public synchronized void setLastCapability(String lastCapability) {
    this.lastCapability = lastCapability;
  }

  public synchronized Object getLastCapabilityResult() {
    return lastCapabilityResult;
  }

  public synchronized void setLastCapabilityResult(Object lastCapabilityResult) {
    this.lastCapabilityResult = lastCapabilityResult;
  }

  public synchronized Instant getLastUpdatedAt() {
    return lastUpdatedAt;
  }
}
