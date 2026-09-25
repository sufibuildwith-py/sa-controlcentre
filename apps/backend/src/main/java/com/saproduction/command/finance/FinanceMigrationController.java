package com.saproduction.command.finance;

import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** Workbook staging is deliberately absent from non-demo runtime modes. */
@RestController
@RequestMapping("/api/v1/finance/migrations")
@ConditionalOnProperty(name = "app.mode", havingValue = "demo")
public class FinanceMigrationController {
  public record WorkbookInput(@NotBlank String filename, @NotBlank String base64) {}
  public record ReviewDecision(@NotBlank String sheetName, int sourceRow, @NotBlank String slot,
                               @NotBlank String action, String target, @NotBlank String reason) {}
  private final FinanceMigrationService migration;
  public FinanceMigrationController(FinanceMigrationService migration) { this.migration = migration; }
  @PostMapping("/excel/preview") public ApiEnvelope<Map<String,Object>> preview(@Valid @RequestBody WorkbookInput input) { return ApiEnvelope.of(migration.preview(input.filename(),input.base64())); }
  @PostMapping("/reset") public ApiEnvelope<Map<String,Object>> reset() { return ApiEnvelope.of(migration.resetStaging()); }
  @GetMapping("/{id}") public ApiEnvelope<Map<String,Object>> report(@PathVariable UUID id) { return ApiEnvelope.of(migration.report(id)); }
  @PostMapping("/{id}/validate") public ApiEnvelope<Map<String,Object>> validate(@PathVariable UUID id) { return ApiEnvelope.of(migration.validate(id)); }
  @PostMapping("/{id}/post-proven") public ApiEnvelope<Map<String,Object>> postProven(@PathVariable UUID id) { return ApiEnvelope.of(migration.postProven(id)); }
  @GetMapping("/{id}/rows") public ApiEnvelope<Map<String,Object>> rows(@PathVariable UUID id,@RequestParam String sheet,@RequestParam(defaultValue="0") int page) { return ApiEnvelope.of(migration.rows(id,sheet,page)); }
  @GetMapping("/{id}/review") public ApiEnvelope<Map<String,Object>> review(@PathVariable UUID id,@RequestParam(defaultValue="0") int page) { return ApiEnvelope.of(migration.review(id,page)); }
  @PostMapping("/{id}/review") public ApiEnvelope<Map<String,Object>> decide(@PathVariable UUID id,@Valid @RequestBody ReviewDecision decision) {
    return ApiEnvelope.of(migration.decide(id,decision.sheetName(),decision.sourceRow(),decision.slot(),decision.action(),decision.target(),decision.reason()));
  }
}
