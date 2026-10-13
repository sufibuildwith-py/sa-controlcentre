package com.saproduction.navigator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
      "navigator.admin-key=test-admin-key",
      "navigator.organization-public-id=6be51f4b-e5bd-4e91-8d60-4e41a72ad86f",
      "navigator.pairing-pepper=test-pepper",
      "management.endpoints.web.exposure.include=health",
      "management.endpoint.health.show-details=never"
    })
class GatewaySecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private OrganizationRepository organizationRepository;
  @MockBean private PairingRepository pairingRepository;
  @MockBean private DeviceRepository deviceRepository;
  @MockBean private SessionRepository sessionRepository;
  @MockBean private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("SEC-001: Public access to /actuator root must be denied (403)")
  void actuatorRootIsDenied() throws Exception {
    mockMvc.perform(get("/actuator")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("SEC-001: Public access to /actuator/metrics must be denied (403)")
  void actuatorMetricsIsDenied() throws Exception {
    mockMvc.perform(get("/actuator/metrics")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("SEC-001: Public access to /actuator/info must be denied (403)")
  void actuatorInfoIsDenied() throws Exception {
    mockMvc.perform(get("/actuator/info")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("SEC-001: Public access to /actuator/health is permitted and does not leak details")
  void actuatorHealthIsPermitted() throws Exception {
    mockMvc
        .perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());
  }

  @Test
  @DisplayName("Public mobile pairing endpoint is accessible through security filter")
  void mobilePairingRedeemIsAccessible() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/mobile/pairings/redeem")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Admin endpoint rejects request when X-Navigator-Service-Key is missing (400)")
  void adminEndpointRejectsMissingServiceKey() throws Exception {
    mockMvc
        .perform(post("/api/v1/admin/realtime-ticket"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Admin endpoint rejects request when X-Navigator-Service-Key is invalid (401)")
  void adminEndpointRejectsInvalidServiceKey() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/realtime-ticket")
                .header("X-Navigator-Service-Key", "wrong-secret-key"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("ADMIN_AUTH_REQUIRED"));
  }

  @Test
  @DisplayName("Admin endpoint permits minting realtime ticket with valid service key")
  void adminEndpointPermitsValidServiceKey() throws Exception {
    Organization org = new Organization(UUID.fromString("6be51f4b-e5bd-4e91-8d60-4e41a72ad86f"));
    org.id = UUID.randomUUID();
    when(organizationRepository.findByPublicId(any())).thenReturn(Optional.of(org));

    mockMvc
        .perform(
            post("/api/v1/admin/realtime-ticket")
                .header("X-Navigator-Service-Key", "test-admin-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ticket").isString());
  }

  @Test
  @DisplayName("Mobile protected endpoint rejects missing Authorization header (400)")
  void mobileEndpointRejectsMissingAuthorization() throws Exception {
    mockMvc
        .perform(get("/api/v1/mobile/state"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Mobile protected endpoint rejects unauthenticated/unknown device token (401)")
  void mobileEndpointRejectsUnknownDeviceToken() throws Exception {
    when(deviceRepository.findByTokenHash(any())).thenReturn(Optional.empty());

    mockMvc
        .perform(
            get("/api/v1/mobile/state")
                .header("Authorization", "Bearer unknown-device-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("INVALID_DEVICE"));
  }

  @Test
  @DisplayName("Mobile protected endpoint rejects revoked device token (403)")
  void mobileEndpointRejectsRevokedDevice() throws Exception {
    Device revokedDevice = new Device();
    revokedDevice.id = UUID.randomUUID();
    revokedDevice.organizationId = UUID.randomUUID();
    revokedDevice.employeeRef = UUID.randomUUID();
    revokedDevice.revokedAt = Instant.now();

    when(deviceRepository.findByTokenHash(any())).thenReturn(Optional.of(revokedDevice));

    mockMvc
        .perform(
            get("/api/v1/mobile/state")
                .header("Authorization", "Bearer revoked-device-token"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("DEVICE_REVOKED"));
  }

  @Test
  @DisplayName("Mobile protected endpoint permits valid active device token (200)")
  void mobileEndpointPermitsActiveDevice() throws Exception {
    Device activeDevice = new Device();
    activeDevice.id = UUID.randomUUID();
    activeDevice.organizationId = UUID.randomUUID();
    activeDevice.employeeRef = UUID.randomUUID();
    activeDevice.revokedAt = null;

    when(deviceRepository.findByTokenHash(any())).thenReturn(Optional.of(activeDevice));

    mockMvc
        .perform(
            get("/api/v1/mobile/state")
                .header("Authorization", "Bearer active-device-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deviceId").value(activeDevice.id.toString()))
        .andExpect(jsonPath("$.revoked").value(false));
  }

  @Test
  @DisplayName("SEC-008: Simulator endpoint is disabled by default (404 SIMULATOR_DISABLED)")
  void simulatorEndpointIsDisabledByDefault() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/simulator/start")
                .header("X-Navigator-Service-Key", "test-admin-key")
                .contentType("application/json")
                .content("{\"employeeRefs\":[\"" + UUID.randomUUID() + "\"]}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("SIMULATOR_DISABLED"));
  }

  @Test
  @DisplayName("RateLimits bounds map size and protects blocked entries against churn")
  void rateLimitsBounding() {
    RateLimits limits = new RateLimits();
    for (int i = 0; i < 5; i++) {
      try {
        limits.check("test-block", "key1", 5, java.time.Duration.ofMinutes(1));
      } catch (GatewayException ignored) {}
    }
    // key1 reached limit (5th attempt threw RATE_LIMITED)
    org.junit.jupiter.api.Assertions.assertThrows(
        GatewayException.class,
        () -> limits.check("test-block", "key1", 5, java.time.Duration.ofMinutes(1)));

    // Churn 2100 unblocked keys to trigger capacity eviction
    for (int i = 0; i < 2100; i++) {
      limits.check("churn", "k" + i, 10, java.time.Duration.ofMinutes(1));
    }

    // key1 must STILL be blocked (anti-churn preserved it)
    org.junit.jupiter.api.Assertions.assertThrows(
        GatewayException.class,
        () -> limits.check("test-block", "key1", 5, java.time.Duration.ofMinutes(1)));
  }
}
