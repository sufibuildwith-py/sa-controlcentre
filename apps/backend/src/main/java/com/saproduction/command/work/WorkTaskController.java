package com.saproduction.command.work;

import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
public class WorkTaskController {
  private final WorkTaskService service;

  public WorkTaskController(WorkTaskService service) {
    this.service = service;
  }

  @GetMapping
  public ApiEnvelope<List<WorkTaskService.View>> list(
      @RequestParam(required = false) UUID assignee,
      @RequestParam(required = false) UUID production,
      @RequestParam(required = false) WorkTask.Status status,
      @RequestParam(required = false) WorkTask.Priority priority,
      @RequestParam(required = false) Instant dueFrom,
      @RequestParam(required = false) Instant dueTo,
      @RequestParam(required = false) Boolean overdue) {
    return ApiEnvelope.of(
        service.list(assignee, production, status, priority, dueFrom, dueTo, overdue));
  }

  @PostMapping
  public ApiEnvelope<WorkTaskService.View> create(@Valid @RequestBody WorkTaskService.Input input) {
    return ApiEnvelope.of(service.create(input));
  }

  @GetMapping("/{id}")
  public ApiEnvelope<WorkTaskService.View> get(@PathVariable UUID id) {
    return ApiEnvelope.of(service.get(id));
  }

  @PatchMapping("/{id}")
  public ApiEnvelope<WorkTaskService.View> update(
      @PathVariable UUID id, @Valid @RequestBody WorkTaskService.Input input) {
    return ApiEnvelope.of(service.update(id, input));
  }

  @PostMapping("/{id}/updates")
  public ApiEnvelope<WorkTaskService.View> progress(
      @PathVariable UUID id, @Valid @RequestBody WorkTaskService.UpdateInput input) {
    return ApiEnvelope.of(service.progress(id, input));
  }
}
