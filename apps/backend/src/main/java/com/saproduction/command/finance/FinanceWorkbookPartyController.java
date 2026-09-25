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
@RequestMapping("/api/v1/finance/workbook/parties")
@ConditionalOnProperty(name = "app.mode", havingValue = "demo")
public class FinanceWorkbookPartyController {
  private final FinanceWorkbookPartyService service;
  public FinanceWorkbookPartyController(FinanceWorkbookPartyService service) { this.service=service; }

  @GetMapping("/summary")
  public ApiEnvelope<Map<String,Object>> summary() { return ApiEnvelope.of(service.summary()); }

  @GetMapping
  public ApiEnvelope<Map<String,Object>> parties(@RequestParam(defaultValue="") String status,
      @RequestParam(defaultValue="") String link,@RequestParam(defaultValue="") String search,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {
    return ApiEnvelope.of(service.parties(status,link,search,page,size));
  }

  @GetMapping("/blocks")
  public ApiEnvelope<Map<String,Object>> blocks(@RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="50") int size) { return ApiEnvelope.of(service.blocks(page,size)); }

  @GetMapping("/entries/{id}")
  public ApiEnvelope<Map<String,Object>> entry(@PathVariable UUID id) { return ApiEnvelope.of(service.entry(id)); }

  @GetMapping("/{key}/entries")
  public ApiEnvelope<Map<String,Object>> entries(@PathVariable String key,
      @RequestParam(defaultValue="") String block,@RequestParam(defaultValue="") String from,
      @RequestParam(defaultValue="") String to,@RequestParam(defaultValue="") String search,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {
    return ApiEnvelope.of(service.entries(key,block,from,to,search,page,size));
  }

  @GetMapping("/{key}")
  public ApiEnvelope<Map<String,Object>> party(@PathVariable String key) { return ApiEnvelope.of(service.party(key)); }
}
