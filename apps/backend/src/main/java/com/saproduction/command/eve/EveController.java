package com.saproduction.command.eve;

import com.saproduction.command.shared.ApiException;
import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/eve")
public class EveController {

  private final EveService service;
  private final EveSuggestionService suggestionService;
  private final EveSignalService signalService;

  public EveController(EveService service) {
    this(service, null, null);
  }

  @Autowired
  public EveController(
      EveService service,
      @Autowired(required = false) EveSuggestionService suggestionService,
      @Autowired(required = false) EveSignalService signalService) {
    this.service = service;
    this.suggestionService = suggestionService;
    this.signalService = signalService;
  }

  @PostMapping("/query")
  public ApiEnvelope<EveDtos.QueryResponse> query(@Valid @RequestBody EveDtos.QueryRequest request) {
    return ApiEnvelope.of(service.query(request));
  }

  @GetMapping("/status")
  public ApiEnvelope<EveDtos.EveStatusView> getStatus() {
    return ApiEnvelope.of(service.getStatus());
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

  // -------------------------------------------------------------
  // Phase 4 Continuous Intelligence & Proactive Suggestions
  // -------------------------------------------------------------

  @GetMapping("/suggestions")
  public ApiEnvelope<List<EveDtos.SuggestionView>> listSuggestions(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String domain,
      @RequestParam(required = false) String priority) {
    if (suggestionService == null) {
      return ApiEnvelope.of(List.of());
    }
    return ApiEnvelope.of(suggestionService.listSuggestions(status, domain, priority));
  }

  @GetMapping("/suggestions/{id}")
  public ApiEnvelope<EveDtos.SuggestionView> getSuggestion(@PathVariable UUID id) {
    if (suggestionService == null) {
      throw ApiException.notFound("SUGGESTION_NOT_FOUND", "Suggestion service not available");
    }
    return ApiEnvelope.of(suggestionService.getSuggestion(id));
  }

  @PostMapping("/suggestions/{id}/dismiss")
  public ApiEnvelope<EveDtos.SuggestionView> dismissSuggestion(
      @PathVariable UUID id,
      @RequestBody(required = false) EveDtos.DismissSuggestionRequest request) {
    if (suggestionService == null) {
      throw ApiException.notFound("SUGGESTION_NOT_FOUND", "Suggestion service not available");
    }
    String reason = request != null ? request.reason() : null;
    return ApiEnvelope.of(suggestionService.dismissSuggestion(id, reason));
  }

  @PostMapping("/suggestions/{id}/resolve")
  public ApiEnvelope<EveDtos.SuggestionView> resolveSuggestion(
      @PathVariable UUID id,
      @RequestBody(required = false) EveDtos.ResolveSuggestionRequest request) {
    if (suggestionService == null) {
      throw ApiException.notFound("SUGGESTION_NOT_FOUND", "Suggestion service not available");
    }
    String note = request != null ? request.note() : null;
    return ApiEnvelope.of(suggestionService.resolveSuggestion(id, note));
  }

  @PostMapping("/suggestions/evaluate")
  public ApiEnvelope<List<EveDtos.SuggestionView>> evaluateSuggestions() {
    if (suggestionService == null) {
      return ApiEnvelope.of(List.of());
    }
    return ApiEnvelope.of(suggestionService.evaluateAll());
  }

  @PostMapping("/signals")
  public ApiEnvelope<EveDtos.SignalView> emitSignal(@Valid @RequestBody EveDtos.EmitSignalRequest request) {
    if (signalService == null) {
      throw ApiException.badRequest("SIGNAL_SERVICE_UNAVAILABLE", "Signal service not available");
    }
    return ApiEnvelope.of(signalService.emit(request));
  }

  @GetMapping("/signals")
  public ApiEnvelope<List<EveDtos.SignalView>> listSignals(@RequestParam(defaultValue = "20") int limit) {
    if (signalService == null) {
      return ApiEnvelope.of(List.of());
    }
    return ApiEnvelope.of(signalService.listRecentSignals(limit));
  }
}
