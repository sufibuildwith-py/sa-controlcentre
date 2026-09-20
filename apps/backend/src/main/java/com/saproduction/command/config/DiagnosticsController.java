package com.saproduction.command.config;

import com.saproduction.command.shared.ApiEnvelope;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system")
public class DiagnosticsController {
  private final JdbcTemplate jdbc;
  private final String mode;
  private final String provider;

  public DiagnosticsController(
      JdbcTemplate jdbc,
      @Value("${app.mode}") String mode,
      @Value("${app.messaging.provider}") String provider) {
    this.jdbc = jdbc;
    this.mode = mode;
    this.provider = provider;
  }

  @GetMapping("/diagnostics")
  public ApiEnvelope<Map<String, String>> diagnostics() {
    String database;
    try {
      database = jdbc.queryForObject("select 'UP'", String.class);
    } catch (Exception ignored) {
      database = "DOWN";
    }
    return ApiEnvelope.of(
        Map.of("api", "UP", "database", database, "appMode", mode, "messagingProvider", provider));
  }
}
