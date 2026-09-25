package com.saproduction.command.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** Explicitly opt in with an isolated database and the private workbook path. */
@EnabledIfEnvironmentVariable(named = "FINANCE_WORKBOOK_PATH", matches = ".+")
@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_DB_URL", matches = ".+")
@SpringBootTest
@TestPropertySource(properties = {"app.mode=test", "app.demo-seed=false", "app.messaging.worker-enabled=false", "app.navigator.enabled=false"})
class FinanceWorkbookResolutionTest {
  @Autowired FinanceMigrationService migration;
  @Autowired JdbcTemplate jdbc;

  @Test
  void realWorkbookResolvesDeterministicallyWithoutPostingUnreviewedMoney() throws Exception {
    byte[] workbook = Files.readAllBytes(Path.of(System.getenv("FINANCE_WORKBOOK_PATH")));
    String encoded = Base64.getEncoder().encodeToString(workbook);
    var first = migration.preview("historical.xlsx",encoded);
    var second = migration.preview("historical.xlsx",encoded);
    UUID batch = (UUID)((Map<?,?>)first.get("batch")).get("id");
    assertThat(((Map<?,?>)second.get("batch")).get("id")).isEqualTo(batch);
    long rows = jdbc.queryForObject("SELECT count(*) FROM finance_migration_rows WHERE batch_id=?",Long.class,batch);
    long links = jdbc.queryForObject("SELECT count(*) FROM finance_migration_links WHERE batch_id=?",Long.class,batch);
    long canonical = jdbc.queryForObject("SELECT count(*) FROM finance_migration_facts WHERE batch_id=? AND canonical_transaction_id IS NOT NULL",Long.class,batch);
    assertThat(rows).isGreaterThan(6000);
    assertThat(links).isEqualTo(jdbc.queryForObject("SELECT count(*) FROM finance_migration_links WHERE batch_id=?",Long.class,batch));
    assertThat(canonical).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_migration_identity_links WHERE batch_id=?",Long.class,batch)).isGreaterThan(100L);
    System.out.println("WORKBOOK_ROWS="+rows+" LINKS="+links+" CANONICAL_POSTED="+canonical);
    System.out.println("WORKBOOK_CLASSES="+jdbc.queryForList("SELECT classification,count(*) AS count FROM finance_migration_facts WHERE batch_id=? GROUP BY classification ORDER BY classification",batch));
    System.out.println("WORKBOOK_ISSUES="+jdbc.queryForList("SELECT issue_code,count(*) AS count FROM finance_migration_facts WHERE batch_id=? AND issue_code IS NOT NULL GROUP BY issue_code ORDER BY issue_code",batch));
    var report = migration.report(batch);
    migration.resetStaging();
    var cleanReplay = migration.preview("historical.xlsx",encoded);
    UUID replayBatch = (UUID)((Map<?,?>)cleanReplay.get("batch")).get("id");
    assertThat(replayBatch).isNotEqualTo(batch);
    assertThat(cleanReplay.get("resolution")).isEqualTo(report.get("resolution"));
    assertThat(cleanReplay.get("parity")).isEqualTo(report.get("parity"));
    System.out.println("WORKBOOK_CLEAN_REPLAY=IDENTICAL");
    var posted = migration.postProven(replayBatch);
    var postedAgain = migration.postProven(replayBatch);
    @SuppressWarnings("unchecked") Map<String,Object> postedNow = (Map<String,Object>)posted.get("postedNow");
    @SuppressWarnings("unchecked") Map<String,Object> retryPosted = (Map<String,Object>)postedAgain.get("postedNow");
    assertThat((Integer)postedNow.get("equipmentPurchases")).isGreaterThan(0);
    assertThat((Integer)postedNow.get("ownerTransfers")).isGreaterThan(0);
    assertThat(retryPosted).containsEntry("equipmentPurchases",0).containsEntry("ownerTransfers",0);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_journal_entries e JOIN finance_transactions t ON t.id=e.transaction_id WHERE t.id IN (SELECT canonical_transaction_id FROM finance_migration_facts WHERE batch_id=?) AND (SELECT sum(debit-credit) FROM finance_journal_lines WHERE entry_id=e.id)<>0",Long.class,replayBatch)).isZero();
    System.out.println("WORKBOOK_PROVEN_POSTED="+postedNow+" RETRY="+retryPosted);
    System.out.println("WORKBOOK_RESOLUTION="+report.get("resolution"));
    System.out.println("WORKBOOK_PARITY="+report.get("parity"));
  }
}
