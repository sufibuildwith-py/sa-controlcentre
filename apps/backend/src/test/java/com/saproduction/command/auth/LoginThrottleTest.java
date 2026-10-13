package com.saproduction.command.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.shared.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LoginThrottleTest {

  private AuditService audit;
  private MutableClock clock;
  private LoginThrottle throttle;

  private static class MutableClock extends Clock {
    private Instant now;
    private final ZoneOffset zone = ZoneOffset.UTC;

    MutableClock(Instant initial) {
      this.now = initial;
    }

    @Override
    public ZoneOffset getZone() {
      return zone;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }
  }

  @BeforeEach
  void setUp() {
    audit = mock(AuditService.class);
    clock = new MutableClock(Instant.parse("2026-10-10T12:00:00Z"));
    throttle = new LoginThrottle(audit, clock);
  }

  @Test
  @DisplayName("Under limit requests pass check successfully")
  void underLimitPasses() {
    assertThatCode(() -> throttle.check("owner@saproduction.com", "192.168.1.100"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("5 failed attempts from same IP trigger rate limit blocking")
  void ipThrottleBlocksAfter5Failures() {
    String ip = "203.0.113.42";
    String email = "target@saproduction.com";

    for (int i = 0; i < 4; i++) {
      throttle.failed(email, ip);
      assertThatCode(() -> throttle.check(email, ip)).doesNotThrowAnyException();
    }

    // 5th failure hits IP limit
    throttle.failed(email, ip);

    assertThatThrownBy(() -> throttle.check(email, ip))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> {
          ApiException apiEx = (ApiException) ex;
          org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, apiEx.status);
          org.junit.jupiter.api.Assertions.assertEquals("LOGIN_RATE_LIMITED", apiEx.code);
        });

    verify(audit).record(eq("AUTH"), eq("AUTH_LOGIN_RATE_LIMITED"), eq(email), eq(null), eq(Map.of("ip", ip)));
  }

  @Test
  @DisplayName("Account-level throttle blocks after 10 failures even across rotating/spoofed IPs")
  void accountThrottleBlocksAcrossDifferentIps() {
    String email = "victim@saproduction.com";

    // Simulate attacker rotating through different IPs (e.g. attempting to bypass IP throttle)
    for (int i = 1; i <= 9; i++) {
      String rotatingIp = "198.51.100." + i;
      throttle.failed(email, rotatingIp);
      // None of the individual IPs exceeded 5 failures, but email bucket is accumulating
      assertThatCode(() -> throttle.check(email, "198.51.100.99")).doesNotThrowAnyException();
    }

    // 10th failure on the account from yet another IP
    throttle.failed(email, "198.51.100.10");

    // Any new request for that account must be blocked regardless of IP
    assertThatThrownBy(() -> throttle.check(email, "10.0.0.1"))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> {
          ApiException apiEx = (ApiException) ex;
          org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, apiEx.status);
          org.junit.jupiter.api.Assertions.assertEquals("LOGIN_RATE_LIMITED", apiEx.code);
        });
  }

  @Test
  @DisplayName("Successful login resets throttle counters for both IP and account")
  void successResetsThrottles() {
    String ip = "203.0.113.50";
    String email = "user@saproduction.com";

    for (int i = 0; i < 4; i++) {
      throttle.failed(email, ip);
    }

    throttle.succeeded(email, ip);

    // Another 4 failures should still not block because counter was cleared
    for (int i = 0; i < 4; i++) {
      throttle.failed(email, ip);
      assertThatCode(() -> throttle.check(email, ip)).doesNotThrowAnyException();
    }
  }

  @Test
  @DisplayName("Failures expire after their sliding window")
  void failuresExpireAfterWindow() {
    String ip = "203.0.113.60";
    String email = "user2@saproduction.com";

    for (int i = 0; i < 5; i++) {
      throttle.failed(email, ip);
    }

    assertThatThrownBy(() -> throttle.check(email, ip)).isInstanceOf(ApiException.class);

    // Advance past IP window (10 minutes)
    clock.advance(Duration.ofMinutes(11));

    // IP block should be lifted
    assertThatCode(() -> throttle.check(email, ip)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Anti-churn protection: Blocked entries are never evicted by new unblocked keys")
  void antiChurnProtectsBlockedEntries() {
    // Create a throttle with small capacity (e.g. max 10 entries)
    LoginThrottle smallThrottle = new LoginThrottle(audit, clock, 10);
    String blockedIp = "192.0.2.1";
    String blockedAccount = "admin@saproduction.com";

    // 5 failures to block this IP
    for (int i = 0; i < 5; i++) {
      smallThrottle.failed(blockedAccount, blockedIp);
    }

    // Verify it is blocked
    assertThatThrownBy(() -> smallThrottle.check(blockedAccount, blockedIp))
        .isInstanceOf(ApiException.class);

    // Attacker attempts key-churn attack: floods 50 distinct fake IPs with single failure
    for (int i = 0; i < 50; i++) {
      smallThrottle.failed("fake" + i + "@example.com", "10.0.0." + i);
    }

    // Map size must remain bounded at maxEntries (10)
    org.junit.jupiter.api.Assertions.assertTrue(smallThrottle.size() <= 10, "Map size exceeded limit: " + smallThrottle.size());

    // The blocked IP MUST STILL BE BLOCKED! Anti-churn eviction protected it!
    assertThatThrownBy(() -> smallThrottle.check(blockedAccount, blockedIp))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> {
          ApiException apiEx = (ApiException) ex;
          org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, apiEx.status);
          org.junit.jupiter.api.Assertions.assertEquals("LOGIN_RATE_LIMITED", apiEx.code);
        });
  }

  @Test
  @DisplayName("CleanupExpired purges stale entries older than 30 minutes")
  void cleanupExpiredPurgesStaleEntries() {
    LoginThrottle expThrottle = new LoginThrottle(audit, clock, 100);
    expThrottle.failed("old@saproduction.com", "10.1.1.1");
    org.junit.jupiter.api.Assertions.assertTrue(expThrottle.size() > 0);

    // Advance past 30 minute window
    clock.advance(Duration.ofMinutes(31));

    expThrottle.cleanupExpired();
    org.junit.jupiter.api.Assertions.assertEquals(0, expThrottle.size(), "Expired entries were not pruned");
  }
}
