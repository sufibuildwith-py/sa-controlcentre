package com.saproduction.navigator;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "navigator_organizations")
class Organization {
  @Id UUID id;
  @Column(name = "public_id", nullable = false, unique = true) UUID publicId;
  @Column(name = "created_at", nullable = false) Instant createdAt;
  protected Organization() {}
  Organization(UUID publicId) { this.id=UUID.randomUUID(); this.publicId=publicId; this.createdAt=Instant.now(); }
}

@Entity
@Table(name = "navigator_pairing_invites")
class PairingInvite {
  @Id UUID id;
  @Column(name="organization_id",nullable=false) UUID organizationId;
  @Column(name="employee_ref",nullable=false) UUID employeeRef;
  @Column(name="code_hash",nullable=false,unique=true) String codeHash;
  @Column(name="expires_at",nullable=false) Instant expiresAt;
  @Column(name="redeemed_at") Instant redeemedAt;
  @Column(name="cancelled_at") Instant cancelledAt;
  @Column(name="created_at",nullable=false) Instant createdAt;
  @Column(name="created_by_ref") UUID createdByRef;
  protected PairingInvite() {}
}

@Entity
@Table(name = "navigator_devices")
class Device {
  @Id UUID id;
  @Column(name="organization_id",nullable=false) UUID organizationId;
  @Column(name="employee_ref",nullable=false) UUID employeeRef;
  @Column(name="token_hash",nullable=false,unique=true) String tokenHash;
  @Column(nullable=false) String platform;
  @Column(name="device_label") String deviceLabel;
  @Column(name="registered_at",nullable=false) Instant registeredAt;
  @Column(name="last_seen_at") Instant lastSeenAt;
  @Column(name="revoked_at") Instant revokedAt;
  protected Device() {}
}

@Entity
@Table(name = "navigator_location_sessions")
class LocationSession {
  @Id UUID id;
  @Column(name="organization_id",nullable=false) UUID organizationId;
  @Column(name="employee_ref",nullable=false) UUID employeeRef;
  @Column(name="device_id",nullable=false) UUID deviceId;
  @Column(name="consent_version",nullable=false) String consentVersion;
  @Column(nullable=false) String trigger;
  @Column(name="started_at",nullable=false) Instant startedAt;
  @Column(name="ended_at") Instant endedAt;
  @Column(name="end_reason") String endReason;
  @Column(name="created_at",nullable=false) Instant createdAt;
  protected LocationSession() {}
}
