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
class FinanceWorkbookOwnerIntegrationTest {
  @Autowired FinanceMigrationService migration;
  @Autowired FinanceWorkbookOwnerService owners;
  @Autowired FinanceWorkbookProductionService productions;
  @Autowired JdbcTemplate jdbc;

  @Test
  void realWorkbookOwnerSegmentsTransfersAndLinks() throws Exception {
    migration.preview("historical.xlsx", Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(System.getenv("FINANCE_WORKBOOK_PATH")))));
    long postedBefore = jdbc.queryForObject("SELECT count(*) FROM finance_transactions", Long.class);
    var summary = owners.summary();
    assertThat(summary.get("available")).isEqualTo(true);
    @SuppressWarnings("unchecked") var accounts = (List<Map<String, Object>>) summary.get("owners");
    var az = accounts.get(0);
    var ak = accounts.get(1);
    assertThat(az.get("previousPosition")).isEqualTo(new BigDecimal("258849"));
    assertThat(az.get("openingReference")).isEqualTo(new BigDecimal("258849"));
    assertThat(az.get("continuity")).isEqualTo("VERIFIED");
    assertThat((Long) az.get("earlierRows")).isGreaterThan(0);
    assertThat((Long) az.get("currentRows")).isGreaterThan(0);
    assertThat((BigDecimal) az.get("moneyIn")).isEqualByComparingTo("2457885");
    assertThat((BigDecimal) az.get("moneyOut")).isEqualByComparingTo("4155407");
    assertThat((BigDecimal) az.get("position")).isEqualByComparingTo("-1697522");
    assertThat((BigDecimal) az.get("newMoneyIn")).isEqualByComparingTo("2199036");
    assertThat(ak.get("continuity")).isEqualTo("NOT_PROVEN_BY_EARLIER_SHEET");
    assertThat((Long) ak.get("earlierRows")).isGreaterThan(0);
    assertThat((Long) ak.get("currentRows")).isGreaterThan(0);
    assertThat((BigDecimal) ak.get("moneyIn")).isEqualByComparingTo("529760");
    assertThat((BigDecimal) ak.get("moneyOut")).isEqualByComparingTo("425665");
    assertThat((BigDecimal) ak.get("position")).isEqualByComparingTo("104095");
    @SuppressWarnings("unchecked") var akSegments = (List<Map<String, Object>>) ak.get("segments");
    assertThat(akSegments).anySatisfy(segment -> {
      assertThat(segment.get("sheet")).isEqualTo("Ak-2");
      assertThat(segment.get("monthKey")).isEqualTo("2026-09");
    });
    @SuppressWarnings("unchecked") var parity = (List<Map<String, Object>>) summary.get("parity");
    assertThat(parity).allSatisfy(item -> assertThat(item.get("status")).isEqualTo("MATCH"));

    var july = owners.movements("AK", "2026-07", "", "", "", "", "", "", 0, 50);
    @SuppressWarnings("unchecked") var julyRows = (List<Map<String, Object>>) july.get("items");
    assertThat(julyRows).allSatisfy(row -> assertThat(row.get("sheet")).isEqualTo("Ak-2"));
    assertThat(owners.movements("AK", "", "", "", "", "", "", "Govind", 0, 50).get("total")).isNotEqualTo(0L);
    assertThat(owners.movements("AK", "", "", "", "", "", "", "Ramada", 0, 50).get("total")).isNotEqualTo(0L);
    var azFirst = owners.movements("AZ", "", "", "", "", "", "", "", 0, 1);
    assertThat(azFirst.get("total")).isNotEqualTo(0L);
    @SuppressWarnings("unchecked") var firstItems = (List<Map<String, Object>>) azFirst.get("items");
    assertThat(firstItems).hasSize(1);
    assertThat(owners.movements("AZ", "", "", "", "", "", "", "", 1, 1).get("items")).isNotEqualTo(azFirst.get("items"));
    assertThat(owners.movements("AK", "", "", "", "", "LEGACY", "", "", 0, 50).get("total")).isNotEqualTo(0L);
    assertThat(owners.movements("AK", "", "", "", "IN", "", "PRODUCTION", "", 0, 50).get("total")).isNotEqualTo(0L);
    assertThat(summary.get("transferCount")).isEqualTo(7L);
    assertThat((BigDecimal) summary.get("transferTotal")).isEqualByComparingTo("185090");
    var transfers = owners.transfers(0, 50);
    @SuppressWarnings("unchecked") var transferRows = (List<Map<String, Object>>) transfers.get("items");
    assertThat(transferRows).hasSize(7).allSatisfy(row -> assertThat(row).containsKeys("primarySheet", "otherSheet", "primaryRange", "otherRange"));

    @SuppressWarnings("unchecked") var govindRows = (List<Map<String, Object>>) productions.list("JUL+DEC", "Govind", 0, 50).get("items");
    var govind = govindRows.stream().filter(row -> ((Number) row.get("sourceRow")).intValue() == 8).findFirst().orElseThrow();
    @SuppressWarnings("unchecked") var links = (List<Map<String, Object>>) productions.detail((UUID) govind.get("id")).get("linkedOwnerEvidence");
    var akLink = links.stream().filter(link -> "AK-2".equals(link.get("accountCode"))).findFirst().orElseThrow();
    assertThat(akLink.get("sourceCell")).isNotNull();
    var movement = owners.movement((UUID) akLink.get("movementId"));
    @SuppressWarnings("unchecked") var business = (List<Map<String, Object>>) movement.get("linkedBusiness");
    assertThat(business).anySatisfy(row -> assertThat(row.get("rowId")).isEqualTo(govind.get("id")));
    assertThat(movement).containsKeys("raw", "workbookSha256", "sourceRange", "sourceCell", "positionAfter", "period", "status");
    assertThat(movement.get("sourceCell")).isNotNull();
    @SuppressWarnings("unchecked") var raw = (Map<String, Object>) movement.get("raw");
    assertThat(raw).containsKeys("cells", "formulas");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_transactions", Long.class)).isEqualTo(postedBefore);
  }
}
