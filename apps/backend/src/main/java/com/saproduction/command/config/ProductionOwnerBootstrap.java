package com.saproduction.command.config;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.auth.User;
import com.saproduction.command.auth.UserRepository;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production-safe initial owner bootstrapper.
 *
 * When SA Command boots against an empty production database (zero users),
 * it creates the initial ROLE_OWNER account using environment-provided credentials
 * (BOOTSTRAP_OWNER_EMAIL and BOOTSTRAP_OWNER_PASSWORD).
 *
 * Invariant:
 * - If any user already exists, this runner exits immediately without action.
 * - Password is never stored plaintext; it is hashed with BCrypt(12).
 * - An immutable audit event is recorded.
 */
@Component
public class ProductionOwnerBootstrap {

  private static final Logger log = LoggerFactory.getLogger(ProductionOwnerBootstrap.class);

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AuditService audit;
  private final String bootstrapEmail;
  private final String bootstrapPassword;
  private final String bootstrapName;

  public ProductionOwnerBootstrap(
      UserRepository users,
      PasswordEncoder encoder,
      AuditService audit,
      @Value("${app.bootstrap.owner-email:${BOOTSTRAP_OWNER_EMAIL:}}") String bootstrapEmail,
      @Value("${app.bootstrap.owner-password:${BOOTSTRAP_OWNER_PASSWORD:}}") String bootstrapPassword,
      @Value("${app.bootstrap.owner-name:${BOOTSTRAP_OWNER_NAME:SA Owner}}") String bootstrapName) {
    this.users = users;
    this.encoder = encoder;
    this.audit = audit;
    this.bootstrapEmail = bootstrapEmail != null ? bootstrapEmail.trim() : "";
    this.bootstrapPassword = bootstrapPassword != null ? bootstrapPassword.trim() : "";
    this.bootstrapName = bootstrapName != null ? bootstrapName.trim() : "SA Owner";
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(1)
  @Transactional
  public void onApplicationReady() {
    if (users.count() > 0) {
      return;
    }

    if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank()) {
      log.info(
          "Clean database detected with 0 users. No bootstrap credentials configured via "
              + "BOOTSTRAP_OWNER_EMAIL / BOOTSTRAP_OWNER_PASSWORD.");
      return;
    }

    if (bootstrapPassword.length() < 8) {
      throw new IllegalStateException(
          "Bootstrap owner password must be at least 8 characters long.");
    }

    User owner = new User();
    owner.email = bootstrapEmail.toLowerCase(Locale.ROOT);
    owner.displayName = bootstrapName;
    owner.role = "OWNER";
    owner.passwordHash = encoder.encode(bootstrapPassword);
    users.save(owner);

    audit.record(
        "AUTH",
        "OWNER_BOOTSTRAPPED",
        owner.id.toString(),
        null,
        Map.of("email", owner.email, "displayName", owner.displayName));

    log.info("Production owner account bootstrapped successfully: {}", owner.email);
  }
}
