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
import com.saproduction.command.dashboard.CommandDashboardDto.*;
import com.saproduction.command.dashboard.CommandDashboardReadService;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.shared.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class FinanceAccessTest {

  private static final String CORRECT_PIN = "153011";
  private static final String PIN_HASH = "dd7a0bb1db3c404e514d61d0b8025f2c0a6023a17fb74c4461c38bf951d9ee47";

  @Nested
  @DisplayName("FinanceAccessService Logic Tests")
  class ServiceTests {

    private ApiSessionRepository sessionRepository;
    private ApiSessionService sessionService;
    private FinanceAccessThrottle throttle;
    private AuditService audit;
    private Clock clock;
    private FinanceAccessService service;

    private User ownerUser;
    private User employeeUser;
    private ApiSession ownerSession;
    private ApiSession employeeSession;

    @BeforeEach
    void setUp() {
      sessionRepository = mock(ApiSessionRepository.class);
      sessionService = mock(ApiSessionService.class);
      audit = mock(AuditService.class);
      clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);
      throttle = new FinanceAccessThrottle(audit, clock);

      service =
          new FinanceAccessService(
              sessionRepository,
              sessionService,
              throttle,
              audit,
              clock,
              PIN_HASH,
              Duration.ofMinutes(30));

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

    @Test
    void nonOwnerCannotUnlockEvenWithCorrectPin() {
      assertThatThrownBy(() -> service.unlock("employee-token", CORRECT_PIN, "127.0.0.1"))
          .isInstanceOf(ApiException.class)
          .satisfies(
              e -> {
                ApiException ae = (ApiException) e;
                assertThat(ae.status).isEqualTo(HttpStatus.FORBIDDEN);
              });
    }

    @Test
    void wrongPinFailsAndIncrementsThrottle() {
      assertThatThrownBy(() -> service.unlock("owner-token", "000000", "127.0.0.1"))
          .isInstanceOf(ApiException.class)
          .satisfies(
              e -> {
                ApiException ae = (ApiException) e;
                assertThat(ae.status).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(ae.code).isEqualTo("INVALID_PIN");
              });
    }

    @Test
    void fiveConsecutiveWrongPinsCausesThrottling() {
      for (int i = 0; i < 5; i++) {
        assertThatThrownBy(() -> service.unlock("owner-token", "000000", "127.0.0.1"))
            .isInstanceOf(ApiException.class);
      }

      assertThatThrownBy(() -> service.unlock("owner-token", CORRECT_PIN, "127.0.0.1"))
          .isInstanceOf(ApiException.class)
          .satisfies(
              e -> {
                ApiException ae = (ApiException) e;
                assertThat(ae.status).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                assertThat(ae.code).isEqualTo("FINANCE_ACCESS_RATE_LIMITED");
              });
    }

    @Test
    void correctPinUnlocksFor30Minutes() {
      var result = service.unlock("owner-token", CORRECT_PIN, "127.0.0.1");
      assertThat(result.unlocked()).isTrue();
      assertThat(result.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(30)));

      verify(sessionRepository).save(ownerSession);
      assertThat(ownerSession.financeGrantedAt).isEqualTo(clock.instant());
      assertThat(ownerSession.financeExpiresAt).isEqualTo(clock.instant().plus(Duration.ofMinutes(30)));
      assertThat(ownerSession.financeRevokedAt).isNull();

      assertThat(service.isUnlocked("owner-token")).isTrue();
      var status = service.getStatus("owner-token");
      assertThat(status.eligible()).isTrue();
      assertThat(status.unlocked()).isTrue();
      assertThat(status.expiresAt()).isEqualTo(result.expiresAt());
    }

    @Test
    void manualLockRevokesSessionGrant() {
      service.unlock("owner-token", CORRECT_PIN, "127.0.0.1");
      assertThat(service.isUnlocked("owner-token")).isTrue();

      service.lock("owner-token", "127.0.0.1");
      assertThat(service.isUnlocked("owner-token")).isFalse();
      assertThat(ownerSession.financeRevokedAt).isEqualTo(clock.instant());
    }
  }

  @Nested
  @DisplayName("FinanceAccessFilter Tests")
  class FilterTests {

    private FinanceAccessService accessService;
    private AuditService audit;
    private ObjectMapper objectMapper;
    private FinanceAccessFilter filter;

    @BeforeEach
    void setUp() {
      accessService = mock(FinanceAccessService.class);
      audit = mock(AuditService.class);
      objectMapper = new ObjectMapper();
      filter = new FinanceAccessFilter(accessService, audit, objectMapper);
    }

    @Test
    void protectedPathWhenLockedReturnsForbiddenFinanceLocked() throws Exception {
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpServletResponse response = mock(HttpServletResponse.class);
      FilterChain chain = mock(FilterChain.class);

      when(request.getMethod()).thenReturn("GET");
      when(request.getRequestURI()).thenReturn("/api/v1/finance/overview");
      when(request.getHeader("Authorization")).thenReturn("Bearer token-123");

      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  "owner", "pass", List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));

      when(accessService.isUnlocked("token-123")).thenReturn(false);

      StringWriter stringWriter = new StringWriter();
      PrintWriter printWriter = new PrintWriter(stringWriter);
      when(response.getWriter()).thenReturn(printWriter);

      filter.doFilter(request, response, chain);

      verify(response).setStatus(403);
      verify(chain, never()).doFilter(request, response);
      assertThat(stringWriter.toString()).contains("FINANCE_LOCKED");
    }

    @Test
    void protectedPathWhenUnlockedPassesThrough() throws Exception {
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpServletResponse response = mock(HttpServletResponse.class);
      FilterChain chain = mock(FilterChain.class);

      when(request.getMethod()).thenReturn("GET");
      when(request.getRequestURI()).thenReturn("/api/v1/finance/overview");
      when(request.getHeader("Authorization")).thenReturn("Bearer token-123");

      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(
                  "owner", "pass", List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));

      when(accessService.isUnlocked("token-123")).thenReturn(true);

      filter.doFilter(request, response, chain);

      verify(chain).doFilter(request, response);
    }
  }

  @Nested
  @DisplayName("CommandDashboardReadService Privacy Veil Tests")
  class DashboardTests {

    private JdbcTemplate jdbc;
    private FinanceReadService financeReads;
    private FinanceAccessGuard guard;
    private CommandDashboardReadService service;

    @BeforeEach
    void setUp() {
      jdbc = mock(JdbcTemplate.class);
      financeReads = mock(FinanceReadService.class);
      guard = mock(FinanceAccessGuard.class);
      service = new CommandDashboardReadService(jdbc, financeReads, "Asia/Kolkata", guard);
    }

    @Test
    void dashboardHidesFinancialDataWhenLocked() {
      when(guard.isFinanceUnlocked()).thenReturn(false);

      LocalDate date = LocalDate.of(2026, 10, 3);
      when(jdbc.queryForObject(contains("productions WHERE event_date = ?"), eq(Integer.class), eq(date)))
          .thenReturn(2);
      when(jdbc.queryForObject(contains("tasks"), eq(Integer.class), eq(date))).thenReturn(3);
      when(jdbc.queryForObject(contains("attendance_records"), eq(Integer.class), eq(date))).thenReturn(1);
      when(jdbc.queryForObject(contains("NOT EXISTS"), eq(Integer.class), eq(date))).thenReturn(0);
      when(jdbc.queryForObject(contains("due_at < now()"), eq(Integer.class))).thenReturn(1);
      when(jdbc.query(contains("FROM productions"), any(RowMapper.class), eq(date))).thenReturn(List.of());
      when(jdbc.query(contains("FROM tasks"), any(RowMapper.class), eq(date))).thenReturn(List.of());
      when(jdbc.query(contains("attendance_records"), any(RowMapper.class), eq(date))).thenReturn(List.of());

      var dashboard = service.getDashboard(date);

      // Money card is null
      assertThat(dashboard.money()).isNull();

      // Today money movement is zeroed
      assertThat(dashboard.today().moneyMovement().received()).isEqualTo(BigDecimal.ZERO);
      assertThat(dashboard.today().moneyMovement().disbursed()).isEqualTo(BigDecimal.ZERO);
      assertThat(dashboard.today().moneyMovement().net()).isEqualTo(BigDecimal.ZERO);
      assertThat(dashboard.today().moneyMovement().transactionCount()).isEqualTo(0);

      // No finance queries called
      verify(financeReads, never()).overview();
      verify(financeReads, never()).accounts();
      verify(financeReads, never()).reconciliation();

      // Quick actions has only operational items
      assertThat(dashboard.quickActions()).hasSize(1);
      assertThat(dashboard.quickActions().get(0).id()).isEqualTo("NEW_PRODUCTION");
    }
  }
}
