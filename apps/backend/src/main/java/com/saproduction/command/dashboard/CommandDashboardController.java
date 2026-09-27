package com.saproduction.command.dashboard;

import com.saproduction.command.dashboard.CommandDashboardDto.CommandDashboard;
import com.saproduction.command.shared.ApiEnvelope;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the Command Dashboard V1. Exposes GET /api/v1/command/dashboard with explicit
 * date semantics.
 */
@RestController
@RequestMapping("/api/v1/command/dashboard")
public class CommandDashboardController {

  private final CommandDashboardReadService service;

  public CommandDashboardController(CommandDashboardReadService service) {
    this.service = service;
  }

  @GetMapping
  public ApiEnvelope<CommandDashboard> getDashboard(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return ApiEnvelope.of(service.getDashboard(date));
  }
}
