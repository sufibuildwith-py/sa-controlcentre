package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.finance.FinancePostingService;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class EveGovernedExecutionTest {

  private EveModelProvider modelProvider;
  private EveRetrievalService retrievalService;
  private EveContextEngine contextEngine;
  private EveKnowledgeService knowledgeService;
  private EveMemoryService memoryService;
  private FinanceReadService financeReadService;
  private FinancePostingService financePostingService;
  private JdbcTemplate jdbc;
  private EveCommandGateway commandGateway;
  private EveService service;

  private final UUID employeeId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    modelProvider = mock(EveModelProvider.class);
    retrievalService = mock(EveRetrievalService.class);
    contextEngine = new EveContextEngine("Asia/Kolkata");
    knowledgeService = new EveKnowledgeService();
    memoryService = mock(EveMemoryService.class);
    financeReadService = mock(FinanceReadService.class);
    financePostingService = mock(FinancePostingService.class);
    jdbc = mock(JdbcTemplate.class);

    EmployeePaymentCommandDefinition paymentDef = new EmployeePaymentCommandDefinition();
    EveCommandRegistry registry = new EveCommandRegistry(List.of(paymentDef));
    commandGateway = new EveCommandGateway(registry, financePostingService, financeReadService, jdbc, new ObjectMapper());

    service = new EveService(
        modelProvider,
        retrievalService,
        contextEngine,
        knowledgeService,
        memoryService,
        financeReadService,
        null,
        commandGateway,
        jdbc);

    // Mock session existence check
    when(jdbc.queryForObject(contains("FROM eve_sessions WHERE id = ?"), eq(Integer.class), any(UUID.class)))
        .thenReturn(1);
  }

  @Test
  void proposesGovernedPlanForPaymentPrompt() {
    String prompt = "Sharma ko 3000 de do";

    // 1. Model interpretation
    when(modelProvider.interpret(any())).thenReturn(
        EveModelProvider.EveInterpretation.proposePayment("Sharma", 300_000L, null));

    // 2. Candidate resolution -> unique Raj Sharma
    EveRetrievalService.Candidate emp = new EveRetrievalService.Candidate(
        employeeId, "EMPLOYEE", "Raj Sharma", "EMP-001", "Senior Cameraman");
    when(retrievalService.resolveEmployee("Sharma")).thenReturn(
        EveRetrievalService.ResolutionResult.resolved(emp, EveRetrievalService.MatchMethod.EXACT_NAME, "Sharma"));

    // 3. Authoritative financial balance from read service
    when(financeReadService.employee(employeeId)).thenReturn(Map.of(
        "employee", Map.of("id", employeeId, "displayName", "Raj Sharma"),
        "earned", new BigDecimal("8000.00"),
        "paid", new BigDecimal("3000.00"),
        "outstanding", new BigDecimal("5000.00"),
        "obligations", List.of()));

    EveDtos.QueryResponse response = service.query(new EveDtos.QueryRequest(prompt, sessionId));

    assertThat(response.status()).isEqualTo("WAITING_CONFIRMATION");
    assertThat(response.plan()).isNotNull();
    assertThat(response.plan().riskTier()).isEqualTo(EveDtos.RiskTier.FINANCIAL_WRITE);
    assertThat(response.plan().confirmationRequired()).isTrue();
    assertThat(response.plan().planHash()).isNotEmpty().hasSize(64);
    assertThat(response.plan().actions()).hasSize(1);

    EveDtos.EvePlanAction action = response.plan().actions().getFirst();
    assertThat(action.commandType()).isEqualTo("RECORD_EMPLOYEE_PAYMENT");
    assertThat(action.targetEntityId()).isEqualTo(employeeId);
    assertThat(action.parameters().get("amount")).isEqualTo("3000.00");
    assertThat(action.parameters().get("payerAccount")).isEqualTo("AZ-2");

    // Verify trace sequence contains all mandatory Phase 3 stages
    List<String> eventTypes = response.trace().stream().map(EveDtos.TraceEventView::eventType).toList();
    assertThat(eventTypes).containsSequence(
        "STARTED",
        "INTERPRETING",
        "RESOLVING",
        "RETRIEVING",
        "VALIDATING",
        "PLANNING",
        "WAITING_CONFIRMATION");
  }

  @Test
  void rejectsPaymentWhenExceedsOutstandingBalance() {
    String prompt = "Sharma ko 6000 de do";

    when(modelProvider.interpret(any())).thenReturn(
        EveModelProvider.EveInterpretation.proposePayment("Sharma", 600_000L, null));

    EveRetrievalService.Candidate emp = new EveRetrievalService.Candidate(
        employeeId, "EMPLOYEE", "Raj Sharma", "EMP-001", "Senior Cameraman");
    when(retrievalService.resolveEmployee("Sharma")).thenReturn(
        EveRetrievalService.ResolutionResult.resolved(emp, EveRetrievalService.MatchMethod.EXACT_NAME, "Sharma"));

    // Outstanding balance is only 5000.00
    when(financeReadService.employee(employeeId)).thenReturn(Map.of(
        "employee", Map.of("id", employeeId, "displayName", "Raj Sharma"),
        "earned", new BigDecimal("8000.00"),
        "paid", new BigDecimal("3000.00"),
        "outstanding", new BigDecimal("5000.00"),
        "obligations", List.of()));

    EveDtos.QueryResponse response = service.query(new EveDtos.QueryRequest(prompt, sessionId));

    assertThat(response.status()).isEqualTo("BLOCKED");
    assertThat(response.plan()).isNull();
    assertThat(response.message().content()).contains("exceeds Raj Sharma's total outstanding balance of ₹5000.00");
  }

  @Test
  void confirmsAndExecutesPlanWithCanonicalPostingAndPostgresVerification() {
    UUID planId = UUID.randomUUID();
    UUID actionId = UUID.randomUUID();

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("employeeId", employeeId.toString());
    params.put("amount", "3000.00");
    params.put("payerAccount", "AZ-2");
    params.put("date", "2026-09-28");
    params.put("description", "Payment via EVE");

    EveDtos.EvePlanAction action = new EveDtos.EvePlanAction(
        actionId, 1, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", employeeId, "Raj Sharma",
        params, "Outstanding balance will reduce", "FINANCE_WRITE");

    String planHash = EvePlanHasher.calculateHash(planId, 1, "EMPLOYEE_PAYMENT", EveDtos.RiskTier.FINANCIAL_WRITE, List.of(action));

    // Mock DB plan row with FOR UPDATE
    when(jdbc.queryForList(contains("FROM eve_plans WHERE id = ? FOR UPDATE"), eq(planId)))
        .thenReturn(List.of(Map.of(
            "id", planId,
            "session_id", sessionId,
            "message_id", UUID.randomUUID(),
            "intent", "EMPLOYEE_PAYMENT",
            "summary", "Pay ₹3,000.00 to Raj Sharma",
            "risk_tier", "FINANCIAL_WRITE",
            "confirmation_required", true,
            "plan_hash", planHash,
            "version", 1,
            "status", "PROPOSED")));

    when(jdbc.query(contains("FROM eve_plan_actions WHERE plan_id = ?"), any(RowMapper.class), eq(planId)))
        .thenReturn(List.of(action));

    when(jdbc.queryForList(contains("FROM eve_plans WHERE id = ?"), eq(planId)))
        .thenReturn(List.of(Map.of(
            "id", planId,
            "session_id", sessionId,
            "message_id", UUID.randomUUID(),
            "intent", "EMPLOYEE_PAYMENT",
            "summary", "Pay ₹3,000.00 to Raj Sharma",
            "risk_tier", "FINANCIAL_WRITE",
            "confirmation_required", true,
            "plan_hash", planHash,
            "version", 1,
            "status", "PROPOSED")));

    // Mock domain checks: employee & account existence
    when(jdbc.queryForObject(contains("FROM employees WHERE id = ? AND status = 'ACTIVE'"), eq(Integer.class), eq(employeeId)))
        .thenReturn(1);
    when(jdbc.queryForObject(contains("FROM finance_accounts WHERE code = ? AND active"), eq(Integer.class), eq("AZ-2")))
        .thenReturn(1);

    // Mock fresh balance check
    when(financeReadService.employee(employeeId)).thenReturn(Map.of(
        "employee", Map.of("id", employeeId, "displayName", "Raj Sharma"),
        "outstanding", new BigDecimal("5000.00")));

    // Mock canonical execution in FinancePostingService
    UUID txnId = UUID.randomUUID();
    when(financePostingService.employeePayment(any())).thenReturn(Map.of(
        "id", txnId,
        "transactionNo", "TXN-2026-0042",
        "status", "POSTED"));

    UUID az2AccId = UUID.randomUUID();
    when(jdbc.query(contains("FROM finance_accounts WHERE code = ?"), any(RowMapper.class), eq("AZ-2")))
        .thenReturn(List.of(az2AccId));

    // Mock verification queries against PostgreSQL
    when(jdbc.queryForList(contains("FROM finance_transactions WHERE id = ?"), eq(txnId)))
        .thenReturn(List.of(Map.of(
            "id", txnId,
            "transaction_no", "TXN-2026-0042",
            "transaction_type", "EMPLOYEE_PAYMENT",
            "status", "POSTED",
            "amount", new BigDecimal("3000.00"),
            "employee_id", employeeId,
            "payer_account_id", az2AccId)));
    when(jdbc.queryForObject(contains("FROM finance_employee_payment_allocations WHERE transaction_id = ?"), eq(BigDecimal.class), eq(txnId)))
        .thenReturn(new BigDecimal("3000.00"));
    when(jdbc.queryForObject(contains("JOIN finance_employee_obligations"), eq(Integer.class), eq(txnId), eq(employeeId)))
        .thenReturn(0);
    when(jdbc.queryForObject(contains("FROM finance_audit_events WHERE transaction_id = ? AND action = 'POSTED'"), eq(Integer.class), eq(txnId)))
        .thenReturn(1);

    EveDtos.ConfirmPlanRequest request = new EveDtos.ConfirmPlanRequest(
        sessionId, planId, 1, planHash, "Approved by Mamu");

    EveDtos.PlanExecutionResponse execResponse = service.confirmPlan(planId, request);

    assertThat(execResponse.status()).isEqualTo("COMPLETED");
    assertThat(execResponse.actions()).hasSize(1);
    assertThat(execResponse.actions().getFirst().status()).isEqualTo("VERIFIED");
    assertThat(execResponse.actions().getFirst().canonicalRecordId()).isEqualTo(txnId);

    // Verify trace stages recorded during confirmation
    verify(jdbc, atLeastOnce()).update(
        contains("INSERT INTO eve_trace_events"),
        any(), eq(sessionId), any(), any(), eq("REVALIDATING"), eq("OK"), any(), any(), any());
    verify(jdbc, atLeastOnce()).update(
        contains("INSERT INTO eve_trace_events"),
        any(), eq(sessionId), any(), any(), eq("EXECUTING"), eq("OK"), any(), any(), any());
    verify(jdbc, atLeastOnce()).update(
        contains("INSERT INTO eve_trace_events"),
        any(), eq(sessionId), any(), any(), eq("VERIFYING"), eq("OK"), any(), any(), any());
    verify(jdbc, atLeastOnce()).update(
        contains("INSERT INTO eve_trace_events"),
        any(), eq(sessionId), any(), any(), eq("COMPLETED"), eq("OK"), any(), any(), any());
  }

  @Test
  void rejectsTamperedConfirmationToken() {
    UUID planId = UUID.randomUUID();
    String storedHash = "valid-original-plan-hash-1111111111111111111111111111111111111111";

    when(jdbc.queryForList(contains("FROM eve_plans WHERE id = ? FOR UPDATE"), eq(planId)))
        .thenReturn(List.of(Map.of(
            "id", planId,
            "session_id", sessionId,
            "message_id", UUID.randomUUID(),
            "intent", "EMPLOYEE_PAYMENT",
            "summary", "Pay ₹3,000",
            "risk_tier", "FINANCIAL_WRITE",
            "confirmation_required", true,
            "plan_hash", storedHash,
            "version", 1,
            "status", "PROPOSED")));

    EveDtos.ConfirmPlanRequest requestWithTamperedHash = new EveDtos.ConfirmPlanRequest(
        sessionId, planId, 1, "tampered-hash-value-00000000000000000000000000000000000000000000", "Tampered");

    assertThatThrownBy(() -> service.confirmPlan(planId, requestWithTamperedHash))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Plan version or cryptographic hash does not match");
  }

  @Test
  void detectsStalePlanWhenCanonicalStateChangesBeforeConfirmation() {
    UUID planId = UUID.randomUUID();
    UUID actionId = UUID.randomUUID();

    Map<String, Object> params = Map.of(
        "employeeId", employeeId.toString(),
        "amount", "3000.00",
        "payerAccount", "AZ-2");

    EveDtos.EvePlanAction action = new EveDtos.EvePlanAction(
        actionId, 1, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", employeeId, "Raj Sharma",
        params, "Effect", "FINANCE_WRITE");

    String planHash = EvePlanHasher.calculateHash(planId, 1, "EMPLOYEE_PAYMENT", EveDtos.RiskTier.FINANCIAL_WRITE, List.of(action));

    when(jdbc.queryForList(contains("FROM eve_plans WHERE id = ? FOR UPDATE"), eq(planId)))
        .thenReturn(List.of(Map.of(
            "id", planId,
            "session_id", sessionId,
            "message_id", UUID.randomUUID(),
            "intent", "EMPLOYEE_PAYMENT",
            "summary", "Pay ₹3,000",
            "risk_tier", "FINANCIAL_WRITE",
            "confirmation_required", true,
            "plan_hash", planHash,
            "version", 1,
            "status", "PROPOSED")));

    when(jdbc.query(contains("FROM eve_plan_actions WHERE plan_id = ?"), any(RowMapper.class), eq(planId)))
        .thenReturn(List.of(action));

    when(jdbc.queryForList(contains("FROM eve_plans WHERE id = ?"), eq(planId)))
        .thenReturn(List.of(Map.of(
            "id", planId,
            "session_id", sessionId,
            "message_id", UUID.randomUUID(),
            "intent", "EMPLOYEE_PAYMENT",
            "summary", "Pay ₹3,000",
            "risk_tier", "FINANCIAL_WRITE",
            "confirmation_required", true,
            "plan_hash", planHash,
            "version", 1,
            "status", "PROPOSED")));

    when(jdbc.queryForObject(contains("FROM employees WHERE id = ? AND status = 'ACTIVE'"), eq(Integer.class), eq(employeeId)))
        .thenReturn(1);
    when(jdbc.queryForObject(contains("FROM finance_accounts WHERE code = ? AND active"), eq(Integer.class), eq("AZ-2")))
        .thenReturn(1);

    // Balance dropped to only 1000.00 (e.g. payout made outside EVE)
    when(financeReadService.employee(employeeId)).thenReturn(Map.of(
        "employee", Map.of("id", employeeId, "displayName", "Raj Sharma"),
        "outstanding", new BigDecimal("1000.00")));

    EveDtos.ConfirmPlanRequest request = new EveDtos.ConfirmPlanRequest(
        sessionId, planId, 1, planHash, "Approved");

    assertThatThrownBy(() -> service.confirmPlan(planId, request))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Preconditions changed");

    // Verify plan transitioned to STALE_PLAN in DB
    verify(jdbc).update(contains("UPDATE eve_plans SET status = 'STALE_PLAN'"), eq(planId));
  }

  @Test
  void cancelsProposedPlanCleanly() {
    UUID planId = UUID.randomUUID();

    when(jdbc.queryForList(argThat(sql -> sql != null && sql.contains("FOR UPDATE")), eq(planId)))
        .thenReturn(List.of(Map.of("id", planId, "session_id", sessionId, "status", "PROPOSED")));

    when(jdbc.queryForList(argThat(sql -> sql != null && !sql.contains("FOR UPDATE") && sql.contains("FROM eve_plans WHERE id = ?")), eq(planId)))
        .thenReturn(List.of(Map.of(
            "id", planId,
            "session_id", sessionId,
            "message_id", UUID.randomUUID(),
            "intent", "EMPLOYEE_PAYMENT",
            "summary", "Pay ₹3,000",
            "risk_tier", "FINANCIAL_WRITE",
            "confirmation_required", true,
            "plan_hash", "hash",
            "version", 1,
            "status", "CANCELLED")));

    EveDtos.EvePlan cancelled = service.cancelPlan(planId, new EveDtos.CancelPlanRequest(sessionId, planId, "Changed my mind"));

    assertThat(cancelled.status()).isEqualTo("CANCELLED");
    verify(jdbc).update(contains("UPDATE eve_plans SET status = 'CANCELLED'"), any(), eq(planId));
  }

  @Test
  void rejectsConfirmationWhenPlanIdMismatchBetweenPathAndBody() {
    UUID planIdA = UUID.randomUUID();
    UUID planIdB = UUID.randomUUID();

    EveDtos.ConfirmPlanRequest request = new EveDtos.ConfirmPlanRequest(
        sessionId, planIdB, 1, "some-hash", "Approved");

    assertThatThrownBy(() -> service.confirmPlan(planIdA, request))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Plan ID in path must match request body");
  }

  @Test
  void rejectsConfirmationWhenVersionMismatch() {
    UUID planId = UUID.randomUUID();
    String storedHash = "valid-hash-11111111111111111111111111111111111111111111111111111111";

    when(jdbc.queryForList(contains("FROM eve_plans WHERE id = ? FOR UPDATE"), eq(planId)))
        .thenReturn(List.of(Map.of(
            "id", planId,
            "session_id", sessionId,
            "message_id", UUID.randomUUID(),
            "intent", "EMPLOYEE_PAYMENT",
            "summary", "Pay ₹3,000",
            "risk_tier", "FINANCIAL_WRITE",
            "confirmation_required", true,
            "plan_hash", storedHash,
            "version", 1,
            "status", "PROPOSED")));

    // Version 2 in request instead of stored version 1
    EveDtos.ConfirmPlanRequest request = new EveDtos.ConfirmPlanRequest(
        sessionId, planId, 2, storedHash, "Approved");

    assertThatThrownBy(() -> service.confirmPlan(planId, request))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Plan version or cryptographic hash does not match");
  }

  @Test
  void rejectsConfirmationWhenSessionMismatch() {
    UUID planId = UUID.randomUUID();
    UUID wrongSessionId = UUID.randomUUID();
    String storedHash = "valid-hash-11111111111111111111111111111111111111111111111111111111";

    when(jdbc.queryForList(contains("FROM eve_plans WHERE id = ? FOR UPDATE"), eq(planId)))
        .thenReturn(List.of(Map.of(
            "id", planId,
            "session_id", sessionId, // plan belongs to sessionId
            "message_id", UUID.randomUUID(),
            "intent", "EMPLOYEE_PAYMENT",
            "summary", "Pay ₹3,000",
            "risk_tier", "FINANCIAL_WRITE",
            "confirmation_required", true,
            "plan_hash", storedHash,
            "version", 1,
            "status", "PROPOSED")));

    // Confirmation request specifies wrongSessionId
    EveDtos.ConfirmPlanRequest request = new EveDtos.ConfirmPlanRequest(
        wrongSessionId, planId, 1, storedHash, "Approved");

    assertThatThrownBy(() -> service.confirmPlan(planId, request))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Plan does not belong to specified session");
  }

  @Test
  void returnsCachedResultWhenPlanAlreadyCompletedWithoutReExecuting() {
    UUID planId = UUID.randomUUID();
    String storedHash = "valid-hash-11111111111111111111111111111111111111111111111111111111";

    when(jdbc.queryForList(contains("FROM eve_plans WHERE id = ? FOR UPDATE"), eq(planId)))
        .thenReturn(List.of(Map.of(
            "id", planId,
            "session_id", sessionId,
            "message_id", UUID.randomUUID(),
            "intent", "EMPLOYEE_PAYMENT",
            "summary", "Pay ₹3,000",
            "risk_tier", "FINANCIAL_WRITE",
            "confirmation_required", true,
            "plan_hash", storedHash,
            "version", 1,
            "status", "COMPLETED")));

    when(jdbc.queryForList(argThat(sql -> sql != null && !sql.contains("FOR UPDATE") && sql.contains("FROM eve_plans WHERE id = ?")), eq(planId)))
        .thenReturn(List.of(Map.of(
            "id", planId,
            "session_id", sessionId,
            "message_id", UUID.randomUUID(),
            "intent", "EMPLOYEE_PAYMENT",
            "summary", "Pay ₹3,000",
            "risk_tier", "FINANCIAL_WRITE",
            "confirmation_required", true,
            "plan_hash", storedHash,
            "version", 1,
            "status", "COMPLETED")));

    when(jdbc.query(contains("FROM eve_plan_actions WHERE plan_id = ?"), any(RowMapper.class), eq(planId)))
        .thenReturn(List.of());

    when(jdbc.query(contains("FROM eve_trace_events WHERE session_id = ?"), any(RowMapper.class), eq(sessionId)))
        .thenReturn(List.of());

    EveDtos.ConfirmPlanRequest request = new EveDtos.ConfirmPlanRequest(
        sessionId, planId, 1, storedHash, "Retry confirmation");

    EveDtos.PlanExecutionResponse response = service.confirmPlan(planId, request);

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.message().content()).contains("already executed successfully");

    // Zero domain execution invocations
    verify(financePostingService, never()).employeePayment(any());
  }

  @Test
  void rejectsConfirmationWhenPlanWasCancelled() {
    UUID planId = UUID.randomUUID();
    String storedHash = "valid-hash-11111111111111111111111111111111111111111111111111111111";

    when(jdbc.queryForList(contains("FROM eve_plans WHERE id = ? FOR UPDATE"), eq(planId)))
        .thenReturn(List.of(Map.of(
            "id", planId,
            "session_id", sessionId,
            "message_id", UUID.randomUUID(),
            "intent", "EMPLOYEE_PAYMENT",
            "summary", "Pay ₹3,000",
            "risk_tier", "FINANCIAL_WRITE",
            "confirmation_required", true,
            "plan_hash", storedHash,
            "version", 1,
            "status", "CANCELLED")));

    EveDtos.ConfirmPlanRequest request = new EveDtos.ConfirmPlanRequest(
        sessionId, planId, 1, storedHash, "Confirm cancelled plan");

    assertThatThrownBy(() -> service.confirmPlan(planId, request))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Plan status is CANCELLED and cannot be confirmed");
  }

  @Test
  void rejectsVerificationWhenTransactionBelongsToUnrelatedEmployeeOrDifferentAmount() {
    EmployeePaymentCommandDefinition def = new EmployeePaymentCommandDefinition();
    UUID planId = UUID.randomUUID();
    UUID targetEmployeeId = UUID.randomUUID();
    UUID unrelatedEmployeeId = UUID.randomUUID();
    UUID unrelatedTxnId = UUID.randomUUID();
    UUID payerAccId = UUID.randomUUID();

    EveDtos.EvePlanAction action = new EveDtos.EvePlanAction(
        UUID.randomUUID(), 1, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", targetEmployeeId, "Raj Sharma",
        Map.of("employeeId", targetEmployeeId.toString(), "amount", "3000.00", "payerAccount", "AZ-2"),
        "Effect", "FINANCE_WRITE");

    EveDtos.EveCommandResult executionResult = new EveDtos.EveCommandResult(
        "RECORD_EMPLOYEE_PAYMENT", "EXECUTED", Instant.now(), unrelatedTxnId, "Executed", "key");

    JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
    EveVerificationContext vContext = new EveVerificationContext(
        sessionId, planId, unrelatedTxnId, financeReadService, mockJdbc);

    // Mock query returning transaction for UNRELATED employee
    when(mockJdbc.queryForList(contains("FROM finance_transactions WHERE id = ?"), eq(unrelatedTxnId)))
        .thenReturn(List.of(Map.of(
            "id", unrelatedTxnId,
            "transaction_type", "EMPLOYEE_PAYMENT",
            "status", "POSTED",
            "amount", new BigDecimal("3000.00"),
            "employee_id", unrelatedEmployeeId, // MISMATCH
            "payer_account_id", payerAccId)));

    EveDtos.EveVerificationResult result = def.verify(action, executionResult, vContext);

    assertThat(result.status()).isEqualTo("FAILED");
    assertThat(result.notes()).contains("Transaction is associated with wrong employee");
  }

  @Test
  void rejectsVerificationWhenTransactionAmountMismatchesExpectedPayment() {
    EmployeePaymentCommandDefinition def = new EmployeePaymentCommandDefinition();
    UUID planId = UUID.randomUUID();
    UUID targetEmployeeId = UUID.randomUUID();
    UUID txnId = UUID.randomUUID();
    UUID payerAccId = UUID.randomUUID();

    EveDtos.EvePlanAction action = new EveDtos.EvePlanAction(
        UUID.randomUUID(), 1, "FINANCE", "RECORD_EMPLOYEE_PAYMENT", targetEmployeeId, "Raj Sharma",
        Map.of("employeeId", targetEmployeeId.toString(), "amount", "3000.00", "payerAccount", "AZ-2"),
        "Effect", "FINANCE_WRITE");

    EveDtos.EveCommandResult executionResult = new EveDtos.EveCommandResult(
        "RECORD_EMPLOYEE_PAYMENT", "EXECUTED", Instant.now(), txnId, "Executed", "key");

    JdbcTemplate mockJdbc = mock(JdbcTemplate.class);
    EveVerificationContext vContext = new EveVerificationContext(
        sessionId, planId, txnId, financeReadService, mockJdbc);

    // Mock query returning transaction with different amount (e.g. 5000.00 instead of 3000.00)
    when(mockJdbc.queryForList(contains("FROM finance_transactions WHERE id = ?"), eq(txnId)))
        .thenReturn(List.of(Map.of(
            "id", txnId,
            "transaction_type", "EMPLOYEE_PAYMENT",
            "status", "POSTED",
            "amount", new BigDecimal("5000.00"), // MISMATCH
            "employee_id", targetEmployeeId,
            "payer_account_id", payerAccId)));

    EveDtos.EveVerificationResult result = def.verify(action, executionResult, vContext);

    assertThat(result.status()).isEqualTo("FAILED");
    assertThat(result.notes()).contains("Transaction amount does not match expected payment amount");
  }
}
