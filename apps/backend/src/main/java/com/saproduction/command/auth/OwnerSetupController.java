package com.saproduction.command.auth;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.shared.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/owner-setup")
@ConditionalOnProperty(name = "app.deployment", havingValue = "desktop")
public class OwnerSetupController {
  public record Input(
      @NotBlank @Size(min = 12, max = 72) String password, @NotBlank String confirmation) {}

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JdbcTemplate jdbc;
  private final ApiSessionService sessions;
  private final AuditService audit;

  public OwnerSetupController(
      UserRepository users,
      PasswordEncoder encoder,
      JdbcTemplate jdbc,
      ApiSessionService sessions,
      AuditService audit) {
    this.users = users;
    this.encoder = encoder;
    this.jdbc = jdbc;
    this.sessions = sessions;
    this.audit = audit;
  }

  @GetMapping
  public ApiEnvelope<Map<String, Object>> status() {
    return ApiEnvelope.of(
        Map.of("required", users.count() == 0, "owner", "Azeem Khan", "company", "SA Productions"));
  }

  @PostMapping
  @Transactional
  public ApiEnvelope<AuthController.SessionView> setup(@Valid @RequestBody Input input) {
    jdbc.execute("LOCK TABLE users IN EXCLUSIVE MODE");
    if (users.count() != 0)
      throw ApiException.conflict(
          "OWNER_ALREADY_INITIALIZED", "SA Command is already set up. Please sign in.");
    if (!input.password().equals(input.confirmation()))
      throw ApiException.badRequest("PASSWORD_MISMATCH", "Passwords do not match.");
    if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw ApiException.badRequest("PASSWORD_TOO_LONG", "Please use a shorter password.");
    User user = new User();
    user.email = "azeem@sa-command.local";
    user.displayName = "Azeem Khan";
    user.role = "OWNER";
    user.passwordHash = encoder.encode(input.password());
    users.saveAndFlush(user);
    var session = sessions.issue(user);
    audit.record(
        "AUTH", "OWNER_INITIALIZED", user.id.toString(), null, Map.of("method", "first-run"));
    return ApiEnvelope.of(
        new AuthController.SessionView(
            new AuthController.OwnerView(
                user.id.toString(), user.email, user.displayName, user.role),
            session.token(),
            session.expiresAt()));
  }
}
