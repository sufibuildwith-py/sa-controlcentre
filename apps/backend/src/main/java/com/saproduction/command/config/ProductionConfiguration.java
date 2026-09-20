package com.saproduction.command.config;

import com.saproduction.command.auth.UserRepository;
import java.net.URI;
import java.time.ZoneId;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class ProductionConfiguration {
  @Bean
  ApplicationRunner validateProductionConfiguration(
      Environment env, UserRepository users, PasswordEncoder passwords) {
    return args -> {
      ZoneId.of(env.getRequiredProperty("app.time-zone"));
      if (!"production".equalsIgnoreCase(env.getRequiredProperty("app.mode"))) return;
      for (String key :
          new String[] {
            "DATABASE_URL",
            "DATABASE_USERNAME",
            "DATABASE_PASSWORD",
            "DESKTOP_ORIGINS",
            "MESSAGING_PROVIDER"
          }) {
        String value = env.getProperty(key);
        if (value == null || value.isBlank())
          throw new IllegalStateException("Production requires explicit " + key + ".");
      }
      if ("sa_command_dev".equals(env.getProperty("DATABASE_PASSWORD")))
        throw new IllegalStateException("Production cannot use the development database password.");
      for (String origin : env.getRequiredProperty("DESKTOP_ORIGINS").split(",")) {
        URI uri = URI.create(origin.trim());
        if (!"https".equals(uri.getScheme())
            && !"tauri://localhost".equals(origin.trim())
            && !"http://tauri.localhost".equals(origin.trim()))
          throw new IllegalStateException(
              "Production desktop origins must use HTTPS or the exact native Tauri origin.");
        if (uri.getHost() == null || origin.contains("*"))
          throw new IllegalStateException("Production origins must be exact origins.");
      }
      users
          .findByEmailIgnoreCase("owner@saproduction.local")
          .ifPresent(
              user -> {
                throw new IllegalStateException(
                    "Remove the demo owner account before production startup.");
              });
    };
  }
}
