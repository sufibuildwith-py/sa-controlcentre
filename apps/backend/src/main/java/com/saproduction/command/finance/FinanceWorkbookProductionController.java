package com.saproduction.command.finance;

import com.saproduction.command.shared.ApiEnvelope;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/workbook/productions")
@ConditionalOnProperty(name = "app.mode", havingValue = "demo")
public class FinanceWorkbookProductionController {
  private final FinanceWorkbookProductionService service;

  public FinanceWorkbookProductionController(FinanceWorkbookProductionService service) {
    this.service = service;
  }

  @GetMapping
  public ApiEnvelope<Map<String, Object>> list(@RequestParam(defaultValue = "") String sheet,
      @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ApiEnvelope.of(service.list(sheet, search, page, size));
  }

  @GetMapping("/{id}")
  public ApiEnvelope<Map<String, Object>> detail(@PathVariable UUID id) {
    return ApiEnvelope.of(service.detail(id));
  }
}
