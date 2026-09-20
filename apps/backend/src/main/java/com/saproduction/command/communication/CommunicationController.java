package com.saproduction.command.communication;

import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CommunicationController {
  private final CommunicationService service;

  public CommunicationController(CommunicationService service) {
    this.service = service;
  }

  @GetMapping("/messages")
  public ApiEnvelope<CommunicationService.Centre> list(
      @RequestParam(required = false) String category,
      @RequestParam(required = false) OutboundMessage.Status status,
      @RequestParam(required = false) Boolean needsAttention,
      @RequestParam(required = false) UUID employeeId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ApiEnvelope.of(service.list(category, status, needsAttention, employeeId, page, size));
  }

  @GetMapping("/messages/{id}")
  public ApiEnvelope<CommunicationService.MessageView> get(@PathVariable UUID id) {
    return ApiEnvelope.of(service.get(id));
  }

  @PostMapping("/messages/manual")
  public ApiEnvelope<Map<String, Object>> manual(
      @Valid @RequestBody CommunicationService.ManualInput input) {
    return ApiEnvelope.of(service.manual(input));
  }

  @PostMapping("/messages/{id}/retry")
  public ApiEnvelope<CommunicationService.MessageView> retry(@PathVariable UUID id) {
    return ApiEnvelope.of(service.retry(id));
  }

  @GetMapping("/notification-rules")
  public ApiEnvelope<List<CommunicationService.RuleView>> rules() {
    return ApiEnvelope.of(service.rules());
  }

  @PatchMapping("/notification-rules/{id}")
  public ApiEnvelope<CommunicationService.RuleView> rule(
      @PathVariable UUID id, @Valid @RequestBody CommunicationService.RuleInput input) {
    return ApiEnvelope.of(service.updateRule(id, input));
  }
}
