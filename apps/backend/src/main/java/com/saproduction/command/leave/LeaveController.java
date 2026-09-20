package com.saproduction.command.leave;

import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/leave-requests")
public class LeaveController {
  private final LeaveService service;

  public LeaveController(LeaveService service) {
    this.service = service;
  }

  @GetMapping
  public ApiEnvelope<List<LeaveService.View>> list() {
    return ApiEnvelope.of(service.list());
  }

  @PostMapping
  public ApiEnvelope<LeaveService.View> create(@Valid @RequestBody LeaveService.CreateInput input) {
    return ApiEnvelope.of(service.create(input));
  }

  @PostMapping("/{id}/approve")
  public ApiEnvelope<LeaveService.View> approve(
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) LeaveService.ResolveInput input) {
    return ApiEnvelope.of(
        service.approve(id, input == null ? new LeaveService.ResolveInput(null) : input));
  }

  @PostMapping("/{id}/reject")
  public ApiEnvelope<LeaveService.View> reject(
      @PathVariable UUID id,
      @Valid @RequestBody(required = false) LeaveService.ResolveInput input) {
    return ApiEnvelope.of(
        service.reject(id, input == null ? new LeaveService.ResolveInput(null) : input));
  }
}
