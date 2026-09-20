package com.saproduction.command.auth;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.shared.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  public record OwnerView(String id, String email, String displayName, String role) {}

  public record SessionView(OwnerView owner, String token, Instant expiresAt) {}

  private final AuthenticationManager authenticationManager;
  private final UserRepository users;
  private final AuditService audit;
  private final ApiSessionService sessions;
  private final LoginThrottle throttle;

  public AuthController(
      AuthenticationManager authenticationManager,
      UserRepository users,
      AuditService audit,
      ApiSessionService sessions,
      LoginThrottle throttle) {
    this.authenticationManager = authenticationManager;
    this.users = users;
    this.audit = audit;
    this.sessions = sessions;
    this.throttle = throttle;
  }

  @PostMapping("/login")
  public ApiEnvelope<SessionView> login(
      @Valid @RequestBody LoginRequest input, HttpServletRequest request) {
    String ip = request.getRemoteAddr();
    throttle.check(input.email(), ip);
    try {
      Authentication auth =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(input.email(), input.password()));
      User user = require(auth.getName());
      throttle.succeeded(input.email(), ip);
      ApiSessionService.Issued issued = sessions.issue(user);
      audit.record(
          "AUTH",
          "OWNER_LOGIN",
          user.id.toString(),
          null,
          Map.of("email", user.email, "sessionRotated", true));
      return ApiEnvelope.of(new SessionView(view(user), issued.token(), issued.expiresAt()));
    } catch (BadCredentialsException ex) {
      throttle.failed(input.email(), ip);
      throw ex;
    }
  }

  @PostMapping("/refresh")
  public ApiEnvelope<SessionView> refresh(
      Authentication auth, @RequestHeader("Authorization") String authorization) {
    sessions.revoke(bearer(authorization));
    User user = require(auth.getName());
    ApiSessionService.Issued issued = sessions.issue(user);
    return ApiEnvelope.of(new SessionView(view(user), issued.token(), issued.expiresAt()));
  }

  @PostMapping("/logout")
  public ApiEnvelope<Boolean> logout(@RequestHeader("Authorization") String authorization) {
    sessions.revoke(bearer(authorization));
    return ApiEnvelope.of(true);
  }

  @GetMapping("/me")
  public ApiEnvelope<OwnerView> me(Authentication auth) {
    return ApiEnvelope.of(view(require(auth.getName())));
  }

  private String bearer(String authorization) {
    return authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
  }

  private User require(String email) {
    return users
        .findByEmailIgnoreCase(email)
        .orElseThrow(
            () -> ApiException.notFound("OWNER_NOT_FOUND", "Owner account was not found."));
  }

  private OwnerView view(User u) {
    return new OwnerView(u.id.toString(), u.email, u.displayName, u.role);
  }
}
