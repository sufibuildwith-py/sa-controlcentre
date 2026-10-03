package com.saproduction.command.finance.access;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.shared.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class FinanceAccessThrottle {
  private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
  private final Clock clock;
  private final AuditService audit;

  @Autowired
  public FinanceAccessThrottle(AuditService audit) {
    this(audit, Clock.systemUTC());
  }

  public FinanceAccessThrottle(AuditService audit, Clock clock) {
    this.audit = audit;
    this.clock = clock;
  }

  public void check(String userId, String ip) {
    if (blocked("ip:" + ip, 5, Duration.ofMinutes(10))
        || (userId != null && blocked("user:" + userId, 5, Duration.ofMinutes(15)))) {
      audit.record("FINANCE_ACCESS", "FINANCE_ACCESS_RATE_LIMITED", userId != null ? userId : "anonymous", null, Map.of("ip", ip));
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS,
          "FINANCE_ACCESS_RATE_LIMITED",
          "Too many unlock attempts. Please wait before trying again.");
    }
  }

  public void failed(String userId, String ip) {
    Instant now = clock.instant();
    add("ip:" + ip, now);
    if (userId != null) {
      add("user:" + userId, now);
    }
    audit.record("FINANCE_ACCESS", "FINANCE_ACCESS_DENIED", userId != null ? userId : "anonymous", null, Map.of("ip", ip, "reason", "INVALID_CODE"));
  }

  public void succeeded(String userId, String ip) {
    failures.remove("ip:" + ip);
    if (userId != null) {
      failures.remove("user:" + userId);
    }
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
      while (!bucket.isEmpty() && !bucket.peekFirst().isAfter(cutoff)) {
        bucket.removeFirst();
      }
      return bucket.size() >= limit;
    }
  }
}
