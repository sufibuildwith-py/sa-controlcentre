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
@RequestMapping("/api/v1/finance/workbook/employees")
@ConditionalOnProperty(name = "app.mode", havingValue = "demo")
public class FinanceWorkbookEmployeeController {
  private final FinanceWorkbookEmployeeService service;

  public FinanceWorkbookEmployeeController(FinanceWorkbookEmployeeService service) { this.service = service; }

  @GetMapping
  public ApiEnvelope<Map<String,Object>> summary() { return ApiEnvelope.of(service.summary()); }

  @GetMapping("/events")
  public ApiEnvelope<Map<String,Object>> events(@RequestParam(defaultValue = "") String employee,
      @RequestParam(defaultValue = "") String sheet, @RequestParam(defaultValue = "") String kind,
      @RequestParam(defaultValue = "") String search, @RequestParam(defaultValue = "") String from,
      @RequestParam(defaultValue = "") String to, @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ApiEnvelope.of(service.events(employee,sheet,kind,search,from,to,page,size));
  }

  @GetMapping("/events/{id}")
  public ApiEnvelope<Map<String,Object>> detail(@PathVariable UUID id) { return ApiEnvelope.of(service.detail(id)); }

  @GetMapping("/salary-evidence")
  public ApiEnvelope<Map<String,Object>> salaryEvidence(@RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) { return ApiEnvelope.of(service.salaryEvidence(page,size)); }
}
