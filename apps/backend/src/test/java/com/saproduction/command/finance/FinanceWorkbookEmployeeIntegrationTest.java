package com.saproduction.command.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@EnabledIfEnvironmentVariable(named = "FINANCE_WORKBOOK_PATH", matches = ".+")
@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_DB_URL", matches = ".+")
@SpringBootTest
@TestPropertySource(properties = {"app.mode=demo", "app.demo-seed=false", "app.messaging.worker-enabled=false", "app.navigator.enabled=false"})
class FinanceWorkbookEmployeeIntegrationTest {
  @Autowired FinanceMigrationService migration;
  @Autowired FinanceWorkbookEmployeeService employees;
  @Autowired FinanceWorkbookProductionService productions;
  @Autowired FinanceWorkbookOwnerService owners;
  @Autowired JdbcTemplate jdbc;

  @Test
  void realWorkbookStaffFactsRemainReadOnlyAndTraceable() throws Exception {
    migration.preview("historical.xlsx",Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(System.getenv("FINANCE_WORKBOOK_PATH")))));
    long journalsBefore = jdbc.queryForObject("SELECT count(*) FROM finance_transactions",Long.class);
    var summary = employees.summary();
    assertThat(summary.get("available")).isEqualTo(true);
    @SuppressWarnings("unchecked") var people = (List<Map<String,Object>>)summary.get("employees");
    assertThat(people).anySatisfy(person -> {
      assertThat(person.get("key")).isEqualTo("AAKASH_STAFF");
      assertThat(person.get("name")).isEqualTo("Aakash · staff");
    });
    assertThat(people).anySatisfy(person -> assertThat(person.get("key")).isEqualTo("ROSHAN"));
    assertThat((BigDecimal)people.stream().filter(person -> person.get("key").equals("ASIF")).findFirst().orElseThrow().get("outstanding")).isNegative();
    assertThat((Long)summary.get("reviewFacts")).isGreaterThan(0);
    assertThat((Long)summary.get("linkedPayments")).isGreaterThan(0);
    @SuppressWarnings("unchecked") var parity = (List<Map<String,Object>>)summary.get("parity");
    assertThat(parity).isNotEmpty().allSatisfy(item -> assertThat(item.get("status")).isEqualTo("MATCH"));
    var roshan = employees.events("ROSHAN","Jan 26","EARNING","",0,1);
    assertThat((Long)roshan.get("total")).isGreaterThan(0);
    @SuppressWarnings("unchecked") var items = (List<Map<String,Object>>)roshan.get("items");
    UUID id = (UUID)items.getFirst().get("id");
    var detail = employees.detail(id);
    assertThat(detail.get("sourceColumn")).isEqualTo("O");
    assertThat(detail.get("raw")).isNotNull();
    assertThat(detail.get("workbookSha256")).isNotNull();
    assertThat(productions.detail((UUID)detail.get("productionRowId")).get("employeeEvidence")).asList().isNotEmpty();
    assertThat(employees.events("AAKASH_STAFF","","","",0,50).get("total")).isNotEqualTo(0L);
    assertThat(employees.events("","","PAYMENT","Roshan",0,1).get("total")).isNotEqualTo(0L);
    assertThat(employees.events("ROSHAN","Jan 26","EARNING","","2026-01-01","2026-01-31",0,50).get("total")).isNotEqualTo(0L);
    assertThat(employees.events("","","","",0,1).get("items")).asList().hasSize(1);
    assertThat(employees.events("","","","",1,1).get("items")).isNotEqualTo(employees.events("","","","",0,1).get("items"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_transactions",Long.class)).isEqualTo(journalsBefore);
    assertThat((BigDecimal)summary.get("earned")).isGreaterThan(BigDecimal.ZERO);
    assertThat((BigDecimal)summary.get("paid")).isGreaterThan(BigDecimal.ZERO);
    assertThat((Long)employees.salaryEvidence(0,50).get("total")).isGreaterThan(0);
    assertThat(owners.summary().get("available")).isEqualTo(true);
    var matched = jdbc.queryForList("SELECT l.owner_fact_id FROM finance_workbook_employee_facts e "
        + "JOIN finance_migration_rows r ON r.id=e.row_id JOIN finance_migration_facts f ON f.row_id=e.row_id "
        + "AND f.source_range=e.source_column||r.source_row::text "
        + "JOIN finance_migration_links l ON l.domain_fact_id=f.id WHERE e.kind='PAYMENT' LIMIT 1");
    assertThat(matched).isNotEmpty();
    var ownerDetail = owners.movement((UUID)matched.getFirst().get("owner_fact_id"));
    @SuppressWarnings("unchecked") var linkedBusiness = (List<Map<String,Object>>)ownerDetail.get("linkedBusiness");
    assertThat(linkedBusiness).anySatisfy(link -> assertThat(link.get("employeeKey")).isNotNull());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_workbook_employee_facts WHERE batch_id=(SELECT id FROM finance_migration_batches ORDER BY created_at DESC LIMIT 1) AND source_column IN ('AI','AJ')",Long.class)).isZero();
    long projected = jdbc.queryForObject("SELECT count(*) FROM finance_workbook_employee_facts",Long.class);
    assertThat(employees.summary().get("parity")).isEqualTo(summary.get("parity"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_workbook_employee_facts",Long.class)).isEqualTo(projected);
  }
}
