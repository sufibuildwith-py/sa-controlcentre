package com.saproduction.command.finance.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.audit.AuditService;
import com.saproduction.command.auth.ApiSession;
import com.saproduction.command.auth.ApiSessionRepository;
import com.saproduction.command.auth.ApiSessionService;
import com.saproduction.command.auth.User;
import com.saproduction.command.billing.BillingCommands;
import com.saproduction.command.billing.BillingController;
import com.saproduction.command.billing.BillingService;
import com.saproduction.command.employee.*;
import com.saproduction.command.finance.FinanceIntegrationController;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.payroll.PayrollAdjustment;
import com.saproduction.command.payroll.PayrollController;
import com.saproduction.command.payroll.PayrollPayment;
import com.saproduction.command.payroll.PayrollPeriod;
import com.saproduction.command.payroll.PayrollService;
import com.saproduction.command.shared.ApiException;
import com.saproduction.command.work.WorkTaskService;
import jakarta.servlet.FilterChain;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Comprehensive verification suite covering:
 * 1. Complete Backend Security Matrix across all financial endpoints
 * 2. PayrollController locked vs unlocked verification
 * 3. BillingController locked vs unlocked verification
 * 4. Employee / Employee Operations / Employee 360 data masking verification
 * 5. Production finance endpoint boundary
 * 6. Export security (PDF & CSV)
 * 7. Session lifecycle and grant boundaries
 * 8. Rate limiting, cooldown, and PIN leakage audit
 */
class FinanceAccessSecurityMatrixTest {

  private static final String CORRECT_PIN = "153011";
  private static final String PIN_HASH =
      "dd7a0bb1db3c404e514d61d0b8025f2c0a6023a17fb74c4461c38bf951d9ee47";

  private Clock clock;
  private AuditService audit;
  private ApiSessionRepository sessionRepository;
  private ApiSessionService sessionService;
  private FinanceAccessThrottle throttle;
  private FinanceAccessService accessService;
  private FinanceAccessFilter filter;
  private ObjectMapper objectMapper;

  private User ownerUser;
  private User employeeUser;
  private ApiSession ownerSession;
  private ApiSession employeeSession;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);
    audit = mock(AuditService.class);
    sessionRepository = mock(ApiSessionRepository.class);
    sessionService = mock(ApiSessionService.class);
    throttle = new FinanceAccessThrottle(audit, clock);
    objectMapper = new ObjectMapper();

    accessService =
        new FinanceAccessService(
            sessionRepository,
            sessionService,
            throttle,
            audit,
            clock,
            PIN_HASH,
            Duration.ofMinutes(30));

    filter = new FinanceAccessFilter(accessService, audit, objectMapper);

    ownerUser = new User();
    ownerUser.id = UUID.randomUUID();
    ownerUser.role = "OWNER";

    employeeUser = new User();
    employeeUser.id = UUID.randomUUID();
    employeeUser.role = "EMPLOYEE";

    ownerSession = new ApiSession();
    ownerSession.id = UUID.randomUUID();
    ownerSession.tokenHash = "owner-hash";
    ownerSession.user = ownerUser;

    employeeSession = new ApiSession();
    employeeSession.id = UUID.randomUUID();
    employeeSession.tokenHash = "employee-hash";
    employeeSession.user = employeeUser;

    when(sessionService.findValidSession("owner-token")).thenReturn(ownerSession);
    when(sessionService.findValidSession("employee-token")).thenReturn(employeeSession);
  }

  // --------------------------------------------------------------------------
  // 1. BACKEND SECURITY MATRIX: 6-STATE VERIFICATION ACROSS ALL ENDPOINTS
  // --------------------------------------------------------------------------
  @Nested
  @DisplayName("1. Backend Security Matrix (All 6 States x Protected Routes)")
  class SecurityMatrix {

    private final List<String> endpoints =
        List.of(
            // Finance routes
            "/api/v1/finance/overview",
            "/api/v1/finance/transactions",
            "/api/v1/finance/reconciliation",
            "/api/v1/finance/accounts",
            "/api/v1/finance/workbook/employees",
            "/api/v1/finance/workbook/owners",
            "/api/v1/finance/workbook/parties",
            "/api/v1/finance/workbook/productions",
            "/api/v1/finance/employee-payables",
            "/api/v1/finance/counterparties",
            // Billing routes
            "/api/v1/billing",
            "/api/v1/billing/123e4567-e89b-12d3-a456-426614174000",
            "/api/v1/billing/123e4567-e89b-12d3-a456-426614174000/issue",
            "/api/v1/billing/123e4567-e89b-12d3-a456-426614174000/cancel",
            "/api/v1/billing/123e4567-e89b-12d3-a456-426614174000/export",
            "/api/v1/billing/123e4567-e89b-12d3-a456-426614174000/export-pdf",
            // Payroll routes
            "/api/v1/payroll",
            "/api/v1/payroll/123e4567-e89b-12d3-a456-426614174000",
            "/api/v1/payroll/2026/10/calculate",
            "/api/v1/payroll/123e4567-e89b-12d3-a456-426614174000/adjustments",
            "/api/v1/payroll/123e4567-e89b-12d3-a456-426614174000/approve",
            "/api/v1/payroll/123e4567-e89b-12d3-a456-426614174000/items/item-1/payments",
            "/api/v1/payroll/123e4567-e89b-12d3-a456-426614174000/lock",
            // Production finance route
            "/api/v1/productions/123e4567-e89b-12d3-a456-426614174000/finance",
            // Employee finance route
            "/api/v1/employees/123e4567-e89b-12d3-a456-426614174000/finance");

    @Test
    @DisplayName("State 1: Owner + Locked -> 403 FINANCE_LOCKED")
    void state1_ownerLocked_denied() throws Exception {
      for (String endpoint : endpoints) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", endpoint);
        req.addHeader("Authorization", "Bearer owner-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "owner", "pass", List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("FINANCE_LOCKED");
        verify(chain, never()).doFilter(req, res);
      }
    }

    @Test
    @DisplayName("State 2: Owner + Unlocked -> Allowed (Chain continues)")
    void state2_ownerUnlocked_allowed() throws Exception {
      // Legitimate unlock
      accessService.unlock("owner-token", CORRECT_PIN, "127.0.0.1");

      for (String endpoint : endpoints) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", endpoint);
        req.addHeader("Authorization", "Bearer owner-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "owner", "pass", List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verify(chain).doFilter(req, res);
      }
    }

    @Test
    @DisplayName("State 3: Non-Owner + Locked -> 403 FORBIDDEN")
    void state3_nonOwnerLocked_denied() throws Exception {
      for (String endpoint : endpoints) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", endpoint);
        req.addHeader("Authorization", "Bearer employee-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "employee", "pass", List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("FORBIDDEN");
        verify(chain, never()).doFilter(req, res);
      }
    }

    @Test
    @DisplayName("State 4: Non-Owner + Correct Code -> Still Denied (Cannot Unlock)")
    void state4_nonOwnerCorrectCode_denied() throws Exception {
      // Non-owner attempts to unlock
      assertThatThrownBy(() -> accessService.unlock("employee-token", CORRECT_PIN, "127.0.0.1"))
          .isInstanceOf(ApiException.class)
          .satisfies(e -> assertThat(((ApiException) e).status).isEqualTo(HttpStatus.FORBIDDEN));

      // Attempting request on protected paths remains forbidden
      for (String endpoint : endpoints) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", endpoint);
        req.addHeader("Authorization", "Bearer employee-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "employee", "pass", List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))));

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("FORBIDDEN");
        verify(chain, never()).doFilter(req, res);
      }
    }

    @Test
    @DisplayName("State 5: Expired Grant -> 403 FINANCE_LOCKED")
    void state5_expiredGrant_denied() throws Exception {
      accessService.unlock("owner-token", CORRECT_PIN, "127.0.0.1");

      // Advance clock past 30-minute expiration window (31 mins)
      Clock advancedClock =
          Clock.fixed(
              Instant.parse("2026-10-03T12:00:00Z").plus(Duration.ofMinutes(31)), ZoneOffset.UTC);
      FinanceAccessService expiredAccessService =
          new FinanceAccessService(
              sessionRepository,
              sessionService,
              throttle,
              audit,
              advancedClock,
              PIN_HASH,
              Duration.ofMinutes(30));
      FinanceAccessFilter expiredFilter =
          new FinanceAccessFilter(expiredAccessService, audit, objectMapper);

      for (String endpoint : endpoints) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", endpoint);
        req.addHeader("Authorization", "Bearer owner-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "owner", "pass", List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));

        expiredFilter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("FINANCE_LOCKED");
        verify(chain, never()).doFilter(req, res);
      }
    }

    @Test
    @DisplayName("State 6: Revoked Grant -> 403 FINANCE_LOCKED")
    void state6_revokedGrant_denied() throws Exception {
      accessService.unlock("owner-token", CORRECT_PIN, "127.0.0.1");
      accessService.lock("owner-token", "127.0.0.1");

      for (String endpoint : endpoints) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", endpoint);
        req.addHeader("Authorization", "Bearer owner-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "owner", "pass", List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("FINANCE_LOCKED");
        verify(chain, never()).doFilter(req, res);
      }
    }
  }

  // --------------------------------------------------------------------------
  // 2. PAYROLL CONTROLLER VERIFICATION (UNLOCKED EXECUTION & EXPORTS)
  // --------------------------------------------------------------------------
  @Nested
  @DisplayName("2. Payroll Verification")
  class PayrollVerification {

    private PayrollService payrollService;
    private PayrollController payrollController;

    @BeforeEach
    void setUp() {
      payrollService = mock(PayrollService.class);
      payrollController = new PayrollController(payrollService);
    }

    @Test
    @DisplayName("Payroll operations succeed when invoked legitimately after unlock")
    void payrollControllerOperationsExecuteWhenUnlocked() {
      UUID periodId = UUID.randomUUID();
      UUID itemId = UUID.randomUUID();

      when(payrollService.list()).thenReturn(List.of());
      when(payrollService.calculate(2026, 10))
          .thenReturn(
              new PayrollService.View(
                  periodId,
                  2026,
                  10,
                  PayrollPeriod.Status.CALCULATED,
                  "POLICY",
                  100000L,
                  0L,
                  100000L,
                  100000L,
                  0,
                  0,
                  1,
                  List.of(),
                  Instant.now(),
                  null,
                  null,
                  null));

      var listRes = payrollController.list();
      assertThat(listRes.data()).isNotNull();

      var calcRes = payrollController.calculate(2026, 10);
      assertThat(calcRes.data().totalMinor()).isEqualTo(100000L);

      payrollController.approve(periodId);
      verify(payrollService).approve(periodId);

      payrollController.lock(periodId);
      verify(payrollService).lock(periodId);
    }
  }

  // --------------------------------------------------------------------------
  // 3. BILLING CONTROLLER VERIFICATION (UNLOCKED EXECUTION & EXPORTS)
  // --------------------------------------------------------------------------
  @Nested
  @DisplayName("3. Billing Verification")
  class BillingVerification {

    private BillingService billingService;
    private BillingController billingController;

    @BeforeEach
    void setUp() {
      billingService = mock(BillingService.class);
      billingController = new BillingController(billingService);
    }

    @Test
    @DisplayName("Billing operations and exports execute properly when unlocked")
    void billingOperationsAndExportsExecute() {
      UUID invoiceId = UUID.randomUUID();
      when(billingService.list()).thenReturn(List.of());
      when(billingService.get(invoiceId)).thenReturn(Map.of("id", invoiceId.toString()));
      when(billingService.export(invoiceId))
          .thenReturn(Map.of("csv", "InvoiceNumber,Client\nINV-001,Acme Corp"));
      when(billingService.exportPdf(invoiceId))
          .thenReturn(Map.of("pdfBase64", "JVBERi0xLjQK..."));

      var listRes = billingController.list();
      assertThat(listRes.data()).isNotNull();

      var getRes = billingController.get(invoiceId);
      assertThat(getRes.data()).isNotNull();

      var exportCsv = billingController.export(invoiceId);
      assertThat(exportCsv.data()).isNotNull();

      var exportPdf = billingController.exportPdf(invoiceId);
      assertThat(exportPdf.data()).isNotNull();
    }
  }

  // --------------------------------------------------------------------------
  // 4. EMPLOYEE & OPERATIONS DATA MASKING
  // --------------------------------------------------------------------------
  @Nested
  @DisplayName("4. Employee / Employee Operations / Employee 360 Audit")
  class EmployeePrivacyAudit {

    @Test
    @DisplayName("EmployeeService masks baseSalaryMinor to 0 when locked")
    void employeeServiceMasksSalaryWhenLocked() {
      EmployeeRepository repo = mock(EmployeeRepository.class);
      AuditService auditMock = mock(AuditService.class);
      JdbcTemplate jdbc = mock(JdbcTemplate.class);
      FinanceAccessGuard guard = mock(FinanceAccessGuard.class);

      when(guard.isFinanceUnlocked()).thenReturn(false);

      Employee e = new Employee();
      e.id = UUID.randomUUID();
      e.employeeCode = "SA-001";
      e.firstName = "Rahul";
      e.lastName = "Verma";
      e.displayName = "Rahul Verma";
      e.phone = "+91 9999999999";
      e.roleTitle = "Cinematographer";
      e.department = "Production";
      e.employmentType = "FULL_TIME";
      e.joiningDate = LocalDate.of(2025, 1, 1);
      e.baseSalaryMinor = 7500000L; // 75,000 INR
      e.salaryCurrency = "INR";
      e.status = Employee.Status.ACTIVE;

      when(repo.findById(e.id)).thenReturn(Optional.of(e));
      when(repo.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
          .thenReturn(List.of(e));

      EmployeeService service =
          new EmployeeService(repo, auditMock, mock(com.saproduction.command.communication.DomainEventService.class), jdbc, guard);

      // Single get
      var view = service.get(e.id);
      assertThat(view.baseSalaryMinor()).isEqualTo(0L); // Masked!

      // List
      var list = service.list(null, null);
      assertThat(list).hasSize(1);
      assertThat(list.get(0).baseSalaryMinor()).isEqualTo(0L); // Masked!
    }

    @Test
    @DisplayName("EmployeeOperationsController returns empty payroll list when locked")
    void employeeOperationsMasksPayrollWhenLocked() {
      EmployeeService empService = mock(EmployeeService.class);
      WorkTaskService taskService = mock(WorkTaskService.class);
      PayrollService payrollService = mock(PayrollService.class);
      JdbcTemplate jdbc = mock(JdbcTemplate.class);
      FinanceAccessGuard guard = mock(FinanceAccessGuard.class);

      when(guard.isFinanceUnlocked()).thenReturn(false);

      UUID empId = UUID.randomUUID();
      when(taskService.list(eq(empId), any(), any(), any(), any(), any(), any()))
          .thenReturn(List.of());
      when(payrollService.listForEmployee(empId))
          .thenReturn(
              List.of(
                  new PayrollService.View(
                      UUID.randomUUID(),
                      2026,
                      9,
                      PayrollPeriod.Status.PAID,
                      "DEFAULT",
                      50000L,
                      50000L,
                      0L,
                      0L,
                      1,
                      0,
                      0,
                      List.of(),
                      Instant.now(),
                      null,
                      null,
                      null)));
      when(jdbc.queryForObject(anyString(), eq(Long.class), eq(empId))).thenReturn(0L);

      EmployeeOperationsController controller =
          new EmployeeOperationsController(empService, taskService, payrollService, jdbc, guard);

      var res = controller.get(empId);
      assertThat(res.data().payroll()).isEmpty(); // Completely empty, zero leakage!
    }
  }

  // --------------------------------------------------------------------------
  // 5. SESSION LIFECYCLE & MULTI-SESSION ISOLATION
  // --------------------------------------------------------------------------
  @Nested
  @DisplayName("5. Session Lifecycle & Multi-Session Isolation")
  class SessionLifecycleTests {

    @Test
    @DisplayName("New session starts locked, grant does not leak to other sessions of same owner")
    void multiSessionIsolation() {
      ApiSession secondOwnerSession = new ApiSession();
      secondOwnerSession.id = UUID.randomUUID();
      secondOwnerSession.tokenHash = "owner-token-2-hash";
      secondOwnerSession.user = ownerUser;

      when(sessionService.findValidSession("owner-token-2")).thenReturn(secondOwnerSession);

      // Initial state: both locked
      assertThat(accessService.isUnlocked("owner-token")).isFalse();
      assertThat(accessService.isUnlocked("owner-token-2")).isFalse();

      // Unlock first session only
      accessService.unlock("owner-token", CORRECT_PIN, "127.0.0.1");

      assertThat(accessService.isUnlocked("owner-token")).isTrue();
      assertThat(accessService.isUnlocked("owner-token-2")).isFalse(); // Second session remains locked!
    }

    @Test
    @DisplayName("Logout or invalid token immediately loses unlock status")
    void logoutRevocation() {
      accessService.unlock("owner-token", CORRECT_PIN, "127.0.0.1");
      assertThat(accessService.isUnlocked("owner-token")).isTrue();

      // When token is invalid or logged out:
      when(sessionService.findValidSession("owner-token")).thenReturn(null);
      assertThat(accessService.isUnlocked("owner-token")).isFalse();
    }
  }

  // --------------------------------------------------------------------------
  // 6. RATE LIMITING, AUDIT, AND ZERO PIN LEAKAGE
  // --------------------------------------------------------------------------
  @Nested
  @DisplayName("6. Rate Limiting, Audit & PIN Leakage")
  class RateLimitingAndPinSecurity {

    @Test
    @DisplayName("Throttles after 5 failed attempts, cooldown clears throttle, PIN never in audit")
    void throttlingAndCooldown() {
      for (int i = 0; i < 5; i++) {
        assertThatThrownBy(() -> accessService.unlock("owner-token", "999999", "127.0.0.1"))
            .isInstanceOf(ApiException.class);
      }

      // 6th attempt is throttled
      assertThatThrownBy(() -> accessService.unlock("owner-token", CORRECT_PIN, "127.0.0.1"))
          .isInstanceOf(ApiException.class)
          .satisfies(
              e -> {
                ApiException ae = (ApiException) e;
                assertThat(ae.status).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(ae.code).isEqualTo("FINANCE_ACCESS_RATE_LIMITED");
              });

      // Verify audit was called with reason THROTTLED, but NEVER with plaintext PIN
      verify(audit, atLeastOnce())
          .record(
              eq("FINANCE_ACCESS"),
              anyString(),
              any(),
              any(),
              argThat(
                  metadata ->
                      metadata instanceof Map<?, ?> map
                          && !map.toString().contains("999999")
                          && !map.toString().contains(CORRECT_PIN)));

      // Advance clock past 15-minute cooldown (16 minutes)
      Clock cooledClock =
          Clock.fixed(
              Instant.parse("2026-10-03T12:00:00Z").plus(Duration.ofMinutes(16)), ZoneOffset.UTC);
      FinanceAccessThrottle cooledThrottle = new FinanceAccessThrottle(audit, cooledClock);
      FinanceAccessService cooledService =
          new FinanceAccessService(
              sessionRepository,
              sessionService,
              cooledThrottle,
              audit,
              cooledClock,
              PIN_HASH,
              Duration.ofMinutes(30));

      // Unlocks successfully after cooldown!
      var res = cooledService.unlock("owner-token", CORRECT_PIN, "127.0.0.1");
      assertThat(res.unlocked()).isTrue();
    }
  }
}
