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

@EnabledIfEnvironmentVariable(named="FINANCE_WORKBOOK_PATH",matches=".+")
@EnabledIfEnvironmentVariable(named="FINANCE_TEST_DB_URL",matches=".+")
@SpringBootTest
@TestPropertySource(properties={"app.mode=demo","app.demo-seed=false","app.messaging.worker-enabled=false","app.navigator.enabled=false"})
class FinanceWorkbookCommercialIntegrationTest {
  @Autowired FinanceMigrationService migration;
  @Autowired FinanceWorkbookCommercialService commercial;
  @Autowired JdbcTemplate jdbc;

  @Test void realGstLedSoundAndAccessoriesRetainWorkbookControls() throws Exception {
    migration.preview("historical.xlsx", Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(System.getenv("FINANCE_WORKBOOK_PATH")))));
    long postedBefore=jdbc.queryForObject("SELECT count(*) FROM finance_transactions",Long.class);
    var gst=commercial.gstSummary();
    assertThat(gst.get("available")).isEqualTo(true);
    assertThat(((Number)gst.get("invoiceCount")).longValue()).isPositive();
    assertThat(((Number)gst.get("parityMismatches")).longValue()).isZero();
    @SuppressWarnings("unchecked") var invoices=(List<Map<String,Object>>)commercial.invoices("LTP CALCULATOR","",0,50).get("items");
    assertThat(invoices).hasSize(1);
    var ltp=invoices.getFirst();
    assertThat(ltp.get("base")).isEqualTo(new BigDecimal("388800.00"));
    assertThat(ltp.get("cgst")).isEqualTo(new BigDecimal("34992.00"));
    assertThat(ltp.get("tds")).isEqualTo(new BigDecimal("7776.00"));
    assertThat(ltp.get("cash")).isEqualTo(new BigDecimal("351008.00"));
    assertThat(ltp.get("outstanding")).isEqualTo(new BigDecimal("100000.00"));
    assertThat(commercial.invoice((UUID)ltp.get("id")).get("settlements")).asList().isNotEmpty();

    var purchases=commercial.purchaseSummary();
    @SuppressWarnings("unchecked") var books=(List<Map<String,Object>>)purchases.get("books");
    assertThat(books).anySatisfy(book -> { assertThat(book.get("book")).isEqualTo("LED"); assertThat(book.get("purchased")).isEqualTo(new BigDecimal("13076798.00")); assertThat(book.get("paid")).isEqualTo(new BigDecimal("11306448.00")); assertThat(book.get("outstanding")).isEqualTo(new BigDecimal("1770350.00")); });
    assertThat(books).anySatisfy(book -> { assertThat(book.get("book")).isEqualTo("SOUND"); assertThat(book.get("purchased")).isEqualTo(new BigDecimal("3174223.00")); assertThat(book.get("paid")).isEqualTo(new BigDecimal("3174223.00")); assertThat(book.get("outstanding")).isEqualTo(BigDecimal.ZERO.setScale(2)); });
    @SuppressWarnings("unchecked") var sound=(List<Map<String,Object>>)commercial.purchases("SOUND","",0,100).get("items");
    assertThat(sound).isNotEmpty();
    assertThat(commercial.purchase((UUID)sound.getFirst().get("id")).get("raw")).isNotNull();
    @SuppressWarnings("unchecked") var accessories=(Map<String,Object>)purchases.get("accessories");
    assertThat(((Number)accessories.get("accessory_quantity")).intValue()).isEqualTo(59);
    assertThat(((Number)accessories.get("duplicate_snapshots")).intValue()).isEqualTo(14);
    assertThat(commercial.accessories("ACCESSORY","Data Cable",0,50).get("items")).asList().hasSize(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_transactions",Long.class)).isEqualTo(postedBefore);
  }
}
