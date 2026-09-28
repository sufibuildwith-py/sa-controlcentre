package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.*;
import org.junit.jupiter.api.Test;

class EvePlanHasherTest {

  @Test
  void computesDeterministicHashForIdenticalPlans() {
    UUID planId = UUID.randomUUID();
    UUID actionId = UUID.randomUUID();
    UUID empId = UUID.randomUUID();

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("employeeId", empId.toString());
    params.put("amount", "3000.00");
    params.put("payerAccount", "AZ-2");

    EveDtos.EvePlanAction action = new EveDtos.EvePlanAction(
        actionId, 1, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", empId, "Raj Sharma",
        params, "Reduced payable", "FINANCE_WRITE");

    String hash1 = EvePlanHasher.calculateHash(planId, 1, "EMPLOYEE_PAYMENT", EveDtos.RiskTier.FINANCIAL_WRITE, List.of(action));
    String hash2 = EvePlanHasher.calculateHash(planId, 1, "EMPLOYEE_PAYMENT", EveDtos.RiskTier.FINANCIAL_WRITE, List.of(action));

    assertThat(hash1).isNotEmpty().hasSize(64).isEqualTo(hash2);
  }

  @Test
  void hashChangesWhenParametersAreModified() {
    UUID planId = UUID.randomUUID();
    UUID actionId = UUID.randomUUID();
    UUID empId = UUID.randomUUID();

    Map<String, Object> params1 = Map.of("employeeId", empId.toString(), "amount", "3000.00", "payerAccount", "AZ-2");
    Map<String, Object> params2 = Map.of("employeeId", empId.toString(), "amount", "3500.00", "payerAccount", "AZ-2");

    EveDtos.EvePlanAction action1 = new EveDtos.EvePlanAction(
        actionId, 1, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", empId, "Raj Sharma",
        params1, "Reduced payable", "FINANCE_WRITE");
    EveDtos.EvePlanAction action2 = new EveDtos.EvePlanAction(
        actionId, 1, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", empId, "Raj Sharma",
        params2, "Reduced payable", "FINANCE_WRITE");

    String hash1 = EvePlanHasher.calculateHash(planId, 1, "EMPLOYEE_PAYMENT", EveDtos.RiskTier.FINANCIAL_WRITE, List.of(action1));
    String hash2 = EvePlanHasher.calculateHash(planId, 1, "EMPLOYEE_PAYMENT", EveDtos.RiskTier.FINANCIAL_WRITE, List.of(action2));

    assertThat(hash1).isNotEqualTo(hash2);
  }

  @Test
  void hashChangesWhenVersionOrRiskTierChanges() {
    UUID planId = UUID.randomUUID();
    UUID actionId = UUID.randomUUID();
    UUID empId = UUID.randomUUID();

    Map<String, Object> params = Map.of("employeeId", empId.toString(), "amount", "3000.00");
    EveDtos.EvePlanAction action = new EveDtos.EvePlanAction(
        actionId, 1, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", empId, "Raj Sharma",
        params, "Effect", "FINANCE_WRITE");

    String hashV1 = EvePlanHasher.calculateHash(planId, 1, "EMPLOYEE_PAYMENT", EveDtos.RiskTier.FINANCIAL_WRITE, List.of(action));
    String hashV2 = EvePlanHasher.calculateHash(planId, 2, "EMPLOYEE_PAYMENT", EveDtos.RiskTier.FINANCIAL_WRITE, List.of(action));
    String hashSafe = EvePlanHasher.calculateHash(planId, 1, "EMPLOYEE_PAYMENT", EveDtos.RiskTier.SAFE_OPERATION, List.of(action));

    assertThat(hashV1).isNotEqualTo(hashV2);
    assertThat(hashV1).isNotEqualTo(hashSafe);
  }

  @Test
  void verifyHashValidatesAuthenticTokenAndRejectsTamperedToken() {
    UUID planId = UUID.randomUUID();
    UUID actionId = UUID.randomUUID();
    UUID empId = UUID.randomUUID();

    Map<String, Object> params = Map.of("employeeId", empId.toString(), "amount", "3000.00", "payerAccount", "AZ-2");
    EveDtos.EvePlanAction action = new EveDtos.EvePlanAction(
        actionId, 1, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", empId, "Raj Sharma",
        params, "Effect", "FINANCE_WRITE");

    EveDtos.EvePlan plan = new EveDtos.EvePlan(
        planId, UUID.randomUUID(), "EMPLOYEE_PAYMENT", "Summary",
        EveDtos.RiskTier.FINANCIAL_WRITE, true, "dummy", 1, "PROPOSED", List.of(action));

    String expectedHash = EvePlanHasher.calculateHash(plan);
    assertThat(EvePlanHasher.verifyHash(plan, expectedHash)).isTrue();
    assertThat(EvePlanHasher.verifyHash(plan, "tampered-hash-value-00000000000000000000000000000000000000000000")).isFalse();
    assertThat(EvePlanHasher.verifyHash(null, expectedHash)).isFalse();
  }
}
