package com.saproduction.navigator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

final class NavigatorDtos {
  private NavigatorDtos() {}
  record CreatePairing(@NotNull UUID employeeRef, UUID createdByRef) {}
  record PairingView(UUID id, String code, Instant expiresAt) {}
  record Redeem(@NotBlank @Pattern(regexp="^[0-9]{6}$") String code, @NotBlank @Pattern(regexp="ANDROID|IOS") String platform, @Size(max=120) String deviceLabel) {}
  record Paired(UUID deviceId, UUID employeeRef, String deviceToken) {}
  record StartSession(@NotBlank @Size(max=32) String consentVersion) {}
  record SessionView(UUID sessionId, Instant startedAt) {}
  record EndDuty(@NotNull UUID sessionId, Instant endedAt) {}
  record Projection(
      @NotNull UUID employeeRef,
      @NotBlank @Size(max = 160) String displayName,
      UUID productionRef,
      @Size(max = 180) String productionTitle,
      @Size(max = 180) String locationName,
      Instant startsAt,
      Instant endsAt) {}
  record MobileMessageInput(
      @NotNull UUID employeeRef,
      @NotBlank @Size(max = 160) String title,
      @NotBlank @Size(max = 1600) String body) {}
  record MobileMessage(UUID id, String title, String body, Instant sentAt, boolean read) {}
  record MobileHome(
      String displayName,
      boolean dutyActive,
      UUID sessionId,
      Instant startedAt,
      Instant serverLastLocationAt,
      UUID productionRef,
      String productionTitle,
      String locationName,
      Instant startsAt,
      Instant endsAt,
      List<MobileMessage> messages) {}
  record Point(@PositiveOrZero long sequenceNo, @DecimalMin("-90") @DecimalMax("90") double latitude, @DecimalMin("-180") @DecimalMax("180") double longitude, @PositiveOrZero Double accuracyMeters, @PositiveOrZero Double speed, @DecimalMin("0") @DecimalMax("360") Double heading, @NotNull Instant recordedAt) {}
  record Batch(@NotNull UUID sessionId, @NotEmpty @Size(max=25) List<@Valid Point> points) {}
  record Acknowledged(List<Long> acknowledgedSequenceNumbers) {}
  record Live(UUID employeeRef, UUID deviceId, UUID sessionId, String state, Double latitude, Double longitude, Double accuracyMeters, Instant recordedAt, Instant receivedAt, Instant trackingStartedAt, Instant pairedAt, String deviceLabel) {}
  record Event(String type, UUID employeeRef, UUID deviceId, UUID sessionId, Double latitude, Double longitude, Double accuracyMeters, Instant recordedAt, Instant receivedAt) {}
  record Ticket(String ticket, UUID organizationPublicId, Instant expiresAt) {}
  record SimulatorStart(@NotEmpty @Size(max=20) List<UUID> employeeRefs) {}
}
