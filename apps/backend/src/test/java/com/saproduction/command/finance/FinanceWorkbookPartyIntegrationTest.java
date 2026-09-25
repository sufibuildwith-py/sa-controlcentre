package com.saproduction.command.finance;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.transaction.annotation.Transactional;

@EnabledIfEnvironmentVariable(named="FINANCE_WORKBOOK_PATH",matches=".+")
@EnabledIfEnvironmentVariable(named="FINANCE_TEST_DB_URL",matches=".+")
@SpringBootTest
@TestPropertySource(properties={"app.mode=demo","app.demo-seed=false","app.messaging.worker-enabled=false","app.navigator.enabled=false"})
class FinanceWorkbookPartyIntegrationTest {
  @Autowired FinanceMigrationService migration;
  @Autowired FinanceWorkbookPartyService parties;
  @Autowired FinanceWorkbookProductionService productions;
  @Autowired FinanceWorkbookOwnerService owners;
  @Autowired FinanceWorkbookEmployeeService employees;
  @Autowired JdbcTemplate jdbc;

  @Test void realPartyBlocksAndSourceParity() throws Exception {
    migration.preview("historical.xlsx",Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(System.getenv("FINANCE_WORKBOOK_PATH")))));
    long postedBefore=jdbc.queryForObject("SELECT count(*) FROM finance_transactions",Long.class);
    var summary=parties.summary();
    assertThat(summary.get("available")).isEqualTo(true);
    assertThat(((Number)summary.get("partyBlocks")).longValue()).isEqualTo(22);
    assertThat(((Number)summary.get("duplicateBlocks")).longValue()).isEqualTo(1);
    assertThat(((Number)summary.get("parityMismatches")).longValue()).isZero();
    assertThat(((Number)summary.get("parties")).longValue()).isEqualTo(9);
    assertThat(((Number)summary.get("duplicateRepresentations")).longValue()).isEqualTo(15);
    assertThat(((Number)summary.get("activeEntries")).longValue()).isEqualTo(708);
    assertThat(summary.get("business").toString()).isEqualTo("9259885.60");
    assertThat(summary.get("received").toString()).isEqualTo("7543045.60");
    assertThat(summary.get("outstanding").toString()).isEqualTo("1716840.00");
    assertThat(summary.get("discountSettlement").toString()).isEqualTo("10205.60");
    assertThat(parties.blocks(0,50).get("items")).asList().hasSize(22);
    assertThat(parties.parties("","","",0,50).get("items")).asList().isNotEmpty();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_workbook_party_blocks WHERE sheet_name='शीट4'",Long.class)).isZero();
    @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)parties.entries("rinku bhaiya","","","","",0,50).get("items");
    assertThat(rows).isNotEmpty();
    assertThat(parties.entry((UUID)rows.getFirst().get("id")).get("raw")).isNotNull();
    assertThat(parties.parties("","","",0,1).get("items")).asList().hasSize(1);
    assertThat(parties.parties("","","",1,1).get("items")).isNotEqualTo(parties.parties("","","",0,1).get("items"));
    assertThat(parties.parties("","","Sumit",0,50).get("total")).isEqualTo(1L);
    var rinku=parties.party("rinku bhaiya");
    @SuppressWarnings("unchecked") var blocks=(List<Map<String,Object>>)rinku.get("blocks");
    assertThat(blocks).anySatisfy(block -> assertThat(block.get("disposition")).isEqualTo("DUPLICATE_SNAPSHOT"));
    assertThat(blocks).anySatisfy(block -> {assertThat(block.get("workbookBalance")).isEqualTo(new java.math.BigDecimal("0.00"));assertThat(block.get("parityStatus")).isEqualTo("MATCH");});
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_workbook_party_blocks WHERE workbook_balance<0",Long.class)).isPositive();
    var linked=jdbc.queryForList("SELECT l.entry_id,l.fact_id,l.relation_type,f.row_id FROM finance_workbook_party_links l "
        +"JOIN finance_migration_facts f ON f.id=l.fact_id WHERE l.relation_type LIKE 'PRODUCTION_%' LIMIT 1");
    assertThat(linked).isNotEmpty();
    assertThat(parties.entry((UUID)linked.getFirst().get("entry_id")).get("links")).asList().isNotEmpty();
    assertThat(productions.detail((UUID)linked.getFirst().get("row_id")).get("partyEvidence")).asList().isNotEmpty();
    var owner=jdbc.queryForList("SELECT l.entry_id,l.fact_id FROM finance_workbook_party_links l WHERE l.relation_type='OWNER_RECEIPT' LIMIT 1");
    assertThat(owner).isNotEmpty();
    assertThat(owners.movement((UUID)owner.getFirst().get("fact_id")).get("partyEvidence")).asList().isNotEmpty();
    assertThat(((List<?>)employees.summary().get("parity"))).allSatisfy(item -> assertThat(((Map<?,?>)item).get("status")).isEqualTo("MATCH"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_transactions",Long.class)).isEqualTo(postedBefore);
  }

  @Test @Transactional void threeSourceEvidenceDoesNotTripleCountOnePartyReceipt() throws Exception {
    migration.preview("historical.xlsx",Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(System.getenv("FINANCE_WORKBOOK_PATH")))));
    var before=parties.summary();
    var source=jdbc.queryForMap("SELECT l.batch_id,l.entry_id,e.payment FROM finance_workbook_party_links l "
        +"JOIN finance_workbook_party_entries e ON e.id=l.entry_id WHERE l.relation_type='OWNER_RECEIPT' LIMIT 1");
    UUID productionFact=jdbc.queryForObject("SELECT id FROM finance_migration_facts WHERE event_type='CLIENT_RECEIPT' LIMIT 1",UUID.class);
    jdbc.update("INSERT INTO finance_workbook_party_links(batch_id,entry_id,fact_id,relation_type,allocated_amount,confidence,evidence) "
        +"VALUES (?,?,?,'PRODUCTION_RECEIPT',?,'EXACT','{\"testOnly\":true}'::jsonb)",source.get("batch_id"),source.get("entry_id"),productionFact,source.get("payment"));
    var after=parties.summary();
    assertThat(after.get("received")).isEqualTo(before.get("received"));
    assertThat(((Number)after.get("threeWayReceipts")).longValue()).isEqualTo(((Number)before.get("threeWayReceipts")).longValue()+1);
    assertThat(parties.entry((UUID)source.get("entry_id")).get("receiptEvidenceCount")).isEqualTo(3L);
  }
}
