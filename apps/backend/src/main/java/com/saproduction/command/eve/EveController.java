package com.saproduction.command.eve;

import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/eve")
public class EveController {

  private final EveService service;

  public EveController(EveService service) {
    this.service = service;
  }

  @PostMapping("/query")
  public ApiEnvelope<EveDtos.QueryResponse> query(@Valid @RequestBody EveDtos.QueryRequest request) {
    return ApiEnvelope.of(service.query(request));
  }

  @GetMapping("/sessions")
  public ApiEnvelope<List<EveDtos.SessionView>> listSessions() {
    return ApiEnvelope.of(service.listSessions());
  }

  @GetMapping("/sessions/{id}")
  public ApiEnvelope<EveDtos.SessionView> getSession(@PathVariable UUID id) {
    return ApiEnvelope.of(service.getSession(id));
  }

  @PostMapping("/sessions")
  public ApiEnvelope<EveDtos.SessionView> createSession(
      @RequestBody(required = false) EveDtos.CreateSessionRequest request) {
    String title = request != null ? request.title() : null;
    return ApiEnvelope.of(service.createSession(title));
  }

  @GetMapping("/memory")
  public ApiEnvelope<List<EveDtos.MemoryView>> listMemories() {
    return ApiEnvelope.of(service.listMemories());
  }

  @PostMapping("/memory")
  public ApiEnvelope<EveDtos.MemoryView> remember(@Valid @RequestBody EveDtos.MemoryRequest request) {
    return ApiEnvelope.of(service.remember(request));
  }

  @DeleteMapping("/memory/{id}")
  public ApiEnvelope<Boolean> deleteMemory(@PathVariable UUID id) {
    return ApiEnvelope.of(service.deleteMemory(id));
  }

  @PostMapping("/plans/{id}/confirm")
  public ApiEnvelope<EveDtos.PlanExecutionResponse> confirmPlan(
      @PathVariable UUID id,
      @Valid @RequestBody EveDtos.ConfirmPlanRequest request) {
    return ApiEnvelope.of(service.confirmPlan(id, request));
  }

  @PostMapping("/plans/{id}/cancel")
  public ApiEnvelope<EveDtos.EvePlan> cancelPlan(
      @PathVariable UUID id,
      @Valid @RequestBody EveDtos.CancelPlanRequest request) {
    return ApiEnvelope.of(service.cancelPlan(id, request));
  }

  @GetMapping("/plans/{id}")
  public ApiEnvelope<EveDtos.EvePlan> getPlan(@PathVariable UUID id) {
    return ApiEnvelope.of(service.getPlan(id));
  }

  @GetMapping("/sessions/{sessionId}/plans")
  public ApiEnvelope<List<EveDtos.EvePlan>> listPlansForSession(@PathVariable UUID sessionId) {
    return ApiEnvelope.of(service.listPlansForSession(sessionId));
  }
}
