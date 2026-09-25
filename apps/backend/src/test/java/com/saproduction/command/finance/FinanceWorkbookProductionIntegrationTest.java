package com.saproduction.command.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Real workbook parity on an isolated PostgreSQL database only. */
@EnabledIfEnvironmentVariable(named = "FINANCE_WORKBOOK_PATH", matches = ".+")
@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_DB_URL", matches = ".+")
@SpringBootTest
@TestPropertySource(properties = {"app.mode=demo", "app.demo-seed=false", "app.messaging.worker-enabled=false", "app.navigator.enabled=false"})
class FinanceWorkbookProductionIntegrationTest {
  @Autowired FinanceMigrationService migration;
  @Autowired FinanceWorkbookProductionService productions;

  @Test
  void originalProductionRowsRemainSearchableWithWorkbookFormulaEvidence() throws Exception {
    byte[] bytes = Files.readAllBytes(Path.of(System.getenv("FINANCE_WORKBOOK_PATH")));
    migration.preview("historical.xlsx", Base64.getEncoder().encodeToString(bytes));
    var all = productions.list("", "", 0, 50);
    assertThat(all).containsEntry("available", true);
    assertThat((Long) all.get("total")).isGreaterThan(100L);

    var jan = productions.list("Jan 26", "", 0, 50);
    @SuppressWarnings("unchecked") var control = (Map<String, Object>) jan.get("sourceControl");
    assertThat(control.get("sourceRow")).isEqualTo(3);
    assertThat(control.get("contracted")).isEqualTo(new BigDecimal("6691600"));
    assertThat(control.get("legacyBudget")).isEqualTo(new BigDecimal("1603683"));
    @SuppressWarnings("unchecked") var rows = (java.util.List<Map<String, Object>>) jan.get("items");
    assertThat(rows).isNotEmpty();
    var row = rows.getFirst();
    var detail = productions.detail((UUID) row.get("id"));
    assertThat(detail.get("sheet")).isEqualTo("Jan 26");
    assertThat(detail).containsKeys("cells", "formulas", "sourceFacts", "sourceBalance", "balanceParity");
    assertThat(productions.list("jully dec P3 Led", "", 0, 10).get("total")).isNotEqualTo(0L);
    assertThat(productions.list("Jul-Dec", "", 0, 10).get("total")).isNotEqualTo(0L);
    assertThat(productions.list("Sound Jul+dec", "", 0, 10).get("total")).isEqualTo(0L);
    @SuppressWarnings("unchecked") var surya = (java.util.List<Map<String, Object>>) productions.list("Jan 26", "Surya Hotel", 0, 50).get("items");
    assertThat(surya).isNotEmpty();
    var source = surya.stream().filter(item -> ((Number) item.get("sourceRow")).intValue() == 5).findFirst().orElseThrow();
    assertThat(source.get("total")).isEqualTo(new BigDecimal("7000"));
    assertThat(source.get("received")).isEqualTo(new BigDecimal("7000"));
    assertThat(source.get("expense")).isEqualTo(new BigDecimal("2500"));
    assertThat(source.get("legacyBudget")).isEqualTo(new BigDecimal("4500"));
    assertThat(source.get("balanceParity")).isEqualTo("MATCH");
    assertThat(source.get("budgetParity")).isEqualTo("MATCH");
    @SuppressWarnings("unchecked") var july = (java.util.List<Map<String, Object>>) productions.list("JUL+DEC", "Govind", 0, 50).get("items");
    var govind = july.stream().filter(item -> ((Number) item.get("sourceRow")).intValue() == 8).findFirst().orElseThrow();
    assertThat(govind.get("received")).isEqualTo(new BigDecimal("10000"));
    assertThat(govind.get("legacyBudget")).isEqualTo(new BigDecimal("8600"));
    assertThat(govind.get("budgetParity")).isEqualTo("MATCH");
    var govindDetail = productions.detail((UUID) govind.get("id"));
    @SuppressWarnings("unchecked") var ownerLinks = (java.util.List<Map<String, Object>>) govindDetail.get("linkedOwnerEvidence");
    assertThat(ownerLinks).anySatisfy(link -> {
      assertThat(link.get("accountCode")).isEqualTo("AK-2");
      assertThat(link.get("amount")).isEqualTo(new BigDecimal("10000.00"));
    });
    @SuppressWarnings("unchecked") var allDate = (java.util.List<Map<String, Object>>) productions.list("All Date 26", "Radisson Hotel", 0, 50).get("items");
    var radisson = allDate.stream().filter(item -> ((Number) item.get("sourceRow")).intValue() == 4).findFirst().orElseThrow();
    assertThat(radisson.get("outstanding")).isEqualTo(new BigDecimal("0"));
    assertThat(radisson.get("balanceParity")).isEqualTo("MATCH");
    assertThat(radisson.get("expense")).isNull();
    assertThat(radisson.get("legacyBudget")).isNull();
  }
}
