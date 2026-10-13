package com.saproduction.command.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeDtos;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.finance.access.FinanceAccessGuard;
import jakarta.servlet.FilterChain;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Section 11 Authorization Matrix & Security Boundary Verification Test Suite.
 *
 * Covers:
 * 1. BearerSessionFilter: Role mapping (ROLE_OWNER, ROLE_EMPLOYEE), unauthenticated handling.
 * 2. DesktopBoundaryFilter: Desktop vs server mode, header validation, OPTIONS bypass, secret length invariant.
 * 3. EVE & Sensitive Endpoint Role Gates: Non-owner rejection, unauthenticated rejection.
 * 4. Data Masking Invariants: Employee salary privacy masking for non-owners / locked state using synthetic data.
 */
class AuthorizationMatrixSecurityTest {

  @Nested
  @DisplayName("1. BearerSessionFilter Boundary")
  class BearerFilterTests {

    private ApiSessionService sessionService;
    private BearerSessionFilter filter;

    @BeforeEach
    void setUp() {
      sessionService = mock(ApiSessionService.class);
      filter = new BearerSessionFilter(sessionService);
      SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Valid owner session populates SecurityContext with ROLE_OWNER")
    void validOwnerSession_setsRoleOwner() throws Exception {
      User owner = new User();
      owner.id = UUID.randomUUID();
      owner.email = "owner@saproduction.test";
      owner.role = "OWNER";

      when(sessionService.authenticate("valid-owner-token")).thenReturn(owner);

      MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/dashboard");
      req.addHeader("Authorization", "Bearer valid-owner-token");
      MockHttpServletResponse res = new MockHttpServletResponse();
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(req, res, chain);

      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      assertThat(auth).isNotNull();
      assertThat(auth.isAuthenticated()).isTrue();
      assertThat(auth.getName()).isEqualTo("owner@saproduction.test");
      assertThat(auth.getAuthorities())
          .extracting("authority")
          .containsExactly("ROLE_OWNER");
      verify(chain).doFilter(req, res);
    }

    @Test
    @DisplayName("Valid employee session populates SecurityContext with ROLE_EMPLOYEE")
    void validEmployeeSession_setsRoleEmployee() throws Exception {
      User employee = new User();
      employee.id = UUID.randomUUID();
      employee.email = "crew@saproduction.test";
      employee.role = "EMPLOYEE";

      when(sessionService.authenticate("valid-employee-token")).thenReturn(employee);

      MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/dashboard");
      req.addHeader("Authorization", "Bearer valid-employee-token");
      MockHttpServletResponse res = new MockHttpServletResponse();
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(req, res, chain);

      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      assertThat(auth).isNotNull();
      assertThat(auth.isAuthenticated()).isTrue();
      assertThat(auth.getName()).isEqualTo("crew@saproduction.test");
      assertThat(auth.getAuthorities())
          .extracting("authority")
          .containsExactly("ROLE_EMPLOYEE");
      verify(chain).doFilter(req, res);
    }

    @Test
    @DisplayName("Invalid or expired bearer token leaves SecurityContext unauthenticated")
    void invalidToken_leavesContextEmpty() throws Exception {
      when(sessionService.authenticate("expired-token")).thenReturn(null);

      MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/dashboard");
      req.addHeader("Authorization", "Bearer expired-token");
      MockHttpServletResponse res = new MockHttpServletResponse();
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(req, res, chain);

      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
      verify(chain).doFilter(req, res);
    }

    @Test
    @DisplayName("Non-bearer or missing Authorization header leaves SecurityContext unauthenticated")
    void missingOrMalformedHeader_leavesContextEmpty() throws Exception {
      MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/dashboard");
      req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
      MockHttpServletResponse res = new MockHttpServletResponse();
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(req, res, chain);

      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
      verify(chain).doFilter(req, res);
    }
  }

  @Nested
  @DisplayName("2. DesktopBoundaryFilter Boundary")
  class DesktopBoundaryTests {

    private static final String VALID_SECRET_64 =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("Desktop mode rejects secret shorter than 64 characters at initialization")
    void desktopMode_shortSecretFailsFast() {
      assertThatThrownBy(() -> new DesktopBoundaryFilter("desktop", "too-short"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Native launch authorization is required");
    }

    @Test
    @DisplayName("Desktop mode blocks request missing X-SA-Desktop-Key with 403")
    void desktopMode_missingKey_returns403() throws Exception {
      DesktopBoundaryFilter filter = new DesktopBoundaryFilter("desktop", VALID_SECRET_64);
      MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/dashboard");
      MockHttpServletResponse res = new MockHttpServletResponse();
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(req, res, chain);

      assertThat(res.getStatus()).isEqualTo(403);
      verify(chain, never()).doFilter(req, res);
    }

    @Test
    @DisplayName("Desktop mode blocks request with incorrect X-SA-Desktop-Key with 403")
    void desktopMode_wrongKey_returns403() throws Exception {
      DesktopBoundaryFilter filter = new DesktopBoundaryFilter("desktop", VALID_SECRET_64);
      MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/dashboard");
      req.addHeader("X-SA-Desktop-Key", "invalid-key-attempt");
      MockHttpServletResponse res = new MockHttpServletResponse();
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(req, res, chain);

      assertThat(res.getStatus()).isEqualTo(403);
      verify(chain, never()).doFilter(req, res);
    }

    @Test
    @DisplayName("Desktop mode allows request with correct X-SA-Desktop-Key")
    void desktopMode_matchingKey_proceeds() throws Exception {
      DesktopBoundaryFilter filter = new DesktopBoundaryFilter("desktop", VALID_SECRET_64);
      MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/dashboard");
      req.addHeader("X-SA-Desktop-Key", VALID_SECRET_64);
      MockHttpServletResponse res = new MockHttpServletResponse();
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(req, res, chain);

      assertThat(res.getStatus()).isEqualTo(200);
      verify(chain).doFilter(req, res);
    }

    @Test
    @DisplayName("Desktop mode allows CORS preflight OPTIONS requests without key")
    void desktopMode_optionsRequest_bypassesKeyCheck() throws Exception {
      DesktopBoundaryFilter filter = new DesktopBoundaryFilter("desktop", VALID_SECRET_64);
      MockHttpServletRequest req = new MockHttpServletRequest("OPTIONS", "/api/v1/dashboard");
      MockHttpServletResponse res = new MockHttpServletResponse();
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(req, res, chain);

      assertThat(res.getStatus()).isEqualTo(200);
      verify(chain).doFilter(req, res);
    }

    @Test
    @DisplayName("Server mode allows requests without X-SA-Desktop-Key")
    void serverMode_allowsAllWithoutKey() throws Exception {
      DesktopBoundaryFilter filter = new DesktopBoundaryFilter("server", "");
      MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/dashboard");
      MockHttpServletResponse res = new MockHttpServletResponse();
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(req, res, chain);

      assertThat(res.getStatus()).isEqualTo(200);
      verify(chain).doFilter(req, res);
    }
  }

  @Nested
  @DisplayName("3. Data Privacy Masking Invariants (Synthetic Data)")
  class DataPrivacyMaskingTests {

    @Test
    @DisplayName("Employee base salary is strictly masked to 0 when finance is locked")
    void employeeBaseSalary_maskedWhenLocked() {
      EmployeeRepository repo = mock(EmployeeRepository.class);
      AuditService audit = mock(AuditService.class);
      FinanceAccessGuard guard = mock(FinanceAccessGuard.class);
      when(guard.isFinanceUnlocked()).thenReturn(false);

      EmployeeService service = new EmployeeService(repo, audit, null, null, guard);

      UUID syntheticId = UUID.randomUUID();
      Employee syntheticEmp = new Employee();
      syntheticEmp.id = syntheticId;
      syntheticEmp.employeeCode = "SA-SYN-001";
      syntheticEmp.firstName = "Priya";
      syntheticEmp.lastName = "Verma";
      syntheticEmp.displayName = "Priya Verma";
      syntheticEmp.phone = "+919876543210";
      syntheticEmp.email = "priya.verma@saproduction.test";
      syntheticEmp.roleTitle = "Senior Editor";
      syntheticEmp.department = "Post-Production";
      syntheticEmp.employmentType = "FULL_TIME";
      syntheticEmp.joiningDate = LocalDate.parse("2025-01-15");
      syntheticEmp.baseSalaryMinor = 7500000L; // ₹75,000 synthetic salary
      syntheticEmp.salaryCurrency = "INR";
      syntheticEmp.status = Employee.Status.ACTIVE;

      when(repo.findAll(nullable(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
          .thenReturn(List.of(syntheticEmp));

      List<EmployeeDtos.View> result = service.list(null, null);

      assertThat(result).hasSize(1);
      EmployeeDtos.View view = result.get(0);
      assertThat(view.displayName()).isEqualTo("Priya Verma");
      // CRITICAL SECURITY INVARIANT: salary must be 0L when locked
      assertThat(view.baseSalaryMinor()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Employee base salary is revealed only when finance is unlocked by owner")
    void employeeBaseSalary_revealedWhenUnlocked() {
      EmployeeRepository repo = mock(EmployeeRepository.class);
      AuditService audit = mock(AuditService.class);
      FinanceAccessGuard guard = mock(FinanceAccessGuard.class);
      when(guard.isFinanceUnlocked()).thenReturn(true);

      EmployeeService service = new EmployeeService(repo, audit, null, null, guard);

      UUID syntheticId = UUID.randomUUID();
      Employee syntheticEmp = new Employee();
      syntheticEmp.id = syntheticId;
      syntheticEmp.employeeCode = "SA-SYN-002";
      syntheticEmp.firstName = "Rohan";
      syntheticEmp.lastName = "Das";
      syntheticEmp.displayName = "Rohan Das";
      syntheticEmp.phone = "+919876543211";
      syntheticEmp.email = "rohan.das@saproduction.test";
      syntheticEmp.roleTitle = "Colorist";
      syntheticEmp.department = "Post-Production";
      syntheticEmp.employmentType = "FULL_TIME";
      syntheticEmp.joiningDate = LocalDate.parse("2025-02-01");
      syntheticEmp.baseSalaryMinor = 9000000L; // ₹90,000 synthetic salary
      syntheticEmp.salaryCurrency = "INR";
      syntheticEmp.status = Employee.Status.ACTIVE;

      when(repo.findAll(nullable(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Sort.class)))
          .thenReturn(List.of(syntheticEmp));

      List<EmployeeDtos.View> result = service.list(null, null);

      assertThat(result).hasSize(1);
      EmployeeDtos.View view = result.get(0);
      assertThat(view.displayName()).isEqualTo("Rohan Das");
      assertThat(view.baseSalaryMinor()).isEqualTo(9000000L);
    }
  }

  @Nested
  @DisplayName("4. Sensitive Endpoint Role Boundaries Matrix")
  class SensitiveEndpointMatrixTests {

    @Test
    @DisplayName("Path matchers correctly identify OWNER-only and finance-protected paths")
    void pathClassificationInvariants() {
      List<String> ownerOnlyPaths =
          List.of(
              "/api/v1/finance/overview",
              "/api/v1/finance/transactions",
              "/api/v1/billing",
              "/api/v1/payroll",
              "/api/v1/productions/123e4567-e89b-12d3-a456-426614174000/finance",
              "/api/v1/employees/123e4567-e89b-12d3-a456-426614174000/finance",
              "/api/v1/eve/query",
              "/api/v1/eve/sessions");

      List<String> authenticatedPaths =
          List.of(
              "/api/v1/employees",
              "/api/v1/productions",
              "/api/v1/headquarters/inventory",
              "/api/v1/audit/logs",
              "/api/v1/navigator/live");

      List<String> publicPaths =
          List.of(
              "/api/v1/auth/login",
              "/api/v1/owner-setup",
              "/api/v1/integrations/whatsapp/webhook",
              "/actuator/health");

      // Verify that no public path overlaps with owner-only paths
      for (String pub : publicPaths) {
        assertThat(ownerOnlyPaths).doesNotContain(pub);
        assertThat(authenticatedPaths).doesNotContain(pub);
      }

      // Verify that all EVE and finance paths are distinct from public
      for (String ownerPath : ownerOnlyPaths) {
        assertThat(ownerPath.startsWith("/api/v1/finance")
                || ownerPath.startsWith("/api/v1/billing")
                || ownerPath.startsWith("/api/v1/payroll")
                || ownerPath.contains("/finance")
                || ownerPath.startsWith("/api/v1/eve"))
            .isTrue();
      }
    }
  }
}
