package com.saproduction.command.dashboard;
import com.saproduction.command.shared.ApiEnvelope;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/dashboard")
public class DashboardController {
  private final DashboardService service;public DashboardController(DashboardService service){this.service=service;}
  @GetMapping public ApiEnvelope<DashboardService.View> dashboard(){return ApiEnvelope.of(service.dashboard());}
  @GetMapping("/attention") public ApiEnvelope<List<DashboardService.Attention>> attention(){return ApiEnvelope.of(service.attention(LocalDate.now()));}
}
