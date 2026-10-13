package com.saproduction.command.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.saproduction.command.auth.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

class ProductionConfigurationTest {

  private final UserRepository users = mock(UserRepository.class);
  private final PasswordEncoder encoder = mock(PasswordEncoder.class);
  private final ProductionConfiguration config = new ProductionConfiguration();

  @Test
  @DisplayName("Non-production mode skips production validation checks")
  void nonProductionSkipsValidation() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("app.time-zone", "Asia/Kolkata");
    env.setProperty("app.mode", "development");

    assertThatCode(() -> config.validateProductionConfiguration(env, users, encoder).run(null))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Production mode fails when FINANCE_UNLOCK_CODE_HASH is absent")
  void productionFailsWhenPinHashAbsent() {
    MockEnvironment env = validProductionEnv();
    env.setProperty("FINANCE_UNLOCK_CODE_HASH", "");

    assertThatThrownBy(() -> config.validateProductionConfiguration(env, users, encoder).run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Production requires explicit FINANCE_UNLOCK_CODE_HASH");
  }

  @Test
  @DisplayName("Production mode fails when FINANCE_UNLOCK_CODE_HASH is the known insecure default")
  void productionFailsWhenPinHashIsDefault() {
    MockEnvironment env = validProductionEnv();
    env.setProperty(
        "FINANCE_UNLOCK_CODE_HASH",
        "dd7a0bb1db3c404e514d61d0b8025f2c0a6023a17fb74c4461c38bf951d9ee47");

    assertThatThrownBy(() -> config.validateProductionConfiguration(env, users, encoder).run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("known insecure default");
  }

  @Test
  @DisplayName("Production mode fails when FINANCE_UNLOCK_CODE_HASH is malformed / invalid hex")
  void productionFailsWhenPinHashMalformed() {
    MockEnvironment env = validProductionEnv();
    env.setProperty("FINANCE_UNLOCK_CODE_HASH", "not-a-valid-64-character-sha256-hash");

    assertThatThrownBy(() -> config.validateProductionConfiguration(env, users, encoder).run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("64-character lowercase SHA-256 hex string");
  }

  @Test
  @DisplayName("Production mode passes with valid custom 64-char SHA-256 hash")
  void productionPassesWithValidCustomPinHash() {
    MockEnvironment env = validProductionEnv();
    env.setProperty(
        "FINANCE_UNLOCK_CODE_HASH",
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

    assertThatCode(() -> config.validateProductionConfiguration(env, users, encoder).run(null))
        .doesNotThrowAnyException();
  }

  private MockEnvironment validProductionEnv() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("app.time-zone", "Asia/Kolkata");
    env.setProperty("app.mode", "production");
    env.setProperty("DATABASE_URL", "jdbc:postgresql://postgres.railway.internal:5432/sa_command");
    env.setProperty("DATABASE_USERNAME", "sa_command_prod");
    env.setProperty("DATABASE_PASSWORD", "strong_production_password_123");
    env.setProperty("DESKTOP_ORIGINS", "https://sa-command.vercel.app,https://panel.saproduction.in");
    env.setProperty("MESSAGING_PROVIDER", "unconfigured");
    return env;
  }
}
