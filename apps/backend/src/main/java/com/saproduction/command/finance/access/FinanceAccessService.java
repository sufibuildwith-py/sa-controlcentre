package com.saproduction.command.finance.access;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.auth.ApiSession;
import com.saproduction.command.auth.ApiSessionRepository;
import com.saproduction.command.auth.ApiSessionService;
import com.saproduction.command.auth.User;
import com.saproduction.command.shared.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceAccessService {

  public record StatusView(boolean eligible, boolean unlocked, Instant expiresAt) {}

  public record UnlockResult(boolean unlocked, Instant expiresAt) {}

  private final ApiSessionRepository sessionRepository;
  private final ApiSessionService sessionService;
  private final FinanceAccessThrottle throttle;
  private final AuditService audit;
  private final Clock clock;
  private final String unlockCodeHash;
  private final Duration grantTtl;

  @Autowired
  public FinanceAccessService(
      ApiSessionRepository sessionRepository,
      ApiSessionService sessionService,
      FinanceAccessThrottle throttle,
      AuditService audit,
      @Value("${app.finance.unlock-code-hash:dd7a0bb1db3c404e514d61d0b8025f2c0a6023a17fb74c4461c38bf951d9ee47}")
          String unlockCodeHash,
      @Value("${app.finance.access-ttl-minutes:30}") long ttlMinutes) {
    this(
        sessionRepository,
        sessionService,
        throttle,
        audit,
        Clock.systemUTC(),
        unlockCodeHash,
        Duration.ofMinutes(ttlMinutes));
  }

  public FinanceAccessService(
      ApiSessionRepository sessionRepository,
      ApiSessionService sessionService,
      FinanceAccessThrottle throttle,
      AuditService audit,
      Clock clock,
      String unlockCodeHash,
      Duration grantTtl) {
    this.sessionRepository = sessionRepository;
    this.sessionService = sessionService;
    this.throttle = throttle;
    this.audit = audit;
    this.clock = clock;
    this.unlockCodeHash = unlockCodeHash != null ? unlockCodeHash.trim().toLowerCase() : "";
    this.grantTtl = grantTtl;
  }

  @Transactional(readOnly = true)
  public StatusView getStatus(String token) {
    ApiSession session = sessionService.findValidSession(token);
    if (session == null || session.user == null) {
      return new StatusView(false, false, null);
    }
    boolean eligible = isOwner(session.user);
    boolean unlocked = eligible && isSessionGrantActive(session);
    Instant expiresAt = unlocked ? session.financeExpiresAt : null;
    return new StatusView(eligible, unlocked, expiresAt);
  }

  @Transactional(readOnly = true)
  public boolean isUnlocked(String token) {
    ApiSession session = sessionService.findValidSession(token);
    if (session == null || session.user == null) {
      return false;
    }
    return isOwner(session.user) && isSessionGrantActive(session);
  }

  @Transactional
  public UnlockResult unlock(String token, String code, String ip) {
    ApiSession session = sessionService.findValidSession(token);
    if (session == null || session.user == null) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Active session required.");
    }
    User user = session.user;
    String userId = user.id != null ? user.id.toString() : "unknown";

    // 1. Audit unlock attempt (NEVER log the code)
    audit.record("FINANCE_ACCESS", "FINANCE_ACCESS_UNLOCK_ATTEMPT", userId, null, Map.of("ip", ip));

    // 2. Check eligibility (must be OWNER)
    if (!isOwner(user)) {
      audit.record("FINANCE_ACCESS", "FINANCE_ACCESS_DENIED", userId, null, Map.of("ip", ip, "reason", "NOT_OWNER"));
      throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only owners can access financial data.");
    }

    // 3. Check rate limiting
    throttle.check(userId, ip);

    // 4. Verify unlock code
    boolean codeMatches = verifyCode(code);
    if (!codeMatches) {
      throttle.failed(userId, ip);
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PIN", "Invalid finance unlock code.");
    }

    // 5. Success: clear throttle and grant session access
    throttle.succeeded(userId, ip);
    Instant now = clock.instant();
    Instant expiresAt = now.plus(grantTtl);

    session.financeGrantedAt = now;
    session.financeExpiresAt = expiresAt;
    session.financeRevokedAt = null;
    sessionRepository.save(session);

    audit.record(
        "FINANCE_ACCESS",
        "FINANCE_ACCESS_GRANTED",
        userId,
        null,
        Map.of("ip", ip, "expiresAt", expiresAt.toString()));

    return new UnlockResult(true, expiresAt);
  }

  @Transactional
  public void lock(String token, String ip) {
    ApiSession session = sessionService.findValidSession(token);
    if (session == null) return;

    Instant now = clock.instant();
    session.financeRevokedAt = now;
    sessionRepository.save(session);

    String userId = session.user != null && session.user.id != null ? session.user.id.toString() : "unknown";
    audit.record("FINANCE_ACCESS", "FINANCE_ACCESS_LOCKED", userId, null, Map.of("ip", ip));
  }

  private boolean isOwner(User user) {
    return user != null && "OWNER".equalsIgnoreCase(user.role);
  }

  private boolean isSessionGrantActive(ApiSession session) {
    if (session.financeGrantedAt == null || session.financeExpiresAt == null) {
      return false;
    }
    if (session.financeRevokedAt != null) {
      return false;
    }
    return session.financeExpiresAt.isAfter(clock.instant());
  }

  private boolean verifyCode(String code) {
    if (code == null || code.isBlank()) return false;
    try {
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(code.trim().getBytes(StandardCharsets.UTF_8)));
      return MessageDigest.isEqual(
          unlockCodeHash.getBytes(StandardCharsets.UTF_8),
          hash.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      return false;
    }
  }
}
