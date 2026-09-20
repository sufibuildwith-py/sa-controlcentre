package com.saproduction.command.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RuntimeModeValidatorTest {
  @Test
  void productionRejectsDemoSeed() {
    assertThatThrownBy(
            () ->
                validator("production", true, "meta", "x", "x", "x", "x", "v23.0")
                    .afterPropertiesSet())
        .hasMessageContaining("Demo seeding");
  }

  @Test
  void productionRejectsConsoleProvider() {
    assertThatThrownBy(
            () ->
                validator("production", false, "console", "", "", "", "", "").afterPropertiesSet())
        .hasMessageContaining("console messaging");
  }

  @Test
  void productionRejectsIncompleteMetaConfiguration() {
    assertThatThrownBy(
            () ->
                validator("production", false, "meta", "", "phone", "secret", "verify", "v23.0")
                    .afterPropertiesSet())
        .hasMessageContaining("Meta configuration is incomplete");
  }

  @Test
  void explicitDemoModeAllowsDemoSeed() {
    assertThatCode(
            () -> validator("demo", true, "console", "", "", "", "", "v23.0").afterPropertiesSet())
        .doesNotThrowAnyException();
  }

  private RuntimeModeValidator validator(
      String mode,
      boolean seed,
      String provider,
      String token,
      String phone,
      String secret,
      String verify,
      String version) {
    return new RuntimeModeValidator(mode, seed, provider, token, phone, secret, verify, version);
  }
}
