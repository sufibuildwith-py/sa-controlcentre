package com.saproduction.command.finance;

import com.saproduction.command.shared.ApiEnvelope;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

/** Contextual Finance read surface in existing Production and People detail screens. */
@RestController
public class FinanceIntegrationController {
  private final FinanceReadService reads;
  public FinanceIntegrationController(FinanceReadService reads) { this.reads = reads; }
  @GetMapping("/api/v1/productions/{id}/finance") public ApiEnvelope<?> production(@PathVariable UUID id) { return ApiEnvelope.of(reads.production(id)); }
  @GetMapping("/api/v1/employees/{id}/finance") public ApiEnvelope<?> employee(@PathVariable UUID id) { return ApiEnvelope.of(reads.employee(id)); }
}
