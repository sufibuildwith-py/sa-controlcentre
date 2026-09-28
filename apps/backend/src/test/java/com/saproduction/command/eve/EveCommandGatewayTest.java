package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.saproduction.command.finance.FinancePostingService;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.shared.ApiException;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EveCommandGatewayTest {

  private EveCommandRegistry registry;
  private FinancePostingService postingService;
  private FinanceReadService readService;
  private JdbcTemplate jdbc;
  private EveCommandGateway gateway;

  @BeforeEach
  void setUp() {
    EmployeePaymentCommandDefinition paymentDef = new EmployeePaymentCommandDefinition();
    registry = new EveCommandRegistry(List.of(paymentDef));
    postingService = mock(FinancePostingService.class);
    readService = mock(FinanceReadService.class);
    jdbc = mock(JdbcTemplate.class);
    gateway = new EveCommandGateway(registry, postingService, readService, jdbc, null);
  }

  @Test
  void enforcesFiniteCommandRegistry() {
    UUID planId = UUID.randomUUID();
    EveDtos.EvePlanAction action = new EveDtos.EvePlanAction(
        UUID.randomUUID(), 1, "SECURITY", "ARBITRARY_SQL_EXECUTE",
        null, null, Map.of("sql", "DROP TABLE users;"), "Exploit", "ROOT");

    EveDtos.EvePlan plan = new EveDtos.EvePlan(
        planId, UUID.randomUUID(), "MALICIOUS", "Summary",
        EveDtos.RiskTier.BLOCKED, true, "hash", 1, "PROPOSED", List.of(action));

    assertThatThrownBy(() -> gateway.validatePlanSchema(plan))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("not registered in the finite EVE Command Registry");
  }

  @Test
  void rejectsPlanExceedingMaxActions() {
    UUID planId = UUID.randomUUID();
    UUID empId = UUID.randomUUID();
    List<EveDtos.EvePlanAction> actions = new ArrayList<>();
    for (int i = 1; i <= 11; i++) {
      actions.add(new EveDtos.EvePlanAction(
          UUID.randomUUID(), i, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", empId, "Raj Sharma",
          Map.of("employeeId", empId.toString(), "amount", "100.00", "payerAccount", "AZ-2"),
          "Effect", "FINANCE_WRITE"));
    }

    EveDtos.EvePlan plan = new EveDtos.EvePlan(
        planId, UUID.randomUUID(), "EMPLOYEE_PAYMENT", "Large batch",
        EveDtos.RiskTier.FINANCIAL_WRITE, true, "hash", 1, "PROPOSED", actions);

    assertThatThrownBy(() -> gateway.validatePlanSchema(plan))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("exceeds maximum limit of 10 actions");
  }

  @Test
  void rejectsInvalidPaymentParameters() {
    UUID empId = UUID.randomUUID();

    // 1. Negative amount
    assertThatThrownBy(() -> registry.getRequired("RECORD_EMPLOYEE_PAYMENT")
        .validateSchema(Map.of("employeeId", empId.toString(), "amount", "-500.00", "payerAccount", "AZ-2")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("must be greater than zero");

    // 2. High precision (> 2 decimal places)
    assertThatThrownBy(() -> registry.getRequired("RECORD_EMPLOYEE_PAYMENT")
        .validateSchema(Map.of("employeeId", empId.toString(), "amount", "500.555", "payerAccount", "AZ-2")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("at most two decimal places");

    // 3. Invalid payer account
    assertThatThrownBy(() -> registry.getRequired("RECORD_EMPLOYEE_PAYMENT")
        .validateSchema(Map.of("employeeId", empId.toString(), "amount", "500.00", "payerAccount", "UNAUTHORIZED_ACC")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Choose Azeem (AZ-2) or Akash (AK-2)");
  }

  @Test
  void rejectsNonContiguousActionSequence() {
    UUID planId = UUID.randomUUID();
    UUID empId = UUID.randomUUID();

    EveDtos.EvePlanAction a1 = new EveDtos.EvePlanAction(
        UUID.randomUUID(), 1, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", empId, "Raj Sharma",
        Map.of("employeeId", empId.toString(), "amount", "100.00", "payerAccount", "AZ-2"), "Effect", "FINANCE_WRITE");
    EveDtos.EvePlanAction a3 = new EveDtos.EvePlanAction(
        UUID.randomUUID(), 3, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", empId, "Raj Sharma",
        Map.of("employeeId", empId.toString(), "amount", "100.00", "payerAccount", "AZ-2"), "Effect", "FINANCE_WRITE");

    EveDtos.EvePlan plan = new EveDtos.EvePlan(
        planId, UUID.randomUUID(), "EMPLOYEE_PAYMENT", "Skipped seq",
        EveDtos.RiskTier.FINANCIAL_WRITE, true, "hash", 1, "PROPOSED", List.of(a1, a3));

    assertThatThrownBy(() -> gateway.validatePlanSchema(plan))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Action sequence numbers must be contiguous");
  }

  @Test
  void rejectsEmptyPlanWithZeroActions() {
    UUID planId = UUID.randomUUID();
    EveDtos.EvePlan plan = new EveDtos.EvePlan(
        planId, UUID.randomUUID(), "EMPLOYEE_PAYMENT", "Zero actions",
        EveDtos.RiskTier.FINANCIAL_WRITE, true, "hash", 1, "PROPOSED", List.of());

    assertThatThrownBy(() -> gateway.validatePlanSchema(plan))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Plan must contain at least one action");
  }

  @Test
  void acceptsPlanWithExactlyTenActions() {
    UUID planId = UUID.randomUUID();
    UUID empId = UUID.randomUUID();
    List<EveDtos.EvePlanAction> actions = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      actions.add(new EveDtos.EvePlanAction(
          UUID.randomUUID(), i, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", empId, "Raj Sharma",
          Map.of("employeeId", empId.toString(), "amount", "100.00", "payerAccount", "AZ-2"),
          "Effect", "FINANCE_WRITE"));
    }

    EveDtos.EvePlan plan = new EveDtos.EvePlan(
        planId, UUID.randomUUID(), "EMPLOYEE_PAYMENT", "Ten actions batch",
        EveDtos.RiskTier.FINANCIAL_WRITE, true, "hash", 1, "PROPOSED", actions);

    // Must not throw
    gateway.validatePlanSchema(plan);
  }

  @Test
  void rejectsDuplicateActionSequenceNumbers() {
    UUID planId = UUID.randomUUID();
    UUID empId = UUID.randomUUID();

    EveDtos.EvePlanAction a1 = new EveDtos.EvePlanAction(
        UUID.randomUUID(), 1, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", empId, "Raj Sharma",
        Map.of("employeeId", empId.toString(), "amount", "100.00", "payerAccount", "AZ-2"), "Effect", "FINANCE_WRITE");
    EveDtos.EvePlanAction a2 = new EveDtos.EvePlanAction(
        UUID.randomUUID(), 1, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", empId, "Raj Sharma",
        Map.of("employeeId", empId.toString(), "amount", "100.00", "payerAccount", "AZ-2"), "Effect", "FINANCE_WRITE");

    EveDtos.EvePlan plan = new EveDtos.EvePlan(
        planId, UUID.randomUUID(), "EMPLOYEE_PAYMENT", "Duplicate seq",
        EveDtos.RiskTier.FINANCIAL_WRITE, true, "hash", 1, "PROPOSED", List.of(a1, a2));

    assertThatThrownBy(() -> gateway.validatePlanSchema(plan))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Action sequence numbers must be contiguous");
  }
}
