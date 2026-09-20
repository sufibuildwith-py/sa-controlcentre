package com.saproduction.command.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiSessionService {
  public record Issued(String token, Instant expiresAt) {}

  private static final SecureRandom RANDOM = new SecureRandom();
  private final ApiSessionRepository sessions;
  private final Clock clock;

  @Autowired
  public ApiSessionService(ApiSessionRepository sessions) {
    this(sessions, Clock.systemUTC());
  }

  ApiSessionService(ApiSessionRepository sessions, Clock clock) {
    this.sessions = sessions;
    this.clock = clock;
  }

  @Transactional
  public Issued issue(User user) {
    Instant now = clock.instant();
    sessions.revokeAll(user.id, now);
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String token = HexFormat.of().formatHex(bytes);
    ApiSession session = new ApiSession();
    session.tokenHash = hash(token);
    session.user = user;
    session.createdAt = now;
    session.lastUsedAt = now;
    session.expiresAt = now.plus(Duration.ofHours(12));
    sessions.save(session);
    return new Issued(token, session.expiresAt);
  }

  @Transactional
  public User authenticate(String token) {
    ApiSession session =
        sessions
            .findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(hash(token), clock.instant())
            .orElse(null);
    if (session == null) return null;
    session.lastUsedAt = clock.instant();
    return session.user;
  }

  @Transactional
  public void revoke(String token) {
    sessions
        .findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(hash(token), clock.instant())
        .ifPresent(session -> session.revokedAt = clock.instant());
  }

  static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }
}
