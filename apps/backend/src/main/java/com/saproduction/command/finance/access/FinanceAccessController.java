package com.saproduction.command.finance.access;

import com.saproduction.command.shared.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance-access")
public class FinanceAccessController {

  public record UnlockRequest(
      @NotBlank
      @Pattern(regexp = "^[0-9]{6}$", message = "Code must be exactly 6 digits.")
      String code) {}

  private final FinanceAccessService service;

  public FinanceAccessController(FinanceAccessService service) {
    this.service = service;
  }

  @GetMapping("/status")
  public ApiEnvelope<FinanceAccessService.StatusView> status(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    String token = bearer(authorization);
    return ApiEnvelope.of(service.getStatus(token));
  }

  @PostMapping("/unlock")
  public ApiEnvelope<FinanceAccessService.UnlockResult> unlock(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody UnlockRequest body,
      HttpServletRequest request) {
    String token = bearer(authorization);
    String ip = request.getRemoteAddr();
    return ApiEnvelope.of(service.unlock(token, body.code(), ip));
  }

  @PostMapping("/lock")
  public ApiEnvelope<FinanceAccessService.StatusView> lock(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      HttpServletRequest request) {
    String token = bearer(authorization);
    String ip = request.getRemoteAddr();
    service.lock(token, ip);
    return ApiEnvelope.of(service.getStatus(token));
  }

  private String bearer(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      return null;
    }
    return authorization.substring(7);
  }
}
