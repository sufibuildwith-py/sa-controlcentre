package com.saproduction.command.eve;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Deterministic cryptographic plan hasher for EVE Phase 3 Governed Execution.
 * Guarantees that confirmation tokens strictly bind to exact parameters, entities, risk tiers,
 * and sequence order. Any parameter manipulation invalidates the hash.
 */
public final class EvePlanHasher {

  private EvePlanHasher() {}

  public static String calculateHash(
      UUID planId,
      int version,
      String intent,
      EveDtos.RiskTier riskTier,
      List<EveDtos.EvePlanAction> actions) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      StringBuilder sb = new StringBuilder();
      sb.append("planId=").append(planId != null ? planId.toString() : "").append(";");
      sb.append("version=").append(version).append(";");
      sb.append("intent=").append(intent != null ? intent : "").append(";");
      sb.append("riskTier=").append(riskTier != null ? riskTier.name() : "").append(";");

      List<EveDtos.EvePlanAction> sortedActions = actions != null
          ? new ArrayList<>(actions)
          : Collections.emptyList();
      sortedActions.sort(Comparator.comparingInt(EveDtos.EvePlanAction::seq));

      sb.append("actionsCount=").append(sortedActions.size()).append(";");
      for (EveDtos.EvePlanAction action : sortedActions) {
        sb.append("[seq=").append(action.seq()).append(",");
        sb.append("domain=").append(action.domain()).append(",");
        sb.append("cmd=").append(action.commandType()).append(",");
        sb.append("target=").append(action.targetEntityId() != null ? action.targetEntityId().toString() : "").append(",");
        sb.append("params=");
        if (action.parameters() != null) {
          TreeMap<String, Object> sortedParams = new TreeMap<>(action.parameters());
          for (Map.Entry<String, Object> entry : sortedParams.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue() != null ? entry.getValue().toString() : "null").append("&");
          }
        }
        sb.append("];");
      }

      byte[] hashBytes = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  public static String calculateHash(EveDtos.EvePlan plan) {
    if (plan == null) {
      throw new IllegalArgumentException("Plan cannot be null");
    }
    return calculateHash(plan.planId(), plan.version(), plan.intent(), plan.riskTier(), plan.actions());
  }

  public static boolean verifyHash(EveDtos.EvePlan plan, String expectedHash) {
    if (plan == null || expectedHash == null || expectedHash.isBlank()) {
      return false;
    }
    String actualHash = calculateHash(plan);
    return actualHash.equalsIgnoreCase(expectedHash.trim());
  }
}
