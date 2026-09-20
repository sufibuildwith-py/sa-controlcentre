package com.saproduction.command.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "api_sessions")
public class ApiSession {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public UUID id;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  public String tokenHash;

  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id")
  public User user;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "last_used_at", nullable = false)
  public Instant lastUsedAt;

  @Column(name = "expires_at", nullable = false)
  public Instant expiresAt;

  @Column(name = "revoked_at")
  public Instant revokedAt;
}
