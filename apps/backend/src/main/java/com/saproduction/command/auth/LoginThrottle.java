package com.saproduction.command.auth;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.shared.ApiException;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LoginThrottle {
  public static final int DEFAULT_MAX_ENTRIES = 5000;
  private static final Duration MAX_WINDOW = Duration.ofMinutes(30);

  private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
  private final Clock clock;
  private final AuditService audit;
  private final int maxEntries;
  private final AtomicInteger opCounter = new AtomicInteger(0);

  @Autowired
  public LoginThrottle(AuditService audit) {
    this(audit, Clock.systemUTC(), DEFAULT_MAX_ENTRIES);
  }

  LoginThrottle(AuditService audit, Clock clock) {
    this(audit, clock, DEFAULT_MAX_ENTRIES);
  }

  LoginThrottle(AuditService audit, Clock clock, int maxEntries) {
    this.audit = audit;
    this.clock = clock;
    this.maxEntries = maxEntries;
  }

  public void check(String email, String ip) {
    String cleanIp = sanitizeIp(ip);
    String cleanEmail = email != null ? email.trim().toLowerCase(Locale.ROOT) : "unknown";

    if (blocked("ip:" + cleanIp, 5, Duration.ofMinutes(10))
        || blocked("account:" + cleanEmail, 10, Duration.ofMinutes(30))) {
      audit.record("AUTH", "AUTH_LOGIN_RATE_LIMITED", cleanEmail, null, Map.of("ip", cleanIp));
      throw new ApiException(
          HttpStatus.TOO_MANY_REQUESTS,
          "LOGIN_RATE_LIMITED",
          "Too many sign-in attempts. Please wait before trying again.");
    }
  }

  public void failed(String email, String ip) {
    Instant now = clock.instant();
    String cleanIp = sanitizeIp(ip);
    String cleanEmail = email != null ? email.trim().toLowerCase(Locale.ROOT) : "unknown";

    String ipKey = "ip:" + cleanIp;
    String accountKey = "account:" + cleanEmail;

    add(ipKey, now, 5, Duration.ofMinutes(10), accountKey);
    add(accountKey, now, 10, Duration.ofMinutes(30), ipKey);
    audit.record("AUTH", "AUTH_LOGIN_FAILED", cleanEmail, null, Map.of("ip", cleanIp));

    // Periodic cleanup of expired entries
    if ((opCounter.incrementAndGet() & 63) == 0) {
      cleanupExpired();
    }
  }

  public void succeeded(String email, String ip) {
    String cleanIp = sanitizeIp(ip);
    String cleanEmail = email != null ? email.trim().toLowerCase(Locale.ROOT) : "unknown";
    failures.remove("ip:" + cleanIp);
    failures.remove("account:" + cleanEmail);
  }

  public int size() {
    return failures.size();
  }

  public void cleanupExpired() {
    Instant cutoff = clock.instant().minus(MAX_WINDOW);
    failures.entrySet().removeIf(entry -> {
      Deque<Instant> bucket = entry.getValue();
      synchronized (bucket) {
        while (!bucket.isEmpty() && !bucket.peekFirst().isAfter(cutoff)) {
          bucket.removeFirst();
        }
        return bucket.isEmpty();
      }
    });
  }

  private void add(String key, Instant now, int blockThreshold, Duration window, String siblingKey) {
    Deque<Instant> bucket = failures.get(key);
    if (bucket == null) {
      if (failures.size() >= maxEntries) {
        cleanupExpired();
      }
      if (failures.size() >= maxEntries) {
        evictCandidate(siblingKey);
      }
      bucket = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
    }

    synchronized (bucket) {
      bucket.addLast(now);
    }
  }

  private void evictCandidate(String protectedKey) {
    Instant now = clock.instant();
    String candidateKey = null;
    int minSize = Integer.MAX_VALUE;

    // Pass 1: Anti-churn eviction - prefer unblocked, lowest-count entries (excluding current operation sibling)
    for (Map.Entry<String, Deque<Instant>> entry : failures.entrySet()) {
      if (protectedKey != null && entry.getKey().equals(protectedKey)) {
        continue;
      }
      Deque<Instant> q = entry.getValue();
      synchronized (q) {
        boolean isIp = entry.getKey().startsWith("ip:");
        int threshold = isIp ? 5 : 10;
        Duration win = isIp ? Duration.ofMinutes(10) : Duration.ofMinutes(30);
        Instant cutoff = now.minus(win);

        int activeCount = 0;
        for (Instant t : q) {
          if (t.isAfter(cutoff)) activeCount++;
        }

        // If currently blocked, protect from eviction during Pass 1
        if (activeCount >= threshold) {
          continue;
        }

        if (activeCount < minSize) {
          minSize = activeCount;
          candidateKey = entry.getKey();
          if (minSize <= 1) {
            break;
          }
        }
      }
    }

    // Pass 2 fallback: If ALL entries are actively blocked, evict the entry with the
    // oldest activity so new attacker attempts are ALWAYS tracked and throttled.
    if (candidateKey == null) {
      Instant oldestLastActivity = Instant.MAX;
      for (Map.Entry<String, Deque<Instant>> entry : failures.entrySet()) {
        if (protectedKey != null && entry.getKey().equals(protectedKey)) {
          continue;
        }
        Deque<Instant> q = entry.getValue();
        synchronized (q) {
          Instant last = q.peekLast();
          if (last != null && last.isBefore(oldestLastActivity)) {
            oldestLastActivity = last;
            candidateKey = entry.getKey();
          }
        }
      }
    }

    if (candidateKey != null) {
      failures.remove(candidateKey);
    }
  }

  private boolean blocked(String key, int limit, Duration window) {
    Deque<Instant> bucket = failures.get(key);
    if (bucket == null) return false;
    Instant cutoff = clock.instant().minus(window);
    synchronized (bucket) {
      while (!bucket.isEmpty() && !bucket.peekFirst().isAfter(cutoff)) bucket.removeFirst();
      return bucket.size() >= limit;
    }
  }

  private String sanitizeIp(String ip) {
    if (ip == null || ip.isBlank() || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
      return "127.0.0.1";
    }
    return ip.trim();
  }
}
