package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.finance.FinanceCommands;
import com.saproduction.command.finance.FinancePostingService;
import com.saproduction.command.finance.FinanceReadService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class EvePostgresIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", postgres::getJdbcUrl);
    p.add("spring.datasource.username", postgres::getUsername);
    p.add("spring.datasource.password", postgres::getPassword);
    p.add("app.demo-seed", () -> false);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired EveService eveService;
  @Autowired EveMemoryService memoryService;
  @Autowired EmployeeRepository employeeRepo;

  @Test
  void verifiesEvePersistenceTablesAndZeroBusinessMutation() {
    // 1. Verify V028 migration tables exist in PostgreSQL
    Integer sessionTableCount = jdbc.queryForObject(
        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'eve_sessions'",
        Integer.class);
    Integer messageTableCount = jdbc.queryForObject(
        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'eve_messages'",
        Integer.class);
    Integer traceTableCount = jdbc.queryForObject(
        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'eve_trace_events'",
        Integer.class);
    Integer memoryTableCount = jdbc.queryForObject(
        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'eve_memory'",
        Integer.class);

    assertThat(sessionTableCount).isEqualTo(1);
    assertThat(messageTableCount).isEqualTo(1);
    assertThat(traceTableCount).isEqualTo(1);
    assertThat(memoryTableCount).isEqualTo(1);

    // 2. Insert test employee into canonical table
    Employee emp = new Employee();
    emp.employeeCode = "SA-99";
    emp.firstName = "Raj";
    emp.lastName = "Sharma";
    emp.displayName = "Raj Sharma";
    emp.roleTitle = "Audio Lead";
    emp.department = "Sound";
    emp.employmentType = "FULL_TIME";
    emp.joiningDate = LocalDate.now();
    emp.baseSalaryMinor = 5000000L;
    emp.salaryCurrency = "INR";
    emp.status = Employee.Status.ACTIVE;
    emp.phone = "+919876543210";
    emp = employeeRepo.saveAndFlush(emp);

    // Snapshot counts of canonical business tables before Eve query
    int initialEmployeeCount = jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class);
    int initialProdCount = jdbc.queryForObject("SELECT count(*) FROM productions", Integer.class);
    int initialMemberCount = jdbc.queryForObject("SELECT count(*) FROM production_members", Integer.class);
    int initialTxCount = jdbc.queryForObject("SELECT count(*) FROM finance_transactions", Integer.class);
    int initialTaskCount = jdbc.queryForObject("SELECT count(*) FROM tasks", Integer.class);

    // 3. Test Eve explicit memory learning against real PostgreSQL
    EveDtos.MemoryRequest memReq = new EveDtos.MemoryRequest(
        "VOCABULARY", "Raju", "EMPLOYEE", emp.id, "Raj Sharma");
    EveDtos.MemoryView savedMem = memoryService.remember(memReq, "OPERATOR_EXPLICIT");
    assertThat(savedMem.term()).isEqualTo("Raju");

    var recalled = memoryService.recall("raju");
    assertThat(recalled).isPresent();
    assertThat(recalled.get().canonicalName()).isEqualTo("Raj Sharma");

    int memoryRowsAfterExplicit = jdbc.queryForObject("SELECT count(*) FROM eve_memory", Integer.class);

    // 4. Execute Eve natural language query
    EveDtos.QueryResponse response = eveService.query(
        new EveDtos.QueryRequest("How much does Sharma still need?", null));

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.message().content()).contains("Raj Sharma (SA-99)");
    assertThat(response.trace()).isNotEmpty();

    // Turn 2: Querying Raju using explicit memory hint
    EveDtos.QueryResponse turn2 = eveService.query(
        new EveDtos.QueryRequest("How much does Raju still need?", response.sessionId()));
    assertThat(turn2.status()).isEqualTo("COMPLETED");
    assertThat(turn2.message().content()).contains("Raj Sharma (SA-99)");

    // STRICT INVARIANT: Implicit query must NEVER create new rows in eve_memory
    int memoryRowsAfterQuery = jdbc.queryForObject("SELECT count(*) FROM eve_memory", Integer.class);
    assertThat(memoryRowsAfterQuery).isEqualTo(memoryRowsAfterExplicit);

    // 5. Verify Eve persistence records were written in EVE-owned tables
    Integer sessionRows = jdbc.queryForObject("SELECT count(*) FROM eve_sessions", Integer.class);
    Integer messageRows = jdbc.queryForObject("SELECT count(*) FROM eve_messages", Integer.class);
    Integer traceRows = jdbc.queryForObject("SELECT count(*) FROM eve_trace_events", Integer.class);

    assertThat(sessionRows).isGreaterThan(0);
    assertThat(messageRows).isGreaterThan(0);
    assertThat(traceRows).isGreaterThan(0);

    // Verify trace events contain NO private reasoning, chain-of-thought, or secrets
    List<String> traceDetails = jdbc.query(
        "SELECT detail FROM eve_trace_events WHERE session_id = ?",
        (rs, rowNum) -> rs.getString("detail"),
        response.sessionId());
    for (String detail : traceDetails) {
      if (detail != null) {
        assertThat(detail).doesNotContain("chain-of-thought");
        assertThat(detail).doesNotContain("prompt");
        assertThat(detail).doesNotContain("password");
        assertThat(detail).doesNotContain("secret");
      }
    }

    // 6. STRICT INVARIANT: Verify ZERO mutations to canonical business tables occurred
    assertThat(jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class)).isEqualTo(initialEmployeeCount);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM productions", Integer.class)).isEqualTo(initialProdCount);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM production_members", Integer.class)).isEqualTo(initialMemberCount);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_transactions", Integer.class)).isEqualTo(initialTxCount);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks", Integer.class)).isEqualTo(initialTaskCount);
  }

  @Test
  void verifiesGovernedExecutionEmployeePaymentVerticalSliceInPostgres() {
    // 1. Verify V029 Flyway migration created eve_plans and eve_plan_actions in PostgreSQL
    Integer planTableCount = jdbc.queryForObject(
        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'eve_plans'",
        Integer.class);
    Integer planActionsTableCount = jdbc.queryForObject(
        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'eve_plan_actions'",
        Integer.class);
    assertThat(planTableCount).isEqualTo(1);
    assertThat(planActionsTableCount).isEqualTo(1);

    // 2. Set up test employee with canonical obligation in PostgreSQL
    Employee emp = new Employee();
    emp.employeeCode = "SA-PAY-1";
    emp.firstName = "Sunil";
    emp.lastName = "Gavaskar";
    emp.displayName = "Sunil Gavaskar";
    emp.roleTitle = "Senior Cinematographer";
    emp.department = "Camera";
    emp.employmentType = "FULL_TIME";
    emp.joiningDate = LocalDate.now();
    emp.baseSalaryMinor = 6000000L;
    emp.salaryCurrency = "INR";
    emp.status = Employee.Status.ACTIVE;
    emp.phone = "+919876543211";
    emp = employeeRepo.saveAndFlush(emp);

    UUID obligationId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO finance_employee_obligations(id, employee_id, obligation_type, effective_date, gross_amount, approved_deductions, adjustment, description) VALUES(?,?,'WORK_EARNING',CURRENT_DATE,5000.00,0,0,'Commercial Shoot')",
        obligationId, emp.id);

    // Read initial owner position for AZ-2
    UUID az2Id = jdbc.queryForObject("SELECT id FROM finance_accounts WHERE code = 'AZ-2'", UUID.class);
    BigDecimal initialAz2Pos = jdbc.queryForObject(
        "SELECT position FROM finance_account_positions WHERE account_id = ?",
        BigDecimal.class, az2Id);
    if (initialAz2Pos == null) initialAz2Pos = BigDecimal.ZERO;

    int initialTxCount = jdbc.queryForObject("SELECT count(*) FROM finance_transactions", Integer.class);
    int initialAllocCount = jdbc.queryForObject("SELECT count(*) FROM finance_employee_payment_allocations", Integer.class);

    // 3. Step 1: Mamu asks EVE: "Sunil ko 3000 de do"
    EveDtos.QueryResponse proposalResp = eveService.query(
        new EveDtos.QueryRequest("Sunil ko 3000 de do", null));

    assertThat(proposalResp.status()).isEqualTo("WAITING_CONFIRMATION");
    assertThat(proposalResp.plan()).isNotNull();
    EveDtos.EvePlan plan = proposalResp.plan();
    assertThat(plan.status()).isEqualTo("PROPOSED");
    assertThat(plan.riskTier()).isEqualTo(EveDtos.RiskTier.FINANCIAL_WRITE);
    assertThat(plan.actions()).hasSize(1);
    assertThat(plan.actions().getFirst().commandType()).isEqualTo("RECORD_EMPLOYEE_PAYMENT");

    // CRITICAL INVARIANT: Proposing a plan must NOT mutate PostgreSQL business tables
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_transactions", Integer.class)).isEqualTo(initialTxCount);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM finance_employee_payment_allocations WHERE obligation_id = ?", Integer.class, obligationId)).isEqualTo(0);
    assertThat(jdbc.queryForObject(
        "SELECT count(*) FROM finance_employee_payment_allocations", Integer.class)).isEqualTo(initialAllocCount);

    // Verify plan is persisted in eve_plans and eve_plan_actions
    Integer planRows = jdbc.queryForObject(
        "SELECT count(*) FROM eve_plans WHERE id = ?", Integer.class, plan.planId());
    Integer actionRows = jdbc.queryForObject(
        "SELECT count(*) FROM eve_plan_actions WHERE plan_id = ?", Integer.class, plan.planId());
    assertThat(planRows).isEqualTo(1);
    assertThat(actionRows).isEqualTo(1);

    // 4. Step 2: Mamu explicitly confirms the proposed plan
    EveDtos.ConfirmPlanRequest confirmReq = new EveDtos.ConfirmPlanRequest(
        proposalResp.sessionId(), plan.planId(), 1, plan.planHash(), "Approved by Mamu");

    EveDtos.PlanExecutionResponse execResp = eveService.confirmPlan(plan.planId(), confirmReq);

    assertThat(execResp.status()).isEqualTo("COMPLETED");
    assertThat(execResp.actions()).hasSize(1);
    assertThat(execResp.actions().getFirst().status()).isEqualTo("VERIFIED");

    UUID txnId = execResp.actions().getFirst().canonicalRecordId();
    assertThat(txnId).isNotNull();

    // 5. Post-Execution Authoritative Verification in PostgreSQL
    // a. finance_transactions row exists with status POSTED and amount 3000.00
    var txnRow = jdbc.queryForMap(
        "SELECT transaction_type, status, amount, employee_id FROM finance_transactions WHERE id = ?", txnId);
    assertThat(txnRow.get("transaction_type")).isEqualTo("EMPLOYEE_PAYMENT");
    assertThat(txnRow.get("status")).isEqualTo("POSTED");
    assertThat(((BigDecimal) txnRow.get("amount")).compareTo(new BigDecimal("3000.00"))).isZero();
    assertThat(txnRow.get("employee_id")).isEqualTo(emp.id);

    // b. Allocation created in finance_employee_payment_allocations
    BigDecimal allocated = jdbc.queryForObject(
        "SELECT sum(amount) FROM finance_employee_payment_allocations WHERE transaction_id = ?",
        BigDecimal.class, txnId);
    assertThat(allocated.compareTo(new BigDecimal("3000.00"))).isZero();

    // c. Updated employee outstanding balance in PostgreSQL is exactly 2000.00 (5000 - 3000)
    BigDecimal currentPaid = jdbc.queryForObject(
        "SELECT coalesce(sum(a.amount),0) FROM finance_employee_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.obligation_id=? AND t.status='POSTED'",
        BigDecimal.class, obligationId);
    assertThat(currentPaid.compareTo(new BigDecimal("3000.00"))).isZero();

    // d. Payer position reduced by exactly 3000.00
    BigDecimal newAz2Pos = jdbc.queryForObject(
        "SELECT position FROM finance_account_positions WHERE account_id = ?",
        BigDecimal.class, az2Id);
    assertThat(newAz2Pos).isEqualTo(initialAz2Pos.subtract(new BigDecimal("3000.00")));

    // e. Audit event recorded in finance_audit_events
    Integer auditRows = jdbc.queryForObject(
        "SELECT count(*) FROM finance_audit_events WHERE transaction_id = ? AND action = 'POSTED'",
        Integer.class, txnId);
    assertThat(auditRows).isGreaterThan(0);

    // f. Plan and action status updated to COMPLETED and VERIFIED in PostgreSQL
    String planDbStatus = jdbc.queryForObject(
        "SELECT status FROM eve_plans WHERE id = ?", String.class, plan.planId());
    String actionDbStatus = jdbc.queryForObject(
        "SELECT status FROM eve_plan_actions WHERE plan_id = ?", String.class, plan.planId());
    assertThat(planDbStatus).isEqualTo("COMPLETED");
    assertThat(actionDbStatus).isEqualTo("VERIFIED");

    // 6. Idempotency test: Re-submitting the exact same confirmation must return completed response without double payment
    int txnCountAfterFirstExec = jdbc.queryForObject("SELECT count(*) FROM finance_transactions", Integer.class);
    EveDtos.PlanExecutionResponse retryResp = eveService.confirmPlan(plan.planId(), confirmReq);
    assertThat(retryResp.status()).isEqualTo("COMPLETED");
    int txnCountAfterRetry = jdbc.queryForObject("SELECT count(*) FROM finance_transactions", Integer.class);
    assertThat(txnCountAfterRetry).isEqualTo(txnCountAfterFirstExec);
  }

  @Test
  void concurrentPlanConfirmationExecutesExactlyOnceWithOneFinancialEffect() throws Exception {
    Employee emp = new Employee();
    emp.employeeCode = "SA-CONC-" + UUID.randomUUID().toString().substring(0, 6);
    emp.firstName = "Vipin";
    emp.lastName = "Verma";
    emp.displayName = "Vipin Verma";
    emp.roleTitle = "Lighting Lead";
    emp.department = "Lighting";
    emp.employmentType = "FULL_TIME";
    emp.joiningDate = LocalDate.now();
    emp.baseSalaryMinor = 6000000L;
    emp.salaryCurrency = "INR";
    emp.status = Employee.Status.ACTIVE;
    emp.phone = "+9198765432" + (int)(Math.random() * 90 + 10);
    emp = employeeRepo.saveAndFlush(emp);

    UUID obligationId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO finance_employee_obligations(id, employee_id, obligation_type, effective_date, gross_amount, approved_deductions, adjustment, description) VALUES(?,?,'WORK_EARNING',CURRENT_DATE,8000.00,0,0,'Gala Night')",
        obligationId, emp.id);

    EveDtos.QueryResponse proposal = eveService.query(
        new EveDtos.QueryRequest("Pay Vipin 3000", null));
    assertThat(proposal.status()).isEqualTo("WAITING_CONFIRMATION");
    assertThat(proposal.plan()).isNotNull();
    EveDtos.EvePlan plan = proposal.plan();

    EveDtos.ConfirmPlanRequest confirmReq = new EveDtos.ConfirmPlanRequest(
        proposal.sessionId(), plan.planId(), 1, plan.planHash(), "Concurrent test approval");

    int initialTxnCount = jdbc.queryForObject(
        "SELECT count(*) FROM finance_transactions WHERE employee_id = ?", Integer.class, emp.id);

    List<EveDtos.PlanExecutionResponse> responses = new CopyOnWriteArrayList<>();
    List<Throwable> errors = new CopyOnWriteArrayList<>();

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Callable<Void> task = () -> {
        try {
          EveDtos.PlanExecutionResponse resp = eveService.confirmPlan(plan.planId(), confirmReq);
          responses.add(resp);
        } catch (Throwable t) {
          errors.add(t);
        }
        return null;
      };

      for (Future<Void> f : pool.invokeAll(List.of(task, task))) {
        f.get();
      }
    }

    // Both invocations must succeed (one performs execution, the concurrent retry sees COMPLETED and returns cached result)
    assertThat(errors).isEmpty();
    assertThat(responses).hasSize(2);
    for (EveDtos.PlanExecutionResponse r : responses) {
      assertThat(r.status()).isEqualTo("COMPLETED");
    }

    // CRITICAL: Exactly ONE canonical transaction must be created in PostgreSQL
    int finalTxnCount = jdbc.queryForObject(
        "SELECT count(*) FROM finance_transactions WHERE employee_id = ?", Integer.class, emp.id);
    assertThat(finalTxnCount).isEqualTo(initialTxnCount + 1);

    // Exactly ₹3,000 allocated for this obligation
    BigDecimal totalAllocated = jdbc.queryForObject(
        "SELECT coalesce(sum(amount),0) FROM finance_employee_payment_allocations WHERE obligation_id = ?",
        BigDecimal.class, obligationId);
    assertThat(totalAllocated.compareTo(new BigDecimal("3000.00"))).isZero();
  }

  @Test
  void stalePlanProducesZeroFinancialEffectAndAllowsFreshPlan() {
    Employee emp = new Employee();
    emp.employeeCode = "SA-STALE-" + UUID.randomUUID().toString().substring(0, 6);
    emp.firstName = "Karan";
    emp.lastName = "Malhotra";
    emp.displayName = "Karan Malhotra";
    emp.roleTitle = "Stage Designer";
    emp.department = "Production";
    emp.employmentType = "FULL_TIME";
    emp.joiningDate = LocalDate.now();
    emp.baseSalaryMinor = 6000000L;
    emp.salaryCurrency = "INR";
    emp.status = Employee.Status.ACTIVE;
    emp.phone = "+9198765433" + (int)(Math.random() * 90 + 10);
    emp = employeeRepo.saveAndFlush(emp);

    UUID obligationId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO finance_employee_obligations(id, employee_id, obligation_type, effective_date, gross_amount, approved_deductions, adjustment, description) VALUES(?,?,'WORK_EARNING',CURRENT_DATE,5000.00,0,0,'Set Design')",
        obligationId, emp.id);

    // 1. Propose plan P1 for ₹4,000 against ₹5,000 balance
    EveDtos.QueryResponse prop1 = eveService.query(
        new EveDtos.QueryRequest("Pay Karan 4000", null));
    assertThat(prop1.status()).isEqualTo("WAITING_CONFIRMATION");
    EveDtos.EvePlan plan1 = prop1.plan();
    assertThat(plan1.status()).isEqualTo("PROPOSED");

    // 2. Canonical state changes outside EVE: gross_amount reduced to 2000.00
    jdbc.update("UPDATE finance_employee_obligations SET gross_amount = 2000.00 WHERE id = ?", obligationId);

    int txnCountBeforeStaleConfirm = jdbc.queryForObject(
        "SELECT count(*) FROM finance_transactions WHERE employee_id = ?", Integer.class, emp.id);

    // 3. Confirming old plan P1 must fail with STALE_PLAN / PRECONDITIONS CHANGED
    EveDtos.ConfirmPlanRequest confirmReq1 = new EveDtos.ConfirmPlanRequest(
        prop1.sessionId(), plan1.planId(), 1, plan1.planHash(), "Approve P1");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> eveService.confirmPlan(plan1.planId(), confirmReq1))
        .isInstanceOf(com.saproduction.command.shared.ApiException.class);

    // 4. Assert old plan produced ZERO financial transactions in PostgreSQL
    int txnCountAfterStaleConfirm = jdbc.queryForObject(
        "SELECT count(*) FROM finance_transactions WHERE employee_id = ?", Integer.class, emp.id);
    assertThat(txnCountAfterStaleConfirm).isEqualTo(txnCountBeforeStaleConfirm);

    String plan1DbStatus = jdbc.queryForObject("SELECT status FROM eve_plans WHERE id = ?", String.class, plan1.planId());
    assertThat(plan1DbStatus).isEqualTo("STALE_PLAN");

    // 5. New plan P2 created against the fresh balance of ₹2,000
    EveDtos.QueryResponse prop2 = eveService.query(
        new EveDtos.QueryRequest("Pay Karan 1500", prop1.sessionId()));
    assertThat(prop2.status()).isEqualTo("WAITING_CONFIRMATION");
    EveDtos.EvePlan plan2 = prop2.plan();

    // 6. Confirm and execute P2
    EveDtos.ConfirmPlanRequest confirmReq2 = new EveDtos.ConfirmPlanRequest(
        prop2.sessionId(), plan2.planId(), 1, plan2.planHash(), "Approve P2");

    EveDtos.PlanExecutionResponse execResp2 = eveService.confirmPlan(plan2.planId(), confirmReq2);
    assertThat(execResp2.status()).isEqualTo("COMPLETED");
    assertThat(execResp2.actions().getFirst().status()).isEqualTo("VERIFIED");

    // Assert exactly ONE financial transaction was created in PostgreSQL (from P2, zero from P1)
    int txnCountFinal = jdbc.queryForObject(
        "SELECT count(*) FROM finance_transactions WHERE employee_id = ?", Integer.class, emp.id);
    assertThat(txnCountFinal).isEqualTo(txnCountBeforeStaleConfirm + 1);

    // Allocations for P2 equal 1500.00
    BigDecimal allocatedP2 = jdbc.queryForObject(
        "SELECT sum(amount) FROM finance_employee_payment_allocations WHERE obligation_id = ?",
        BigDecimal.class, obligationId);
    assertThat(allocatedP2.compareTo(new BigDecimal("1500.00"))).isZero();
  }

  @Autowired EveSignalService signalService;
  @Autowired EveSuggestionService suggestionService;
  @Autowired EveObserverService observerService;
  @Autowired FinancePostingService financePostingService;
  @Autowired FinanceReadService financeReadService;
  @Autowired AuditService auditService;
  @Autowired PlatformTransactionManager transactionManager;

  @Test
  void verifiesAllEveTablesConstraintsIndexesExistInFreshPostgres() {
    List<String> expectedTables = List.of(
        "eve_sessions", "eve_messages", "eve_trace_events", "eve_memory", "eve_plans", "eve_plan_actions",
        "eve_signals", "eve_suggestions");
    for (String table : expectedTables) {
      Integer count = jdbc.queryForObject(
          "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
          Integer.class, table);
      assertThat(count).as("Table " + table + " must exist in fresh schema").isEqualTo(1);
    }

    // Check unique constraints
    List<String> uniqueConstraints = jdbc.queryForList(
        "SELECT conname FROM pg_constraint WHERE contype = 'u' AND connamespace = 'public'::regnamespace",
        String.class);
    assertThat(uniqueConstraints).contains("eve_memory_term_uniq", "uq_eve_plan_actions_plan_seq");

    // Check foreign keys
    List<String> fkeys = jdbc.queryForList(
        "SELECT conname FROM pg_constraint WHERE contype = 'f' AND connamespace = 'public'::regnamespace",
        String.class);
    assertThat(fkeys).contains(
        "eve_messages_session_id_fkey",
        "eve_trace_events_session_id_fkey",
        "eve_plans_session_id_fkey",
        "eve_plan_actions_plan_id_fkey",
        "eve_suggestions_source_signal_id_fkey");

    // Check indexes
    List<String> indexes = jdbc.queryForList(
        "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'",
        String.class);
    assertThat(indexes).contains(
        "eve_messages_session_idx",
        "eve_trace_session_idx",
        "eve_sessions_updated_idx",
        "eve_memory_term_idx",
        "eve_plans_session_idx",
        "eve_plans_status_idx",
        "eve_plan_actions_plan_idx",
        "eve_plan_actions_idempotency_idx",
        "eve_plan_actions_canonical_idx",
        "eve_signals_type_idx",
        "eve_signals_entity_idx",
        "eve_signals_occurred_idx",
        "eve_suggestions_status_idx",
        "eve_suggestions_entity_idx",
        "eve_suggestions_dedupe_idx",
        "eve_suggestions_type_idx",
        "uq_eve_active_suggestion_dedupe");
  }

  @Test
  void verifiesContinuousIntelligenceSignalAndSuggestionLifecycleInPostgres() {
    UUID empId = UUID.randomUUID();
    // 1. Emit typed signal
    EveDtos.SignalView signal = signalService.emit(new EveDtos.EmitSignalRequest(
        EveSignalTypes.EMPLOYEE_PAYMENT_POSTED,
        "FINANCE",
        "EMPLOYEE",
        empId,
        1,
        "corr-pg-test-1",
        Map.of("note", "Postgres integration test signal")
    ));

    assertThat(signal).isNotNull();
    assertThat(signal.status()).isEqualTo("EVALUATED");

    // Verify row persisted in real PostgreSQL eve_signals table
    Integer count = jdbc.queryForObject(
        "SELECT count(*) FROM eve_signals WHERE id = ?", Integer.class, signal.id());
    assertThat(count).isEqualTo(1);

    // 2. Direct suggestion insertion and partial unique index validation
    String dedupeKey = "OUTSTANDING_EMPLOYEE_PAYMENT:" + empId;
    UUID s1Id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO eve_suggestions (
          id, type, status, priority, title, summary, source_signal_id, target_domain,
          canonical_entity_type, canonical_entity_id, canonical_entity_name, evidence, dedupe_key
        ) VALUES (?, 'OUTSTANDING_EMPLOYEE_PAYMENT', 'ACTIVE', 'HIGH', 'Test Title', 'Test Summary',
          ?, 'FINANCE', 'EMPLOYEE', ?, 'Test Emp', '[]'::jsonb, ?)
        """, s1Id, signal.id(), empId, dedupeKey);

    // Attempting to insert a second ACTIVE suggestion with the exact same dedupe_key MUST violate uq_eve_active_suggestion_dedupe
    assertThatThrownBy(() -> {
      jdbc.update("""
          INSERT INTO eve_suggestions (
            id, type, status, priority, title, summary, source_signal_id, target_domain,
            canonical_entity_type, canonical_entity_id, canonical_entity_name, evidence, dedupe_key
          ) VALUES (?, 'OUTSTANDING_EMPLOYEE_PAYMENT', 'ACTIVE', 'HIGH', 'Duplicate', 'Duplicate',
            ?, 'FINANCE', 'EMPLOYEE', ?, 'Test Emp', '[]'::jsonb, ?)
          """, UUID.randomUUID(), signal.id(), empId, dedupeKey);
    }).isInstanceOf(Exception.class);

    // 3. Dismiss suggestion via service
    EveDtos.SuggestionView dismissed = suggestionService.dismissSuggestion(s1Id, "Reviewed");
    assertThat(dismissed.status()).isEqualTo("DISMISSED");

    // 4. Now that previous suggestion is DISMISSED, inserting new ACTIVE with same dedupe_key succeeds
    UUID s2Id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO eve_suggestions (
          id, type, status, priority, title, summary, source_signal_id, target_domain,
          canonical_entity_type, canonical_entity_id, canonical_entity_name, evidence, dedupe_key
        ) VALUES (?, 'OUTSTANDING_EMPLOYEE_PAYMENT', 'ACTIVE', 'HIGH', 'Test Title 2', 'Test Summary 2',
          ?, 'FINANCE', 'EMPLOYEE', ?, 'Test Emp', '[]'::jsonb, ?)
        """, s2Id, signal.id(), empId, dedupeKey);

    // 5. Resolve suggestion
    EveDtos.SuggestionView resolved = suggestionService.resolveSuggestion(s2Id, "Paid in full");
    assertThat(resolved.status()).isEqualTo("RESOLVED");
  }

  private Employee createTestEmployee(String suffix) {
    Employee emp = new Employee();
    emp.employeeCode = "SA-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6);
    emp.firstName = "Test";
    emp.lastName = "Worker-" + suffix;
    emp.displayName = "Test Worker " + suffix;
    emp.roleTitle = "Technician";
    emp.department = "Production";
    emp.employmentType = "FULL_TIME";
    emp.joiningDate = LocalDate.now();
    emp.baseSalaryMinor = 5000000L;
    emp.salaryCurrency = "INR";
    emp.status = Employee.Status.ACTIVE;
    emp.phone = "+91987654" + (int)(Math.random() * 9000 + 1000);
    return employeeRepo.saveAndFlush(emp);
  }

  @Test
  void realCanonicalMutation_automaticallyProducesEveSignalAndSuggestion_withoutManualEveCall() {
    Employee emp = createTestEmployee("P4AUTO");

    // Pure canonical mutation via FinancePostingService.earning - ZERO manual EVE endpoint call!
    financePostingService.earning(new FinanceCommands.Earning(
        UUID.randomUUID(), emp.id, null, new BigDecimal("5500.00"), LocalDate.now(), "Stage lighting installation"));

    // 1. Verify signal automatically created in PostgreSQL eve_signals table via AFTER_COMMIT
    Integer signalCount = jdbc.queryForObject(
        "SELECT count(*) FROM eve_signals WHERE canonical_entity_id = ? AND signal_type = 'EMPLOYEE_EARNING_CREATED'",
        Integer.class, emp.id);
    assertThat(signalCount).isGreaterThanOrEqualTo(1);

    // 2. Verify active suggestion automatically created in PostgreSQL eve_suggestions table
    List<Map<String, Object>> suggestions = jdbc.queryForList(
        "SELECT id, type, status, priority, title, summary, evidence::text as evidence_text FROM eve_suggestions WHERE canonical_entity_id = ? AND status = 'ACTIVE'",
        emp.id);
    assertThat(suggestions).hasSize(1);
    var s = suggestions.getFirst();
    assertThat(s.get("type")).isEqualTo("OUTSTANDING_EMPLOYEE_PAYMENT");
    assertThat(s.get("priority")).isEqualTo("HIGH");
    assertThat((String) s.get("evidence_text")).contains("5500.00");
  }

  @Test
  void canonicalMutationRollback_producesZeroSignalsAndZeroSuggestions() {
    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
    Employee emp = createTestEmployee("P4ROLLBACK");

    assertThatThrownBy(() -> {
      txTemplate.execute(status -> {
        // Attempt a financial posting inside a transaction that fails
        financePostingService.earning(new FinanceCommands.Earning(
            UUID.randomUUID(), emp.id, null, new BigDecimal("7000.00"), LocalDate.now(), "Uncommitted Earning"));
        status.setRollbackOnly();
        throw new RuntimeException("Simulated transaction failure forcing rollback");
      });
    }).isInstanceOf(RuntimeException.class);

    // Assert: AFTER_COMMIT was NOT triggered, transaction rolled back
    // ZERO records in eve_signals and ZERO records in eve_suggestions for this employee
    Integer signals = jdbc.queryForObject("SELECT count(*) FROM eve_signals WHERE canonical_entity_id = ?", Integer.class, emp.id);
    Integer suggestions = jdbc.queryForObject("SELECT count(*) FROM eve_suggestions WHERE canonical_entity_id = ?", Integer.class, emp.id);
    assertThat(signals).isZero();
    assertThat(suggestions).isZero();
  }

  @Test
  void realCanonicalMutation_automaticallyResolvesActiveSuggestion_withoutManualEveCall() {
    Employee emp = createTestEmployee("P4RESOLVE");

    // 1. Create initial earning -> produces active suggestion
    financePostingService.earning(new FinanceCommands.Earning(
        UUID.randomUUID(), emp.id, null, new BigDecimal("3500.00"), LocalDate.now(), "Initial Audio Rigging"));

    Integer activeBefore = jdbc.queryForObject(
        "SELECT count(*) FROM eve_suggestions WHERE canonical_entity_id = ? AND status = 'ACTIVE'",
        Integer.class, emp.id);
    assertThat(activeBefore).isEqualTo(1);

    // 2. Now post full employee payment mutation through canonical FinancePostingService
    financePostingService.employeePayment(new FinanceCommands.EmployeePayment(
        UUID.randomUUID(), emp.id, new BigDecimal("3500.00"), LocalDate.now(), "Full settlement", "AZ-2"));

    // 3. Verify suggestion automatically transitioned to RESOLVED with resolved_at populated
    Integer activeAfter = jdbc.queryForObject(
        "SELECT count(*) FROM eve_suggestions WHERE canonical_entity_id = ? AND status = 'ACTIVE'",
        Integer.class, emp.id);
    assertThat(activeAfter).isZero();

    List<Map<String, Object>> resolvedList = jdbc.queryForList(
        "SELECT status, resolved_at FROM eve_suggestions WHERE canonical_entity_id = ? AND status = 'RESOLVED'",
        emp.id);
    assertThat(resolvedList).hasSize(1);
    assertThat(resolvedList.getFirst().get("resolved_at")).isNotNull();
  }

  @Test
  void staleEvidence_updatesInPlaceWhenCanonicalStateDrifts() {
    Employee emp = createTestEmployee("P4STALE");

    // 1. Initial earning ₹2,000 (MEDIUM priority)
    financePostingService.earning(new FinanceCommands.Earning(
        UUID.randomUUID(), emp.id, null, new BigDecimal("2000.00"), LocalDate.now(), "Day 1 Shift"));

    var s1 = jdbc.queryForMap(
        "SELECT id, priority, evidence::text as evidence_text FROM eve_suggestions WHERE canonical_entity_id = ? AND status = 'ACTIVE'",
        emp.id);
    assertThat(s1.get("priority")).isEqualTo("MEDIUM");
    assertThat((String) s1.get("evidence_text")).contains("2000.00");
    UUID initialSuggestionId = (UUID) s1.get("id");

    // 2. Additional earning ₹4,000 (total ₹6,000 -> HIGH priority)
    financePostingService.earning(new FinanceCommands.Earning(
        UUID.randomUUID(), emp.id, null, new BigDecimal("4000.00"), LocalDate.now(), "Day 2 Shift"));

    // 3. Verify exactly ONE active suggestion remains (same ID updated in place)
    List<Map<String, Object>> activeSuggestions = jdbc.queryForList(
        "SELECT id, priority, evidence::text as evidence_text FROM eve_suggestions WHERE canonical_entity_id = ? AND status = 'ACTIVE'",
        emp.id);
    assertThat(activeSuggestions).hasSize(1);
    var s2 = activeSuggestions.getFirst();
    assertThat(s2.get("id")).isEqualTo(initialSuggestionId);
    assertThat(s2.get("priority")).isEqualTo("HIGH");
    assertThat((String) s2.get("evidence_text")).contains("6000.00");
    assertThat((String) s2.get("evidence_text")).doesNotContain("2000.00");
  }

  @Test
  void cooldown_suppressesImmediateRepeat_andAllowsAfterExpiryInPostgres() {
    Employee emp = createTestEmployee("P4COOL");
    financePostingService.earning(new FinanceCommands.Earning(
        UUID.randomUUID(), emp.id, null, new BigDecimal("2500.00"), LocalDate.now(), "Consulting Work"));

    UUID suggId = jdbc.queryForObject(
        "SELECT id FROM eve_suggestions WHERE canonical_entity_id = ? AND status = 'ACTIVE'",
        UUID.class, emp.id);

    // Dismiss suggestion -> 24h cooldown active
    suggestionService.dismissSuggestion(suggId, "Not paying now");

    // Immediate repeat evaluation
    observerService.evaluateEmployeePayable(emp.id, null);
    Integer activeDuringCooldown = jdbc.queryForObject(
        "SELECT count(*) FROM eve_suggestions WHERE canonical_entity_id = ? AND status = 'ACTIVE'",
        Integer.class, emp.id);
    assertThat(activeDuringCooldown).isZero(); // Suppressed!

    // Fast-forward dismissed_at to 25 hours ago
    jdbc.update("UPDATE eve_suggestions SET dismissed_at = now() - interval '25 hours' WHERE id = ?", suggId);

    // Next evaluation or mutation -> Cooldown expired!
    observerService.evaluateEmployeePayable(emp.id, null);
    Integer activeAfterCooldown = jdbc.queryForObject(
        "SELECT count(*) FROM eve_suggestions WHERE canonical_entity_id = ? AND status = 'ACTIVE'",
        Integer.class, emp.id);
    assertThat(activeAfterCooldown).isEqualTo(1); // Created again!
  }

  @Test
  void spoofedSignalPayload_cannotFabricateBusinessTruth_inPostgres() {
    Employee emp = createTestEmployee("P4SPOOF");
    // Real balance in PostgreSQL is 0

    // Attempt spoofed signal with fake metadata claiming huge balance
    signalService.emit(new EveDtos.EmitSignalRequest(
        EveSignalTypes.EMPLOYEE_EARNING_CREATED,
        "FINANCE",
        "EMPLOYEE",
        emp.id,
        1,
        "spoof-" + UUID.randomUUID(),
        Map.of("fake_amount", 999999999, "fake_status", "URGENT")
    ));

    // Observer must check PostgreSQL canonical truth (FinanceReadService), see 0 balance, and create ZERO suggestions
    Integer count = jdbc.queryForObject(
        "SELECT count(*) FROM eve_suggestions WHERE canonical_entity_id = ? AND status = 'ACTIVE'",
        Integer.class, emp.id);
    assertThat(count).isZero();
  }

  @Test
  void verifiesSharmaWeddingRealPostgresFlow_andAmbiguityClarification() {
    // 1. Seed real employee in PostgreSQL
    Employee crewEmp = new Employee();
    crewEmp.employeeCode = "SA-CREW-99";
    crewEmp.firstName = "Rahul";
    crewEmp.lastName = "Verma";
    crewEmp.displayName = "Rahul Verma";
    crewEmp.roleTitle = "Lead Audio Engineer";
    crewEmp.department = "Sound";
    crewEmp.employmentType = "FULL_TIME";
    crewEmp.joiningDate = LocalDate.now();
    crewEmp.baseSalaryMinor = 4500000L;
    crewEmp.salaryCurrency = "INR";
    crewEmp.status = Employee.Status.ACTIVE;
    crewEmp.phone = "+919876543299";
    crewEmp = employeeRepo.saveAndFlush(crewEmp);

    // 2. Seed real production in PostgreSQL: "Sharma Wedding"
    UUID weddingId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO productions (id, title, client_name, description, event_date, start_time, end_time, venue_name, status, priority, progress_percent) " +
        "VALUES (?, 'Sharma Wedding', 'Sharma Family', 'Grand wedding ceremony', CURRENT_DATE + 3, '10:00:00', '18:00:00', 'Jaipur Palace', 'PRODUCTION', 'HIGH', 50)",
        weddingId);

    // 3. Seed real crew member in production_members table in PostgreSQL
    UUID memberId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO production_members (id, production_id, employee_id, production_role, attendance_required, assignment_status, conflict_overridden) " +
        "VALUES (?, ?, ?, 'Lead Audio Engineer', true, 'CONFIRMED', false)",
        memberId, weddingId, crewEmp.id);

    // 4. Exercise Turn 1: "crew for sharma wedding"
    EveDtos.QueryResponse turn1 = eveService.query(new EveDtos.QueryRequest("crew for sharma wedding", null));
    assertThat(turn1.status()).isEqualTo("COMPLETED");
    assertThat(turn1.message().content()).contains("Sharma Wedding");
    assertThat(turn1.message().content()).contains("Rahul Verma (Lead Audio Engineer)");
    assertThat(turn1.message().content()).doesNotContain("Royal");
    assertThat(turn1.trace()).isNotEmpty();

    // 5. Exercise Turn 2: "shamra wedding ke event me kon gaya he" in the same session
    EveDtos.QueryResponse turn2 = eveService.query(new EveDtos.QueryRequest("shamra wedding ke event me kon gaya he", turn1.sessionId()));
    assertThat(turn2.status()).isEqualTo("COMPLETED");
    assertThat(turn2.message().content()).contains("Sharma Wedding");
    assertThat(turn2.message().content()).contains("Rahul Verma (Lead Audio Engineer)");
    assertThat(turn2.message().content()).doesNotContain("I couldn't find relevant records");

    // 6. Test Ambiguity & Clarification Invariant:
    // Seed a second production with "Sharma" in the title
    UUID receptionId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO productions (id, title, client_name, description, event_date, start_time, end_time, venue_name, status, priority, progress_percent) " +
        "VALUES (?, 'Sharma Sangeet & Reception', 'Sharma Family', 'Sangeet party', CURRENT_DATE + 2, '18:00:00', '23:00:00', 'Udaipur Resort', 'PLANNING', 'NORMAL', 20)",
        receptionId);

    // Now query with ambiguous prompt: "crew for sharma"
    EveDtos.QueryResponse ambigResp = eveService.query(new EveDtos.QueryRequest("crew for sharma", null));
    assertThat(ambigResp.status()).isEqualTo("CLARIFICATION_REQUIRED");
    assertThat(ambigResp.message().content()).contains("I found multiple matching productions");
    assertThat(ambigResp.candidates()).hasSize(2);
    assertThat(ambigResp.candidates().stream().map(EveDtos.CandidateView::displayName))
        .contains("Sharma Wedding", "Sharma Sangeet & Reception");

    // Disambiguation turn: User specifies "wedding wala"
    EveDtos.QueryResponse disambigTurn = eveService.query(
        new EveDtos.QueryRequest("wedding wala", ambigResp.sessionId()));
    assertThat(disambigTurn.status()).isEqualTo("COMPLETED");
    assertThat(disambigTurn.message().content()).contains("Sharma Wedding");
    assertThat(disambigTurn.message().content()).contains("Rahul Verma (Lead Audio Engineer)");
  }
}
