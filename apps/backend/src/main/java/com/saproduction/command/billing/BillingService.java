package com.saproduction.command.billing;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.saproduction.command.finance.FinanceCommands;
import com.saproduction.command.finance.FinancePostingService;
import com.saproduction.command.shared.ApiException;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private final JdbcTemplate jdbc;
  private final FinancePostingService finance;

  public BillingService(JdbcTemplate jdbc, FinancePostingService finance) {
    this.jdbc = jdbc;
    this.finance = finance;
  }

  @Transactional
  public Map<String, Object> create(BillingCommands.Create input) {
    validateTaxMode(input.taxMode(), input.cgstRate(), input.sgstRate(), input.igstRate());
    requireParty(input.counterpartyId());
    if (input.productionId() != null) requireProduction(input.productionId());

    if (jdbc.queryForObject(
            "SELECT count(*) FROM billing_bills WHERE bill_number=?",
            Long.class,
            input.billNumber().trim())
        > 0) {
      throw ApiException.conflict(
          "BILL_NUMBER_EXISTS", "A bill with this bill number already exists.");
    }

    var activeLines =
        input.lines().stream()
            .filter(l -> l.description() != null && !l.description().isBlank())
            .toList();
    if (activeLines.isEmpty()) {
      throw ApiException.badRequest(
          "BILL_LINES_EMPTY", "A bill must contain at least one line item.");
    }

    UUID id = UUID.randomUUID();
    BigDecimal subtotal = ZERO();
    for (BillingCommands.Line line : activeLines) {
      BigDecimal rate = money(line.rate());
      BigDecimal amount =
          line.quantity().multiply(line.days()).multiply(rate).setScale(2, RoundingMode.HALF_UP);
      subtotal = subtotal.add(amount);
    }
    subtotal = money(subtotal);
    BigDecimal discount = nonnegative(input.discount());
    if (discount.compareTo(subtotal) > 0)
      throw ApiException.badRequest(
          "BILL_DISCOUNT_EXCEEDS_SUBTOTAL", "Discount cannot exceed the line subtotal.");
    BigDecimal taxable = subtotal.subtract(discount);
    BigDecimal freight = nonnegative(input.freight());
    BigDecimal taxBase = taxable.add(freight);
    BigDecimal cgstRate = money(input.cgstRate());
    BigDecimal sgstRate = money(input.sgstRate());
    BigDecimal igstRate = money(input.igstRate());
    BigDecimal cgst = tax(taxBase, cgstRate);
    BigDecimal sgst = tax(taxBase, sgstRate);
    BigDecimal igst = tax(taxBase, igstRate);
    BigDecimal tax = cgst.add(sgst).add(igst);
    BigDecimal total = taxBase.add(tax).setScale(2, RoundingMode.HALF_UP);
    if (total.signum() <= 0)
      throw ApiException.badRequest("BILL_TOTAL_INVALID", "A bill must contain a positive total.");
    BigDecimal advancePaid = nonnegative(input.advancePaid());
    if (advancePaid.compareTo(total) > 0)
      throw ApiException.badRequest(
          "BILL_ADVANCE_EXCEEDS_TOTAL", "Advance / already received cannot exceed the bill total.");

    jdbc.update(
        """
      INSERT INTO billing_bills
      (id,bill_number,bill_date,financial_year,counterparty_id,production_id,event_name,venue,status,tax_mode,gstin,
       cgst_rate,sgst_rate,igst_rate,discount,freight,advance_paid,subtotal,tax_amount,gross_total,notes,payment_terms)
      VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
      """,
        id,
        input.billNumber().trim(),
        input.billDate(),
        input.financialYear().trim(),
        input.counterpartyId(),
        input.productionId(),
        blank(input.eventName()),
        blank(input.venue()),
        "DRAFT",
        input.taxMode(),
        blank(input.gstin()),
        cgstRate,
        sgstRate,
        igstRate,
        discount,
        freight,
        advancePaid,
        subtotal,
        tax,
        total,
        blank(input.notes()),
        blank(input.paymentTerms()));

    int no = 1;
    for (BillingCommands.Line line : activeLines) {
      BigDecimal rate = money(line.rate());
      BigDecimal amount =
          line.quantity().multiply(line.days()).multiply(rate).setScale(2, RoundingMode.HALF_UP);
      jdbc.update(
          """
        INSERT INTO billing_bill_lines(id,bill_id,line_no,quantity,days,description,rate,amount,reference)
        VALUES(?,?,?,?,?,?,?,?,?)
        """,
          UUID.randomUUID(),
          id,
          no++,
          line.quantity(),
          line.days(),
          line.description().trim(),
          rate,
          amount,
          blank(line.reference()));
    }
    return get(id);
  }

  @Transactional
  public Map<String, Object> update(UUID id, BillingCommands.Create input) {
    validateTaxMode(input.taxMode(), input.cgstRate(), input.sgstRate(), input.igstRate());
    requireParty(input.counterpartyId());
    if (input.productionId() != null) requireProduction(input.productionId());

    Map<String, Object> bill = one("SELECT * FROM billing_bills WHERE id=? FOR UPDATE", id);
    if (!"DRAFT".equals(bill.get("status"))) {
      throw ApiException.conflict("BILL_NOT_DRAFT", "Only draft bills can be updated.");
    }

    if (jdbc.queryForObject(
            "SELECT count(*) FROM billing_bills WHERE bill_number=? AND id!=?",
            Long.class,
            input.billNumber().trim(),
            id)
        > 0) {
      throw ApiException.conflict(
          "BILL_NUMBER_EXISTS", "A bill with this bill number already exists.");
    }

    var activeLines =
        input.lines().stream()
            .filter(l -> l.description() != null && !l.description().isBlank())
            .toList();
    if (activeLines.isEmpty()) {
      throw ApiException.badRequest(
          "BILL_LINES_EMPTY", "A bill must contain at least one line item.");
    }

    BigDecimal subtotal = ZERO();
    for (BillingCommands.Line line : activeLines) {
      BigDecimal rate = money(line.rate());
      BigDecimal amount =
          line.quantity().multiply(line.days()).multiply(rate).setScale(2, RoundingMode.HALF_UP);
      subtotal = subtotal.add(amount);
    }
    subtotal = money(subtotal);
    BigDecimal discount = nonnegative(input.discount());
    if (discount.compareTo(subtotal) > 0)
      throw ApiException.badRequest(
          "BILL_DISCOUNT_EXCEEDS_SUBTOTAL", "Discount cannot exceed the line subtotal.");
    BigDecimal taxable = subtotal.subtract(discount);
    BigDecimal freight = nonnegative(input.freight());
    BigDecimal taxBase = taxable.add(freight);
    BigDecimal cgstRate = money(input.cgstRate());
    BigDecimal sgstRate = money(input.sgstRate());
    BigDecimal igstRate = money(input.igstRate());
    BigDecimal cgst = tax(taxBase, cgstRate);
    BigDecimal sgst = tax(taxBase, sgstRate);
    BigDecimal igst = tax(taxBase, igstRate);
    BigDecimal tax = cgst.add(sgst).add(igst);
    BigDecimal total = taxBase.add(tax).setScale(2, RoundingMode.HALF_UP);
    if (total.signum() <= 0)
      throw ApiException.badRequest("BILL_TOTAL_INVALID", "A bill must contain a positive total.");
    BigDecimal advancePaid = nonnegative(input.advancePaid());
    if (advancePaid.compareTo(total) > 0)
      throw ApiException.badRequest(
          "BILL_ADVANCE_EXCEEDS_TOTAL", "Advance / already received cannot exceed the bill total.");

    jdbc.update(
        """
      UPDATE billing_bills SET
        bill_number=?, bill_date=?, financial_year=?, counterparty_id=?, production_id=?,
        event_name=?, venue=?, tax_mode=?, gstin=?, cgst_rate=?, sgst_rate=?, igst_rate=?,
        discount=?, freight=?, advance_paid=?, subtotal=?, tax_amount=?, gross_total=?,
        notes=?, payment_terms=?, updated_at=now()
      WHERE id=?
      """,
        input.billNumber().trim(),
        input.billDate(),
        input.financialYear().trim(),
        input.counterpartyId(),
        input.productionId(),
        blank(input.eventName()),
        blank(input.venue()),
        input.taxMode(),
        blank(input.gstin()),
        cgstRate,
        sgstRate,
        igstRate,
        discount,
        freight,
        advancePaid,
        subtotal,
        tax,
        total,
        blank(input.notes()),
        blank(input.paymentTerms()),
        id);

    jdbc.update("DELETE FROM billing_bill_lines WHERE bill_id=?", id);
    int no = 1;
    for (BillingCommands.Line line : activeLines) {
      BigDecimal rate = money(line.rate());
      BigDecimal amount =
          line.quantity().multiply(line.days()).multiply(rate).setScale(2, RoundingMode.HALF_UP);
      jdbc.update(
          """
        INSERT INTO billing_bill_lines(id,bill_id,line_no,quantity,days,description,rate,amount,reference)
        VALUES(?,?,?,?,?,?,?,?,?)
        """,
          UUID.randomUUID(),
          id,
          no++,
          line.quantity(),
          line.days(),
          line.description().trim(),
          rate,
          amount,
          blank(line.reference()));
    }
    return get(id);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list() {
    return jdbc.queryForList(
        """
      SELECT b.id,b.bill_number AS "billNumber",b.bill_date AS "billDate",b.financial_year AS "financialYear",
             c.display_name AS "customer",b.status,b.gross_total AS "grossTotal",b.advance_paid AS "advancePaid",
             b.gross_total-b.advance_paid-coalesce((SELECT sum(a.amount) FROM finance_invoice_payment_allocations a
               JOIN finance_transactions pt ON pt.id=a.transaction_id
               WHERE a.invoice_id=b.canonical_invoice_id AND pt.status='POSTED'),0) AS "balanceDue"
      FROM billing_bills b JOIN finance_counterparties c ON c.id=b.counterparty_id
      ORDER BY b.bill_date DESC,b.created_at DESC
      """);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(UUID id) {
    var bill =
        one(
            "SELECT b.*,c.display_name AS customer FROM billing_bills b JOIN finance_counterparties c ON c.id=b.counterparty_id WHERE b.id=?",
            id);
    refreshPaymentStatus(id, bill);
    bill =
        one(
            "SELECT b.*,c.display_name AS customer FROM billing_bills b JOIN finance_counterparties c ON c.id=b.counterparty_id WHERE b.id=?",
            id);
    var lines =
        jdbc.queryForList(
            "SELECT id,line_no AS \"lineNo\",quantity,days,description,rate,amount,reference FROM billing_bill_lines WHERE bill_id=? ORDER BY line_no",
            id);
    return Map.of("bill", bill, "lines", lines);
  }

  @Transactional
  public Map<String, Object> issue(UUID id, BillingCommands.Issue input) {
    Map<String, Object> bill = one("SELECT * FROM billing_bills WHERE id=? FOR UPDATE", id);
    String status = (String) bill.get("status");
    if ("CANCELLED".equals(status))
      throw ApiException.conflict("BILL_CANCELLED", "Cancelled bills cannot be issued.");
    if (!"DRAFT".equals(status)) return get(id);
    UUID existing = (UUID) bill.get("canonical_invoice_id");
    if (existing != null) return get(id);

    BigDecimal subtotal = (BigDecimal) bill.get("subtotal");
    BigDecimal discount = (BigDecimal) bill.get("discount");
    BigDecimal freight = (BigDecimal) bill.get("freight");
    BigDecimal base = money(subtotal.subtract(discount).add(freight));
    String taxMode = (String) bill.get("tax_mode");
    BigDecimal cgstRate = money((BigDecimal) bill.get("cgst_rate"));
    BigDecimal sgstRate = money((BigDecimal) bill.get("sgst_rate"));
    BigDecimal igstRate = money((BigDecimal) bill.get("igst_rate"));

    FinanceCommands.Invoice command =
        new FinanceCommands.Invoice(
            input.idempotencyKey(),
            (String) bill.get("bill_number"),
            (String) bill.get("financial_year"),
            ((java.sql.Date) bill.get("bill_date")).toLocalDate(),
            (UUID) bill.get("counterparty_id"),
            (UUID) bill.get("production_id"),
            (String) bill.get("gstin"),
            taxMode,
            base,
            cgstRate,
            sgstRate,
            igstRate,
            BigDecimal.ZERO.setScale(2));

    Map<String, Object> posted = finance.invoice(command);
    UUID transactionId = (UUID) posted.get("id");
    UUID invoiceId =
        jdbc.queryForObject(
            "SELECT id FROM finance_invoices WHERE source_transaction_id=?",
            UUID.class,
            transactionId);
    jdbc.update(
        "UPDATE billing_bills SET status='ISSUED',canonical_invoice_id=?,issued_at=now(),updated_at=now() WHERE id=?",
        invoiceId,
        id);
    return get(id);
  }

  @Transactional
  public Map<String, Object> cancel(UUID id) {
    Map<String, Object> bill = one("SELECT * FROM billing_bills WHERE id=? FOR UPDATE", id);
    if (!"DRAFT".equals(bill.get("status")))
      throw ApiException.conflict("BILL_NOT_DRAFT", "Only draft bills can be cancelled.");
    jdbc.update(
        "UPDATE billing_bills SET status='CANCELLED',cancelled_at=now(),updated_at=now() WHERE id=?",
        id);
    return get(id);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> export(UUID id) {
    Map<String, Object> data = get(id);
    @SuppressWarnings("unchecked")
    Map<String, Object> bill = (Map<String, Object>) data.get("bill");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lines = (List<Map<String, Object>>) data.get("lines");
    try (XSSFWorkbook wb = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = wb.createSheet("INVOICE");
      sheet.setPrintGridlines(false);
      sheet.getPrintSetup().setLandscape(false);
      sheet.getPrintSetup().setFitWidth((short) 1);
      sheet.getPrintSetup().setFitHeight((short) 0);
      sheet.setAutobreaks(true);

      org.apache.poi.ss.usermodel.Font title = wb.createFont();
      title.setBold(true);
      title.setFontHeightInPoints((short) 20);
      org.apache.poi.ss.usermodel.Font header = wb.createFont();
      header.setBold(true);
      header.setColor(IndexedColors.WHITE.getIndex());
      org.apache.poi.ss.usermodel.Font small = wb.createFont();
      small.setFontHeightInPoints((short) 9);
      CellStyle titleStyle = wb.createCellStyle();
      titleStyle.setFont(title);
      CellStyle headerStyle = wb.createCellStyle();
      headerStyle.setFont(header);
      headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
      headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      CellStyle money = wb.createCellStyle();
      money.setDataFormat(wb.createDataFormat().getFormat("₹#,##0.00"));
      CellStyle date = wb.createCellStyle();
      date.setDataFormat(wb.createDataFormat().getFormat("dd-mmm-yyyy"));
      CellStyle wrap = wb.createCellStyle();
      wrap.setWrapText(true);
      wrap.setVerticalAlignment(VerticalAlignment.TOP);

      sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 5));
      Row r0 = sheet.getRow(0) == null ? sheet.createRow(0) : sheet.getRow(0);
      r0.createCell(0).setCellValue("SA PRODUCTIONS");
      r0.getCell(0).setCellStyle(titleStyle);
      sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 5));
      sheet.createRow(1).createCell(0).setCellValue("INVOICE");
      sheet.getRow(1).getCell(0).setCellStyle(titleStyle);

      row(sheet, 2, 0, "Bill No.", bill.get("bill_number"));
      row(sheet, 2, 2, "Date", bill.get("bill_date"));
      row(sheet, 3, 0, "Customer", bill.get("customer"));
      row(sheet, 3, 2, "Financial Year", bill.get("financial_year"));
      row(sheet, 4, 0, "Event", bill.get("event_name"));
      row(sheet, 4, 2, "Venue", bill.get("venue"));
      row(sheet, 5, 0, "GSTIN", bill.get("gstin"));
      row(sheet, 5, 2, "Status", bill.get("status"));

      Row h = sheet.createRow(7);
      String[] heads = {"SR.", "QTY / DAYS", "PRODUCT-DESCRIPTION", "RATE", "AMOUNT", "REF"};
      for (int i = 0; i < heads.length; i++) {
        Cell cell = h.createCell(i);
        cell.setCellValue(heads[i]);
        cell.setCellStyle(headerStyle);
      }
      int r = 8;
      for (Map<String, Object> line : lines) {
        Row rr = sheet.createRow(r++);
        rr.createCell(0).setCellValue(((Number) line.get("lineNo")).doubleValue());
        rr.createCell(1)
            .setCellValue(
                ((Number) line.get("quantity")).doubleValue()
                    + " × "
                    + ((Number) line.get("days")).doubleValue());
        rr.createCell(2).setCellValue(String.valueOf(line.get("description")));
        rr.getCell(2).setCellStyle(wrap);
        rr.createCell(3).setCellValue(((Number) line.get("rate")).doubleValue());
        rr.getCell(3).setCellStyle(money);
        rr.createCell(4).setCellValue(((Number) line.get("amount")).doubleValue());
        rr.getCell(4).setCellStyle(money);
        rr.createCell(5)
            .setCellValue(
                String.valueOf(line.get("reference") == null ? "" : line.get("reference")));
      }
      r++;
      row(sheet, r, 2, "Subtotal", bill.get("subtotal"));
      if (sheet.getRow(r).getCell(3) != null) sheet.getRow(r).getCell(3).setCellStyle(money);
      if (nonZero(bill.get("discount"))) {
        r++;
        row(sheet, r, 2, "Discount", bill.get("discount"));
        if (sheet.getRow(r).getCell(3) != null) sheet.getRow(r).getCell(3).setCellStyle(money);
      }
      if (nonZero(bill.get("freight"))) {
        r++;
        row(sheet, r, 2, "Freight / Transport", bill.get("freight"));
        if (sheet.getRow(r).getCell(3) != null) sheet.getRow(r).getCell(3).setCellStyle(money);
      }
      if (nonZero(bill.get("tax_amount"))) {
        r++;
        row(sheet, r, 2, "Tax", bill.get("tax_amount"));
        if (sheet.getRow(r).getCell(3) != null) sheet.getRow(r).getCell(3).setCellStyle(money);
      }
      r++;
      row(sheet, r, 2, "GROSS TOTAL", bill.get("gross_total"));
      if (sheet.getRow(r).getCell(3) != null) sheet.getRow(r).getCell(3).setCellStyle(money);
      r++;
      row(sheet, r, 2, "Advance / Already Received", bill.get("advance_paid"));
      if (sheet.getRow(r).getCell(3) != null) sheet.getRow(r).getCell(3).setCellStyle(money);
      r++;
      BigDecimal gross = (BigDecimal) bill.get("gross_total");
      BigDecimal advance = (BigDecimal) bill.get("advance_paid");
      row(sheet, r, 2, "NET BALANCE DUE", gross.subtract(advance));
      if (sheet.getRow(r).getCell(3) != null) sheet.getRow(r).getCell(3).setCellStyle(money);

      r += 2;
      row(sheet, r, 0, "Notes", bill.get("notes"));
      if (sheet.getRow(r).getCell(1) != null) sheet.getRow(r).getCell(1).setCellStyle(wrap);
      r++;
      row(sheet, r, 0, "Payment Terms", bill.get("payment_terms"));
      if (sheet.getRow(r).getCell(1) != null) sheet.getRow(r).getCell(1).setCellStyle(wrap);

      for (int i = 0; i < 6; i++) sheet.autoSizeColumn(i);
      sheet.setColumnWidth(2, 9000);
      sheet.setColumnWidth(3, 4000);
      sheet.setColumnWidth(4, 4500);
      wb.write(out);
      String filename =
          String.valueOf(bill.get("bill_number")).replaceAll("[^A-Za-z0-9._-]", "_") + ".xlsx";
      String base64 = Base64.getEncoder().encodeToString(out.toByteArray());
      return Map.of(
          "base64",
          base64,
          "filename",
          filename,
          "contentType",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    } catch (Exception e) {
      throw new IllegalStateException("Could not generate the editable invoice workbook.", e);
    }
  }

  @Transactional(readOnly = true)
  public Map<String, Object> exportPdf(UUID id) {
    Map<String, Object> data = get(id);
    @SuppressWarnings("unchecked")
    Map<String, Object> bill = (Map<String, Object>) data.get("bill");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> lines = (List<Map<String, Object>>) data.get("lines");

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
      PdfWriter.getInstance(doc, out);
      doc.open();

      Color brandNavy = new Color(24, 32, 47);
      Color slateGray = new Color(71, 85, 105);
      Color lightBg = new Color(248, 250, 252);
      Color borderColor = new Color(226, 232, 240);

      Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brandNavy);
      Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, slateGray);
      Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
      Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, brandNavy);
      Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, brandNavy);
      Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, slateGray);

      PdfPTable headerTable = new PdfPTable(2);
      headerTable.setWidthPercentage(100);
      headerTable.setWidths(new float[] {58f, 42f});

      PdfPCell titleCell = new PdfPCell();
      titleCell.setBorder(Rectangle.NO_BORDER);
      titleCell.addElement(new Paragraph("SA PRODUCTIONS", titleFont));
      titleCell.addElement(new Paragraph("INVOICE / BILL OF SUPPLY", subtitleFont));
      headerTable.addCell(titleCell);

      PdfPCell metaRight = new PdfPCell();
      metaRight.setBorder(Rectangle.NO_BORDER);
      metaRight.setHorizontalAlignment(Element.ALIGN_RIGHT);
      Paragraph pBill = new Paragraph("Bill No: " + bill.get("bill_number"), boldFont);
      pBill.setAlignment(Element.ALIGN_RIGHT);
      metaRight.addElement(pBill);
      Paragraph pDate = new Paragraph("Date: " + bill.get("bill_date"), normalFont);
      pDate.setAlignment(Element.ALIGN_RIGHT);
      metaRight.addElement(pDate);
      Paragraph pStatus = new Paragraph("Status: " + bill.get("status"), boldFont);
      pStatus.setAlignment(Element.ALIGN_RIGHT);
      metaRight.addElement(pStatus);
      headerTable.addCell(metaRight);

      doc.add(headerTable);
      doc.add(new Paragraph(" ", smallFont));

      PdfPTable infoTable = new PdfPTable(4);
      infoTable.setWidthPercentage(100);
      infoTable.setWidths(new float[] {25f, 25f, 25f, 25f});

      addMetaCell(
          infoTable,
          "Customer",
          String.valueOf(bill.get("customer") == null ? "" : bill.get("customer")),
          smallFont,
          normalFont,
          lightBg,
          borderColor,
          2);
      addMetaCell(
          infoTable,
          "Financial Year",
          String.valueOf(bill.get("financial_year") == null ? "" : bill.get("financial_year")),
          smallFont,
          normalFont,
          lightBg,
          borderColor,
          2);
      addMetaCell(
          infoTable,
          "Event / Project",
          String.valueOf(bill.get("event_name") == null ? "—" : bill.get("event_name")),
          smallFont,
          normalFont,
          lightBg,
          borderColor,
          2);
      addMetaCell(
          infoTable,
          "Venue",
          String.valueOf(bill.get("venue") == null ? "—" : bill.get("venue")),
          smallFont,
          normalFont,
          lightBg,
          borderColor,
          2);

      String gstin =
          bill.get("gstin") == null || String.valueOf(bill.get("gstin")).isBlank()
              ? "—"
              : String.valueOf(bill.get("gstin"));
      addMetaCell(infoTable, "GSTIN", gstin, smallFont, normalFont, lightBg, borderColor, 2);
      String taxMode = String.valueOf(bill.get("tax_mode") == null ? "NONE" : bill.get("tax_mode"));
      addMetaCell(infoTable, "Tax Mode", taxMode, smallFont, normalFont, lightBg, borderColor, 2);

      String terms =
          bill.get("payment_terms") == null || String.valueOf(bill.get("payment_terms")).isBlank()
              ? "—"
              : String.valueOf(bill.get("payment_terms"));
      addMetaCell(
          infoTable, "Payment Terms", terms, smallFont, normalFont, lightBg, borderColor, 4);

      doc.add(infoTable);
      doc.add(new Paragraph(" ", smallFont));

      PdfPTable lineTable = new PdfPTable(6);
      lineTable.setWidthPercentage(100);
      lineTable.setWidths(new float[] {6f, 16f, 40f, 13f, 15f, 10f});

      String[] headers = {"SR.", "QTY / DAYS", "PRODUCT-DESCRIPTION", "RATE", "AMOUNT", "REF"};
      for (String h : headers) {
        PdfPCell th = new PdfPCell(new Phrase(h, headerFont));
        th.setBackgroundColor(brandNavy);
        th.setPadding(6f);
        th.setHorizontalAlignment(Element.ALIGN_CENTER);
        th.setBorderColor(brandNavy);
        lineTable.addCell(th);
      }

      int lineIdx = 0;
      for (Map<String, Object> line : lines) {
        Color rowBg = (lineIdx % 2 == 0) ? Color.WHITE : lightBg;
        lineIdx++;

        PdfPCell cSr = new PdfPCell(new Phrase(String.valueOf(line.get("lineNo")), normalFont));
        cSr.setHorizontalAlignment(Element.ALIGN_CENTER);
        cSr.setBackgroundColor(rowBg);
        cSr.setBorderColor(borderColor);
        cSr.setPadding(5f);
        lineTable.addCell(cSr);

        String qtyDays = line.get("quantity") + " × " + line.get("days");
        PdfPCell cQty = new PdfPCell(new Phrase(qtyDays, normalFont));
        cQty.setHorizontalAlignment(Element.ALIGN_CENTER);
        cQty.setBackgroundColor(rowBg);
        cQty.setBorderColor(borderColor);
        cQty.setPadding(5f);
        lineTable.addCell(cQty);

        PdfPCell cDesc =
            new PdfPCell(new Phrase(String.valueOf(line.get("description")), normalFont));
        cDesc.setBackgroundColor(rowBg);
        cDesc.setBorderColor(borderColor);
        cDesc.setPadding(5f);
        lineTable.addCell(cDesc);

        PdfPCell cRate = new PdfPCell(new Phrase(formatCurrency(line.get("rate")), normalFont));
        cRate.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cRate.setBackgroundColor(rowBg);
        cRate.setBorderColor(borderColor);
        cRate.setPadding(5f);
        lineTable.addCell(cRate);

        PdfPCell cAmt = new PdfPCell(new Phrase(formatCurrency(line.get("amount")), normalFont));
        cAmt.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cAmt.setBackgroundColor(rowBg);
        cAmt.setBorderColor(borderColor);
        cAmt.setPadding(5f);
        lineTable.addCell(cAmt);

        String ref = line.get("reference") == null ? "" : String.valueOf(line.get("reference"));
        PdfPCell cRef = new PdfPCell(new Phrase(ref, smallFont));
        cRef.setHorizontalAlignment(Element.ALIGN_CENTER);
        cRef.setBackgroundColor(rowBg);
        cRef.setBorderColor(borderColor);
        cRef.setPadding(5f);
        lineTable.addCell(cRef);
      }

      doc.add(lineTable);
      doc.add(new Paragraph(" ", smallFont));

      PdfPTable totalsTable = new PdfPTable(2);
      totalsTable.setWidthPercentage(45);
      totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
      totalsTable.setWidths(new float[] {50f, 50f});

      addTotalRow(
          totalsTable,
          "Subtotal",
          formatCurrency(bill.get("subtotal")),
          normalFont,
          normalFont,
          borderColor);
      if (nonZero(bill.get("discount"))) {
        addTotalRow(
            totalsTable,
            "Discount",
            "−" + formatCurrency(bill.get("discount")),
            normalFont,
            normalFont,
            borderColor);
      }
      if (nonZero(bill.get("freight"))) {
        addTotalRow(
            totalsTable,
            "Freight / Transport",
            formatCurrency(bill.get("freight")),
            normalFont,
            normalFont,
            borderColor);
      }
      if (nonZero(bill.get("tax_amount"))) {
        addTotalRow(
            totalsTable,
            "Tax",
            formatCurrency(bill.get("tax_amount")),
            normalFont,
            normalFont,
            borderColor);
      }
      addTotalRow(
          totalsTable,
          "GROSS TOTAL",
          formatCurrency(bill.get("gross_total")),
          boldFont,
          boldFont,
          borderColor,
          lightBg);
      addTotalRow(
          totalsTable,
          "Advance Received",
          formatCurrency(bill.get("advance_paid")),
          normalFont,
          normalFont,
          borderColor);

      BigDecimal gross = (BigDecimal) bill.get("gross_total");
      BigDecimal advance = (BigDecimal) bill.get("advance_paid");
      BigDecimal balance = gross.subtract(advance);
      addTotalRow(
          totalsTable,
          "NET BALANCE DUE",
          formatCurrency(balance),
          boldFont,
          boldFont,
          borderColor,
          lightBg);

      doc.add(totalsTable);

      if (bill.get("notes") != null && !String.valueOf(bill.get("notes")).isBlank()) {
        doc.add(new Paragraph(" ", smallFont));
        PdfPTable notesTable = new PdfPTable(1);
        notesTable.setWidthPercentage(100);
        PdfPCell notesCell = new PdfPCell();
        notesCell.setBackgroundColor(lightBg);
        notesCell.setBorderColor(borderColor);
        notesCell.setPadding(6f);
        notesCell.addElement(new Paragraph("Notes & Instructions:", boldFont));
        notesCell.addElement(new Paragraph(String.valueOf(bill.get("notes")), normalFont));
        notesTable.addCell(notesCell);
        doc.add(notesTable);
      }

      Paragraph footer =
          new Paragraph("SA Productions · Computer-generated commercial document", smallFont);
      footer.setAlignment(Element.ALIGN_CENTER);
      footer.setSpacingBefore(18f);
      doc.add(footer);

      doc.close();

      String filename =
          String.valueOf(bill.get("bill_number")).replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
      String base64 = Base64.getEncoder().encodeToString(out.toByteArray());
      return Map.of("base64", base64, "filename", filename, "contentType", "application/pdf");
    } catch (Exception e) {
      throw new IllegalStateException("Could not generate the PDF invoice document.", e);
    }
  }

  private void addMetaCell(
      PdfPTable table,
      String label,
      String value,
      Font labelFont,
      Font valFont,
      Color bg,
      Color border,
      int colspan) {
    PdfPCell cell = new PdfPCell();
    cell.setColspan(colspan);
    cell.setBackgroundColor(bg);
    cell.setBorderColor(border);
    cell.setPadding(5f);
    Paragraph pLabel = new Paragraph(label.toUpperCase(), labelFont);
    pLabel.setSpacingAfter(1f);
    cell.addElement(pLabel);
    cell.addElement(new Paragraph(value, valFont));
    table.addCell(cell);
  }

  private void addTotalRow(
      PdfPTable table, String label, String value, Font labelFont, Font valFont, Color border) {
    addTotalRow(table, label, value, labelFont, valFont, border, Color.WHITE);
  }

  private void addTotalRow(
      PdfPTable table,
      String label,
      String value,
      Font labelFont,
      Font valFont,
      Color border,
      Color bg) {
    PdfPCell cLabel = new PdfPCell(new Phrase(label, labelFont));
    cLabel.setBorderColor(border);
    cLabel.setBackgroundColor(bg);
    cLabel.setPadding(5f);
    table.addCell(cLabel);

    PdfPCell cVal = new PdfPCell(new Phrase(value, valFont));
    cVal.setBorderColor(border);
    cVal.setBackgroundColor(bg);
    cVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
    cVal.setPadding(5f);
    table.addCell(cVal);
  }

  private String formatCurrency(Object val) {
    if (val == null) return "INR 0.00";
    BigDecimal num = val instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(val));
    return "INR " + new DecimalFormat("#,##0.00").format(num);
  }

  private boolean nonZero(Object val) {
    if (val == null) return false;
    BigDecimal num = val instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(val));
    return num.signum() != 0;
  }

  private void refreshPaymentStatus(UUID id, Map<String, Object> bill) {
    UUID invoice = (UUID) bill.get("canonical_invoice_id");
    if (invoice == null) return;
    BigDecimal total = (BigDecimal) bill.get("gross_total");
    BigDecimal paid =
        jdbc.queryForObject(
            "SELECT coalesce(sum(a.amount),0) FROM finance_invoice_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.invoice_id=? AND t.status='POSTED'",
            BigDecimal.class,
            invoice);
    if (paid == null) paid = ZERO();
    String status =
        paid.compareTo(total) >= 0 ? "PAID" : paid.signum() > 0 ? "PARTIALLY_PAID" : "ISSUED";
    jdbc.update(
        "UPDATE billing_bills SET status=?,updated_at=now() WHERE id=? AND status NOT IN ('CANCELLED','DRAFT')",
        status,
        id);
  }

  private void validateTaxMode(String mode, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {
    if (!Set.of("NONE", "CGST_SGST", "IGST", "CUSTOM").contains(mode))
      throw ApiException.badRequest("BILL_TAX_MODE_INVALID", "Unsupported GST mode.");
    if ("NONE".equals(mode) && (cgst.signum() != 0 || sgst.signum() != 0 || igst.signum() != 0))
      throw ApiException.badRequest("BILL_TAX_MODE_MISMATCH", "NONE cannot carry tax rates.");
    if ("CGST_SGST".equals(mode) && igst.signum() != 0)
      throw ApiException.badRequest("BILL_TAX_MODE_MISMATCH", "CGST/SGST mode cannot carry IGST.");
    if ("IGST".equals(mode) && (cgst.signum() != 0 || sgst.signum() != 0))
      throw ApiException.badRequest("BILL_TAX_MODE_MISMATCH", "IGST mode cannot carry CGST/SGST.");
  }

  private BigDecimal tax(BigDecimal base, BigDecimal rate) {
    if (rate.compareTo(HUNDRED) > 0)
      throw ApiException.badRequest("BILL_TAX_RATE_INVALID", "Tax rate cannot exceed 100%.");
    return base.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
  }

  private BigDecimal money(BigDecimal n) {
    return n == null ? ZERO() : n.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal nonnegative(BigDecimal n) {
    if (n.signum() < 0)
      throw ApiException.badRequest("BILL_NEGATIVE_AMOUNT", "Bill amounts cannot be negative.");
    return n.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal ZERO() {
    return BigDecimal.ZERO.setScale(2);
  }

  private String blank(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private void requireParty(UUID id) {
    if (id == null
        || jdbc.queryForObject(
                "SELECT count(*) FROM finance_counterparties WHERE id=?", Long.class, id)
            == 0) throw ApiException.notFound("BILL_CUSTOMER_NOT_FOUND", "Customer was not found.");
  }

  private void requireProduction(UUID id) {
    if (jdbc.queryForObject("SELECT count(*) FROM productions WHERE id=?", Long.class, id) == 0)
      throw ApiException.notFound("BILL_PRODUCTION_NOT_FOUND", "Production was not found.");
  }

  private Map<String, Object> one(String sql, Object... args) {
    return jdbc.queryForList(sql, args).stream()
        .findFirst()
        .orElseThrow(() -> ApiException.notFound("BILL_NOT_FOUND", "Bill was not found."));
  }

  private void row(Sheet sheet, int row, int col, String label, Object value) {
    Row r = sheet.getRow(row);
    if (r == null) r = sheet.createRow(row);
    r.createCell(col).setCellValue(label);
    Cell v = r.createCell(col + 1);
    if (value instanceof Number n) v.setCellValue(n.doubleValue());
    else v.setCellValue(value == null ? "" : String.valueOf(value));
  }
}
