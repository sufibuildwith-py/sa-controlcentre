package com.saproduction.command.auth;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.shared.ApiException;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LoginThrottle {
  private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
  private final Clock clock;
  private final AuditService audit;

  @Autowired
  public LoginThrottle(AuditService audit) {
    this(audit, Clock.systemUTC());
  }

  LoginThrottle(AuditService audit, Clock clock) {
    this.audit = audit;
    this.clock = clock;
  }

  public void check(String email, String ip) {
    if (blocked("ip:" + ip, 5, Duration.ofMinutes(10))
        || blocked("account:" + email.toLowerCase(Locale.ROOT), 10, Duration.ofMinutes(30))) {
      audit.record("AUTH", "AUTH_LOGIN_RATE_LIMITED", email, null, Map.of("ip", ip));
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS,
          "LOGIN_RATE_LIMITED",
          "Too many sign-in attempts. Please wait before trying again.");
    }
  }

  public void failed(String email, String ip) {
    Instant now = clock.instant();
    add("ip:" + ip, now);
    add("account:" + email.toLowerCase(Locale.ROOT), now);
    audit.record("AUTH", "AUTH_LOGIN_FAILED", email, null, Map.of("ip", ip));
  }

  public void succeeded(String email, String ip) {
    failures.remove("ip:" + ip);
    failures.remove("account:" + email.toLowerCase(Locale.ROOT));
  }

  private void add(String key, Instant now) {
    Deque<Instant> bucket = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
    synchronized (bucket) {
      bucket.addLast(now);
    }
  }

  private boolean blocked(String key, int limit, Duration window) {
    Deque<Instant> bucket = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
    Instant cutoff = clock.instant().minus(window);
    synchronized (bucket) {
      while (!bucket.isEmpty() && !bucket.peekFirst().isAfter(cutoff)) bucket.removeFirst();
      return bucket.size() >= limit;
    }
  }
}
