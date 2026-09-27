package com.saproduction.command.employee;

import com.saproduction.command.employee.Employee360Dto.Employee360View;
import com.saproduction.command.shared.ApiEnvelope;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employees/{id}/360")
public class Employee360Controller {

  private final Employee360Service service;

  public Employee360Controller(Employee360Service service) {
    this.service = service;
  }

  @GetMapping
  public ApiEnvelope<Employee360View> get(@PathVariable UUID id) {
    return ApiEnvelope.of(service.get360(id));
  }
}
