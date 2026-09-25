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
@RequestMapping("/api/v1/finance/workbook/owners")
@ConditionalOnProperty(name = "app.mode", havingValue = "demo")
public class FinanceWorkbookOwnerController {
  private final FinanceWorkbookOwnerService service;

  public FinanceWorkbookOwnerController(FinanceWorkbookOwnerService service) {
    this.service = service;
  }

  @GetMapping
  public ApiEnvelope<Map<String, Object>> summary() {
    return ApiEnvelope.of(service.summary());
  }

  @GetMapping("/{owner}/movements")
  public ApiEnvelope<Map<String, Object>> movements(@PathVariable String owner,
      @RequestParam(defaultValue = "") String month, @RequestParam(defaultValue = "") String from,
      @RequestParam(defaultValue = "") String to, @RequestParam(defaultValue = "") String direction,
      @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String businessType,
      @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ApiEnvelope.of(service.movements(owner, month, from, to, direction, status, businessType, search, page, size));
  }

  @GetMapping("/movements/{id}")
  public ApiEnvelope<Map<String, Object>> movement(@PathVariable UUID id) {
    return ApiEnvelope.of(service.movement(id));
  }

  @GetMapping("/transfers")
  public ApiEnvelope<Map<String, Object>> transfers(@RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ApiEnvelope.of(service.transfers(page, size));
  }
}
