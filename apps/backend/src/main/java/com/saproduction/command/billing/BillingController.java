package com.saproduction.command.billing;

import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {
  private final BillingService billing;

  public BillingController(BillingService billing) {
    this.billing = billing;
  }

  @GetMapping
  public ApiEnvelope<?> list() {
    return ApiEnvelope.of(billing.list());
  }

  @GetMapping("/{id}")
  public ApiEnvelope<?> get(@PathVariable UUID id) {
    return ApiEnvelope.of(billing.get(id));
  }

  @PostMapping
  public ApiEnvelope<?> create(@Valid @RequestBody BillingCommands.Create input) {
    return ApiEnvelope.of(billing.create(input));
  }

  @PostMapping("/{id}/issue")
  public ApiEnvelope<?> issue(@PathVariable UUID id, @Valid @RequestBody BillingCommands.Issue input) {
    return ApiEnvelope.of(billing.issue(id, input));
  }

  @PostMapping("/{id}/cancel")
  public ApiEnvelope<?> cancel(@PathVariable UUID id) {
    return ApiEnvelope.of(billing.cancel(id));
  }

  @GetMapping("/{id}/export")
  public ApiEnvelope<?> export(@PathVariable UUID id) {
    return ApiEnvelope.of(billing.export(id));
  }
}
