package com.saproduction.command.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ApiSessionRepository extends JpaRepository<ApiSession, UUID> {
  Optional<ApiSession> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
      String tokenHash, Instant now);

  @Modifying
  @Query(
      "update ApiSession s set s.revokedAt = :now where s.user.id = :userId and s.revokedAt is null")
  int revokeAll(UUID userId, Instant now);
}
