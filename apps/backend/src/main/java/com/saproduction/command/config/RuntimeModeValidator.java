package com.saproduction.command.config;

import java.util.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RuntimeModeValidator implements InitializingBean {
  private final String mode;
  private final boolean demoSeed;
  private final String provider;
  private final Map<String, String> meta;

  public RuntimeModeValidator(
      @Value("${app.mode}") String mode,
      @Value("${app.demo-seed}") boolean demoSeed,
      @Value("${app.messaging.provider}") String provider,
      @Value("${app.messaging.meta.access-token:}") String accessToken,
      @Value("${app.messaging.meta.phone-number-id:}") String phoneNumberId,
      @Value("${app.messaging.meta.app-secret:}") String appSecret,
      @Value("${app.messaging.meta.verify-token:}") String verifyToken,
      @Value("${app.messaging.meta.graph-api-version:}") String apiVersion) {
    this.mode = mode;
    this.demoSeed = demoSeed;
    this.provider = provider;
    this.meta =
        Map.of(
            "access token",
            accessToken,
            "phone number ID",
            phoneNumberId,
            "app secret",
            appSecret,
            "verify token",
            verifyToken,
            "API version",
            apiVersion);
  }

  @Override
  public void afterPropertiesSet() {
    if (!Set.of("development", "demo", "production", "test")
        .contains(mode.toLowerCase(Locale.ROOT))) {
      throw new IllegalStateException("APP_MODE must be development, demo, test, or production.");
    }
    if (demoSeed && !mode.equalsIgnoreCase("demo")) {
      throw new IllegalStateException("Demo seeding is only allowed in explicit demo mode.");
    }
    if (mode.equalsIgnoreCase("production") && provider.equalsIgnoreCase("console")) {
      throw new IllegalStateException("Production cannot use console messaging.");
    }
    if (mode.equalsIgnoreCase("production") && provider.equalsIgnoreCase("meta")) {
      meta.forEach(
          (name, value) -> {
            if (value == null || value.isBlank()) {
              throw new IllegalStateException(
                  "Production Meta configuration is incomplete: missing " + name + ".");
            }
          });
    }
  }
}
