package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;

import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
        "SELECT count(*) FROM finance_employee_payment_allocations", Integer.class)).isEqualTo(0);

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

  @Test
  void verifiesAllEveTablesConstraintsIndexesExistInFreshPostgres() {
    List<String> expectedTables = List.of(
        "eve_sessions", "eve_messages", "eve_trace_events", "eve_memory", "eve_plans", "eve_plan_actions");
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
        "eve_plan_actions_plan_id_fkey");

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
        "eve_plan_actions_canonical_idx");
  }
}
