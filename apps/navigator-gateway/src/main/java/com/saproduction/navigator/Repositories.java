package com.saproduction.navigator;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface OrganizationRepository extends JpaRepository<Organization, UUID> {
  Optional<Organization> findByPublicId(UUID publicId);
}
interface PairingRepository extends JpaRepository<PairingInvite, UUID> {
  Optional<PairingInvite> findByCodeHash(String codeHash);
}
interface DeviceRepository extends JpaRepository<Device, UUID> {
  Optional<Device> findByTokenHash(String tokenHash);
  Optional<Device> findFirstByOrganizationIdAndEmployeeRefOrderByRegisteredAtDesc(UUID organizationId, UUID employeeRef);
  List<Device> findAllByOrganizationId(UUID organizationId);
}
interface SessionRepository extends JpaRepository<LocationSession, UUID> {
  Optional<LocationSession> findByIdAndDeviceId(UUID id, UUID deviceId);
  Optional<LocationSession> findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc(UUID deviceId);
  Optional<LocationSession> findFirstByOrganizationIdAndEmployeeRefAndEndedAtIsNullOrderByStartedAtDesc(UUID organizationId, UUID employeeRef);
  List<LocationSession> findAllByOrganizationIdAndEndedAtIsNull(UUID organizationId);
}
