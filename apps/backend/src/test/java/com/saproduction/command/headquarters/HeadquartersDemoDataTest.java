package com.saproduction.command.headquarters;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
      "app.mode=demo",
      "app.demo-seed=true",
      "app.messaging.worker-enabled=false",
      "app.navigator.enabled=false"
    })
@Testcontainers(disabledWithoutDocker = true)
class HeadquartersDemoDataTest {
  @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", postgres::getJdbcUrl);
    properties.add("spring.datasource.username", postgres::getUsername);
    properties.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired JdbcTemplate jdbc;

  @Test
  void demoModeSeedsACompleteHeadquartersScenario() {
    assertThat(count("hq_equipment")).isEqualTo(8);
    assertThat(count("hq_serialized_assets")).isEqualTo(5);
    assertThat(count("hq_reservations")).isGreaterThanOrEqualTo(1);
    assertThat(count("hq_dispatches")).isGreaterThanOrEqualTo(1);
    assertThat(count("hq_returns")).isGreaterThanOrEqualTo(1);
    assertThat(count("hq_transfers")).isGreaterThanOrEqualTo(1);
    assertThat(count("hq_maintenance")).isEqualTo(1);
    assertThat(count("hq_issues")).isGreaterThanOrEqualTo(2);
    assertThat(count("hq_inventory_movements")).isGreaterThan(10);
    assertThat(
            jdbc.queryForList(
                "SELECT ownership_default FROM hq_equipment", String.class))
        .contains("SA_OWNED", "RENTED", "CLIENT_SUPPLIED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM hq_equipment WHERE internal_code LIKE 'DEMO-HQ-%'",
                Long.class))
        .isEqualTo(8);
  }

  private long count(String table) {
    Long value = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    return value == null ? 0 : value;
  }
}
