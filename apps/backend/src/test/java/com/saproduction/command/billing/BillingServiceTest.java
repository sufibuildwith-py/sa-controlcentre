package com.saproduction.command.billing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.lowagie.text.pdf.PdfReader;
import com.saproduction.command.finance.FinanceCommands;
import com.saproduction.command.finance.FinancePostingService;
import com.saproduction.command.shared.ApiException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

  @Mock JdbcTemplate jdbc;
  @Mock FinancePostingService finance;

  BillingService billing;

  @BeforeEach
  void setUp() {
    billing = new BillingService(jdbc, finance);
  }

  @Test
  void exportXlsxGeneratesValidWorkbook() throws Exception {
    UUID billId = UUID.randomUUID();
    Map<String, Object> bill = new HashMap<>();
    bill.put("id", billId);
    bill.put("bill_number", "SA-2026-TEST");
    bill.put("bill_date", java.sql.Date.valueOf(LocalDate.of(2026, 9, 25)));
    bill.put("financial_year", "2026-27");
    bill.put("customer", "Acme Events");
    bill.put("status", "DRAFT");
    bill.put("event_name", "Corporate Gala");
    bill.put("venue", "Grand Ballroom");
    bill.put("gstin", "27AABCU9603R1ZM");
    bill.put("subtotal", new BigDecimal("50000.00"));
    bill.put("discount", new BigDecimal("5000.00"));
    bill.put("freight", new BigDecimal("2000.00"));
    bill.put("tax_amount", new BigDecimal("8460.00"));
    bill.put("gross_total", new BigDecimal("55460.00"));
    bill.put("advance_paid", new BigDecimal("10000.00"));
    bill.put("notes", "Please deliver equipment 2 hours prior.");
    bill.put("payment_terms", "Net 15 days");
    bill.put("canonical_invoice_id", null);

    Map<String, Object> line1 = new HashMap<>();
    line1.put("id", UUID.randomUUID());
    line1.put("lineNo", 1);
    line1.put("quantity", new BigDecimal("2.000"));
    line1.put("days", new BigDecimal("3.000"));
    line1.put("description", "4K Video Wall");
    line1.put("rate", new BigDecimal("7500.00"));
    line1.put("amount", new BigDecimal("45000.00"));
    line1.put("reference", "LED-1");

    Map<String, Object> line2 = new HashMap<>();
    line2.put("id", UUID.randomUUID());
    line2.put("lineNo", 2);
    line2.put("quantity", new BigDecimal("1.000"));
    line2.put("days", new BigDecimal("1.000"));
    line2.put("description", "Truss & Rigging");
    line2.put("rate", new BigDecimal("5000.00"));
    line2.put("amount", new BigDecimal("5000.00"));
    line2.put("reference", "");

    when(jdbc.queryForList(startsWith("SELECT b.*,c.display_name"), eq(billId)))
        .thenReturn(List.of(bill));
    when(jdbc.queryForList(startsWith("SELECT id,line_no AS \"lineNo\""), eq(billId)))
        .thenReturn(List.of(line1, line2));

    Map<String, Object> result = billing.export(billId);

    assertThat(result).containsKey("base64");
    assertThat(result).containsKey("filename");
    assertThat(result.get("filename")).isEqualTo("SA-2026-TEST.xlsx");
    assertThat(result.get("contentType"))
        .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    byte[] bytes = Base64.getDecoder().decode((String) result.get("base64"));
    try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      assertThat(wb.getNumberOfSheets()).isEqualTo(1);
      assertThat(wb.getSheetName(0)).isEqualTo("INVOICE");
      var sheet = wb.getSheetAt(0);
      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("SA PRODUCTIONS");
      assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("INVOICE");
    }

    // Verify export NEVER touches finance
    verifyNoInteractions(finance);
  }

  @Test
  void exportPdfGeneratesValidPdfDocument() throws Exception {
    UUID billId = UUID.randomUUID();
    Map<String, Object> bill = new HashMap<>();
    bill.put("id", billId);
    bill.put("bill_number", "SA-2026-PDF");
    bill.put("bill_date", java.sql.Date.valueOf(LocalDate.of(2026, 9, 25)));
    bill.put("financial_year", "2026-27");
    bill.put("customer", "Global Media Inc");
    bill.put("status", "DRAFT");
    bill.put("event_name", "Tech Summit");
    bill.put("venue", "Convention Center");
    bill.put("gstin", "27AABCU9603R1ZM");
    bill.put("tax_mode", "CGST_SGST");
    bill.put("subtotal", new BigDecimal("25000.00"));
    bill.put("discount", BigDecimal.ZERO);
    bill.put("freight", BigDecimal.ZERO);
    bill.put("tax_amount", new BigDecimal("4500.00"));
    bill.put("gross_total", new BigDecimal("29500.00"));
    bill.put("advance_paid", new BigDecimal("5000.00"));
    bill.put("notes", "Setup by 8 AM.");
    bill.put("payment_terms", "Immediate on completion");
    bill.put("canonical_invoice_id", null);

    Map<String, Object> line1 = new HashMap<>();
    line1.put("id", UUID.randomUUID());
    line1.put("lineNo", 1);
    line1.put("quantity", new BigDecimal("1.000"));
    line1.put("days", new BigDecimal("1.000"));
    line1.put("description", "PA System & Wireless Mics");
    line1.put("rate", new BigDecimal("25000.00"));
    line1.put("amount", new BigDecimal("25000.00"));
    line1.put("reference", "AUDIO-1");

    when(jdbc.queryForList(startsWith("SELECT b.*,c.display_name"), eq(billId)))
        .thenReturn(List.of(bill));
    when(jdbc.queryForList(startsWith("SELECT id,line_no AS \"lineNo\""), eq(billId)))
        .thenReturn(List.of(line1));

    Map<String, Object> result = billing.exportPdf(billId);

    assertThat(result).containsKey("base64");
    assertThat(result).containsKey("filename");
    assertThat(result.get("filename")).isEqualTo("SA-2026-PDF.pdf");
    assertThat(result.get("contentType")).isEqualTo("application/pdf");

    byte[] bytes = Base64.getDecoder().decode((String) result.get("base64"));
    assertThat(bytes.length).isGreaterThan(100);

    // Verify PDF header magic bytes "%PDF-"
    String header = new String(bytes, 0, 5);
    assertThat(header).isEqualTo("%PDF-");

    // Verify it is a valid parseable PDF document
    PdfReader reader = new PdfReader(bytes);
    assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
    reader.close();

    // Verify export never touched finance
    verifyNoInteractions(finance);
  }

  @Test
  void cannotCancelNonDraftBill() {
    UUID billId = UUID.randomUUID();
    Map<String, Object> bill = Map.of("id", billId, "status", "ISSUED");
    when(jdbc.queryForList(
            startsWith("SELECT * FROM billing_bills WHERE id=? FOR UPDATE"), eq(billId)))
        .thenReturn(List.of(bill));

    assertThatThrownBy(() -> billing.cancel(billId))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("BILL_NOT_DRAFT");
  }

  @Test
  void cannotIssueCancelledBill() {
    UUID billId = UUID.randomUUID();
    Map<String, Object> bill = Map.of("id", billId, "status", "CANCELLED");
    when(jdbc.queryForList(
            startsWith("SELECT * FROM billing_bills WHERE id=? FOR UPDATE"), eq(billId)))
        .thenReturn(List.of(bill));

    assertThatThrownBy(() -> billing.issue(billId, new BillingCommands.Issue(UUID.randomUUID())))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("BILL_CANCELLED");
  }

  @Test
  void issuingDraftPostsToCanonicalFinance() {
    UUID billId = UUID.randomUUID();
    UUID counterpartyId = UUID.randomUUID();
    UUID productionId = UUID.randomUUID();
    UUID txnId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();

    Map<String, Object> bill = new HashMap<>();
    bill.put("id", billId);
    bill.put("status", "DRAFT");
    bill.put("bill_number", "SA-2026-003");
    bill.put("financial_year", "2026-27");
    bill.put("bill_date", java.sql.Date.valueOf(LocalDate.of(2026, 9, 25)));
    bill.put("counterparty_id", counterpartyId);
    bill.put("production_id", productionId);
    bill.put("gstin", "27AABCU9603R1ZM");
    bill.put("tax_mode", "CGST_SGST");
    bill.put("subtotal", new BigDecimal("10000.00"));
    bill.put("discount", BigDecimal.ZERO);
    bill.put("freight", BigDecimal.ZERO);
    bill.put("cgst_rate", new BigDecimal("9.000"));
    bill.put("sgst_rate", new BigDecimal("9.000"));
    bill.put("igst_rate", BigDecimal.ZERO);
    bill.put("canonical_invoice_id", null);

    when(jdbc.queryForList(
            startsWith("SELECT * FROM billing_bills WHERE id=? FOR UPDATE"), eq(billId)))
        .thenReturn(List.of(bill));
    when(finance.invoice(any(FinanceCommands.Invoice.class))).thenReturn(Map.of("id", txnId));
    when(jdbc.queryForObject(
            startsWith("SELECT id FROM finance_invoices"), eq(UUID.class), eq(txnId)))
        .thenReturn(invoiceId);

    // Mock the subsequent get() calls
    Map<String, Object> issuedBill = new HashMap<>(bill);
    issuedBill.put("status", "ISSUED");
    issuedBill.put("canonical_invoice_id", invoiceId);
    issuedBill.put("customer", "Client X");
    issuedBill.put("gross_total", new BigDecimal("11800.00"));
    when(jdbc.queryForList(startsWith("SELECT b.*,c.display_name"), eq(billId)))
        .thenReturn(List.of(issuedBill));
    when(jdbc.queryForList(startsWith("SELECT id,line_no AS \"lineNo\""), eq(billId)))
        .thenReturn(List.of());
    when(jdbc.queryForObject(
            startsWith("SELECT coalesce(sum(a.amount),0)"), eq(BigDecimal.class), eq(invoiceId)))
        .thenReturn(BigDecimal.ZERO);

    UUID idempotencyKey = UUID.randomUUID();
    Map<String, Object> result = billing.issue(billId, new BillingCommands.Issue(idempotencyKey));

    verify(finance, times(1))
        .invoice(
            argThat(
                cmd ->
                    cmd.idempotencyKey().equals(idempotencyKey)
                        && cmd.invoiceNumber().equals("SA-2026-003")
                        && cmd.baseAmount().compareTo(new BigDecimal("10000.00")) == 0
                        && cmd.baseAmount().scale() <= 2
                        && cmd.cgstRate().compareTo(new BigDecimal("9.00")) == 0
                        && cmd.cgstRate().scale() <= 2
                        && cmd.sgstRate().scale() <= 2
                        && cmd.igstRate().scale() <= 2
                        && cmd.counterpartyId().equals(counterpartyId)));

    verify(jdbc)
        .update(startsWith("UPDATE billing_bills SET status='ISSUED'"), eq(invoiceId), eq(billId));
  }

  @Test
  void exportWritesValidFilesToDiskForInspection() throws Exception {
    UUID billId = UUID.randomUUID();
    Map<String, Object> bill = new HashMap<>();
    bill.put("id", billId);
    bill.put("bill_number", "SA-2026-INSPECT");
    bill.put("bill_date", java.sql.Date.valueOf(LocalDate.of(2026, 9, 25)));
    bill.put("financial_year", "2026-27");
    bill.put("customer", "Starlight Productions");
    bill.put("status", "DRAFT");
    bill.put("event_name", "Diwali Music Fest");
    bill.put("venue", "Jio World Garden");
    bill.put("gstin", "27AABCU9603R1ZM");
    bill.put("tax_mode", "CGST_SGST");
    bill.put("subtotal", new BigDecimal("100000.00"));
    bill.put("discount", new BigDecimal("5000.00"));
    bill.put("freight", new BigDecimal("2500.00"));
    bill.put("cgst_rate", new BigDecimal("9.000"));
    bill.put("sgst_rate", new BigDecimal("9.000"));
    bill.put("igst_rate", BigDecimal.ZERO);
    bill.put("tax_amount", new BigDecimal("17550.00"));
    bill.put("gross_total", new BigDecimal("115050.00"));
    bill.put("advance_paid", new BigDecimal("25000.00"));
    bill.put("notes", "VIP stage sound and LED screens.");
    bill.put("payment_terms", "50% advance, balance on show day");
    bill.put("canonical_invoice_id", null);

    Map<String, Object> line1 = new HashMap<>();
    line1.put("id", UUID.randomUUID());
    line1.put("lineNo", 1);
    line1.put("quantity", new BigDecimal("1.000"));
    line1.put("days", new BigDecimal("2.000"));
    line1.put("description", "V-DOSC Line Array & Subs");
    line1.put("rate", new BigDecimal("50000.00"));
    line1.put("amount", new BigDecimal("100000.00"));
    line1.put("reference", "SOUND-01");

    when(jdbc.queryForList(startsWith("SELECT b.*,c.display_name"), eq(billId)))
        .thenReturn(List.of(bill));
    when(jdbc.queryForList(startsWith("SELECT id,line_no AS \"lineNo\""), eq(billId)))
        .thenReturn(List.of(line1));

    // Export XLSX
    Map<String, Object> xlsxResult = billing.export(billId);
    byte[] xlsxBytes = Base64.getDecoder().decode((String) xlsxResult.get("base64"));
    java.nio.file.Path targetDir = java.nio.file.Paths.get("target", "generated-exports");
    java.nio.file.Files.createDirectories(targetDir);
    java.nio.file.Path xlsxPath = targetDir.resolve("SA-2026-INSPECT.xlsx");
    java.nio.file.Files.write(xlsxPath, xlsxBytes);

    assertThat(java.nio.file.Files.exists(xlsxPath)).isTrue();
    assertThat(xlsxBytes.length).isGreaterThan(1000);

    // Export PDF
    Map<String, Object> pdfResult = billing.exportPdf(billId);
    byte[] pdfBytes = Base64.getDecoder().decode((String) pdfResult.get("base64"));
    java.nio.file.Path pdfPath = targetDir.resolve("SA-2026-INSPECT.pdf");
    java.nio.file.Files.write(pdfPath, pdfBytes);

    assertThat(java.nio.file.Files.exists(pdfPath)).isTrue();
    assertThat(pdfBytes.length).isGreaterThan(1000);
    assertThat(new String(pdfBytes, 0, 5)).isEqualTo("%PDF-");

    // Also copy to brain artifact scratch for user inspection if desired
    try {
      java.nio.file.Path scratchDir =
          java.nio.file.Paths.get(
              "C:\\Users\\xtrar\\.gemini\\antigravity\\brain\\1be62e58-c99c-4d1a-abaa-651f14f94244");
      java.nio.file.Files.write(scratchDir.resolve("SA-2026-INSPECT.xlsx"), xlsxBytes);
      java.nio.file.Files.write(scratchDir.resolve("SA-2026-INSPECT.pdf"), pdfBytes);
    } catch (Exception ignored) {
    }

    // Verify finance was never touched during export
    verifyNoInteractions(finance);
  }
}
