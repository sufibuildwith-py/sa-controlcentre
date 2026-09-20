package com.saproduction.command.employee;

import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {
  private final EmployeeService service;

  public EmployeeController(EmployeeService service) {
    this.service = service;
  }

  @GetMapping
  public ApiEnvelope<List<EmployeeDtos.View>> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Employee.Status status) {
    return ApiEnvelope.of(service.list(search, status));
  }

  @PostMapping
  public ApiEnvelope<EmployeeDtos.View> create(@Valid @RequestBody EmployeeDtos.Input input) {
    return ApiEnvelope.of(service.create(input));
  }

  @GetMapping("/{id}")
  public ApiEnvelope<EmployeeDtos.View> get(@PathVariable UUID id) {
    return ApiEnvelope.of(service.get(id));
  }

  @PatchMapping("/{id}")
  public ApiEnvelope<EmployeeDtos.View> update(
      @PathVariable UUID id, @Valid @RequestBody EmployeeDtos.Input input) {
    return ApiEnvelope.of(service.update(id, input));
  }

  @PostMapping("/{id}/deactivate")
  public ApiEnvelope<EmployeeDtos.View> deactivate(@PathVariable UUID id) {
    return ApiEnvelope.of(service.deactivate(id));
  }
}
