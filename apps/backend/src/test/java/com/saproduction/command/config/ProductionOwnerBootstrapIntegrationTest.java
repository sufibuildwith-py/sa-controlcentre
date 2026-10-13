package com.saproduction.command.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.saproduction.command.auth.User;
import com.saproduction.command.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
      "app.mode=production",
      "app.demo-seed=false",
      "app.messaging.provider=unconfigured",
      "app.messaging.worker-enabled=false",
      "MESSAGING_PROVIDER=unconfigured",
      "DESKTOP_ORIGINS=https://panel.saproduction.in",
      "BOOTSTRAP_OWNER_EMAIL=prod-owner@saproduction.in",
      "BOOTSTRAP_OWNER_PASSWORD=ProdSecurePass2026!",
      "BOOTSTRAP_OWNER_NAME=Production Owner"
    })
@Testcontainers(disabledWithoutDocker = true)
class ProductionOwnerBootstrapIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", postgres::getJdbcUrl);
    p.add("DATABASE_URL", postgres::getJdbcUrl);
    p.add("spring.datasource.username", postgres::getUsername);
    p.add("DATABASE_USERNAME", postgres::getUsername);
    p.add("spring.datasource.password", postgres::getPassword);
    p.add("DATABASE_PASSWORD", postgres::getPassword);
    p.add("FINANCE_UNLOCK_CODE_HASH", () -> "ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f");
  }

  @Autowired UserRepository users;
  @Autowired PasswordEncoder encoder;

  @Test
  void bootstrapsOwnerAccountOnCleanDatabase() {
    User owner = users.findByEmailIgnoreCase("prod-owner@saproduction.in").orElse(null);
    assertThat(owner).isNotNull();
    assertThat(owner.displayName).isEqualTo("Production Owner");
    assertThat(owner.role).isEqualTo("OWNER");
    assertThat(encoder.matches("ProdSecurePass2026!", owner.passwordHash)).isTrue();
  }
}
