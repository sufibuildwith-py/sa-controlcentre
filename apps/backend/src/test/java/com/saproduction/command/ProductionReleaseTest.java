package com.saproduction.command;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.mode=production",
      "app.deployment=desktop",
      "app.demo-seed=false",
      "app.messaging.provider=unconfigured",
      "app.messaging.worker-enabled=false",
      "MESSAGING_PROVIDER=unconfigured",
      "DESKTOP_ORIGINS=tauri://localhost",
      "SA_DESKTOP_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    })
@Testcontainers(disabledWithoutDocker = true)
class ProductionReleaseTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", postgres::getJdbcUrl);
    p.add("DATABASE_URL", postgres::getJdbcUrl);
    p.add("spring.datasource.username", postgres::getUsername);
    p.add("DATABASE_USERNAME", postgres::getUsername);
    p.add("spring.datasource.password", postgres::getPassword);
    p.add("DATABASE_PASSWORD", postgres::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @LocalServerPort int port;
  final HttpClient client = HttpClient.newHttpClient();

  @Test
  void firstOwnerSetupIsPrivateSingleUseAndStartsWithNoBusinessData() throws Exception {
    for (String table :
        new String[] {
          "users",
          "employees",
          "attendance_records",
          "productions",
          "tasks",
          "meetings",
          "payroll_periods",
          "outbound_messages"
        })
      assertThat(jdbc.queryForObject("select count(*) from " + table, Long.class))
          .as(table)
          .isZero();
    assertThat(call("GET", "/owner-setup", null, null, false).statusCode()).isEqualTo(403);
    assertThat(
            json.readTree(call("GET", "/owner-setup", null, null, true).body())
                .path("data")
                .path("required")
                .asBoolean())
        .isTrue();
    String password = java.util.UUID.randomUUID() + "!aB9";
    String body =
        json.writeValueAsString(java.util.Map.of("password", password, "confirmation", password));
    var setup = call("POST", "/owner-setup", body, null, true);
    assertThat(setup.statusCode()).isEqualTo(200);
    String token = json.readTree(setup.body()).path("data").path("token").asText();
    assertThat(token).hasSize(64);
    assertThat(call("GET", "/auth/me", null, token, true).body()).contains("Azeem Khan");
    assertThat(call("POST", "/owner-setup", body, null, true).statusCode()).isEqualTo(409);
    assertThat(call("GET", "/demo/messages", null, token, true).statusCode()).isIn(404, 500);
    assertThat(call("POST", "/auth/logout", "{}", token, true).statusCode()).isEqualTo(200);
    assertThat(call("GET", "/auth/me", null, token, true).statusCode()).isIn(401, 403);
    assertThat(call("GET", "/auth/me", null, "invalid", true).statusCode()).isIn(401, 403);
    var login =
        call(
            "POST",
            "/auth/login",
            json.writeValueAsString(
                java.util.Map.of("email", "azeem@sa-command.local", "password", password)),
            null,
            true);
    assertThat(login.statusCode()).isEqualTo(200);
    String next = json.readTree(login.body()).path("data").path("token").asText();
    assertThat(next).isNotEqualTo(token);
    jdbc.update(
        "update api_sessions set created_at=now()-interval '2 hours', expires_at=now()-interval '1 second'");
    assertThat(call("GET", "/auth/me", null, next, true).statusCode()).isIn(401, 403);
  }

  private HttpResponse<String> call(
      String method, String path, String body, String token, boolean nativeKey) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
            .header("Content-Type", "application/json");
    if (nativeKey)
      b.header(
          "X-SA-Desktop-Key", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    if (token != null) b.header("Authorization", "Bearer " + token);
    return client.send(
        b.method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
