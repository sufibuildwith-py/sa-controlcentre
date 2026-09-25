package com.saproduction.command.billing;

import com.saproduction.command.finance.FinanceCommands;
import com.saproduction.command.finance.FinancePostingService;
import com.saproduction.command.shared.ApiException;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

    UUID id = UUID.randomUUID();
    BigDecimal subtotal = ZERO();
    for (BillingCommands.Line line : input.lines()) {
      BigDecimal amount = line.quantity().multiply(line.days()).multiply(line.rate()).setScale(2, RoundingMode.HALF_UP);
      subtotal = subtotal.add(amount);
    }
    subtotal = money(subtotal);
    BigDecimal discount = nonnegative(input.discount());
    if (discount.compareTo(subtotal) > 0) throw ApiException.badRequest("BILL_DISCOUNT_EXCEEDS_SUBTOTAL", "Discount cannot exceed the line subtotal.");
    BigDecimal taxable = subtotal.subtract(discount);
    BigDecimal freight = nonnegative(input.freight());
    BigDecimal taxBase = taxable.add(freight);
    BigDecimal cgst = tax(taxBase, input.cgstRate());
    BigDecimal sgst = tax(taxBase, input.sgstRate());
    BigDecimal igst = tax(taxBase, input.igstRate());
    BigDecimal tax = cgst.add(sgst).add(igst);
    BigDecimal total = taxBase.add(tax).setScale(2, RoundingMode.HALF_UP);\n    if (total.signum() <= 0) throw ApiException.badRequest("BILL_TOTAL_INVALID", "A bill must contain a positive total.");\n    if (input.advancePaid().compareTo(total) > 0) throw ApiException.badRequest("BILL_ADVANCE_EXCEEDS_TOTAL", "Advance / already received cannot exceed the bill total.");\n\n    jdbc.update("""
      INSERT INTO billing_bills
      (id,bill_number,bill_date,financial_year,counterparty_id,production_id,event_name,venue,status,tax_mode,gstin,
       cgst_rate,sgst_rate,igst_rate,discount,freight,advance_paid,subtotal,tax_amount,gross_total,notes,payment_terms)
      VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
      """,
      id,input.billNumber().trim(),input.billDate(),input.financialYear().trim(),input.counterpartyId(),input.productionId(),
      blank(input.eventName()),blank(input.venue()),"DRAFT",input.taxMode(),blank(input.gstin()),
      input.cgstRate(),input.sgstRate(),input.igstRate(),discount,freight,input.advancePaid(),subtotal,tax,total,
      blank(input.notes()),blank(input.paymentTerms()));

    int no=1;
    for (BillingCommands.Line line : input.lines()) {
      BigDecimal amount = line.quantity().multiply(line.days()).multiply(line.rate()).setScale(2, RoundingMode.HALF_UP);
      jdbc.update("""
        INSERT INTO billing_bill_lines(id,bill_id,line_no,quantity,days,description,rate,amount,reference)
        VALUES(?,?,?,?,?,?,?,?,?)
        """,UUID.randomUUID(),id,no++,line.quantity(),line.days(),line.description().trim(),line.rate(),amount,blank(line.reference()));
    }
    return get(id);
  }

  @Transactional(readOnly=true)
  public List<Map<String,Object>> list() {
    return jdbc.queryForList("""
      SELECT b.id,b.bill_number AS "billNumber",b.bill_date AS "billDate",b.financial_year AS "financialYear",
             c.display_name AS "customer",b.status,b.gross_total AS "grossTotal",b.advance_paid AS "advancePaid",
             b.gross_total-b.advance_paid-coalesce((SELECT sum(a.amount) FROM finance_invoice_payment_allocations a
               JOIN finance_transactions pt ON pt.id=a.transaction_id
               WHERE a.invoice_id=b.canonical_invoice_id AND pt.status='POSTED'),0) AS "balanceDue"
      FROM billing_bills b JOIN finance_counterparties c ON c.id=b.counterparty_id
      ORDER BY b.bill_date DESC,b.created_at DESC
      """);
  }

  @Transactional(readOnly=true)
  public Map<String,Object> get(UUID id) {
    var bill = one("SELECT b.*,c.display_name AS customer FROM billing_bills b JOIN finance_counterparties c ON c.id=b.counterparty_id WHERE b.id=?",id);
    refreshPaymentStatus(id, bill);
    bill = one("SELECT b.*,c.display_name AS customer FROM billing_bills b JOIN finance_counterparties c ON c.id=b.counterparty_id WHERE b.id=?",id);
    var lines = jdbc.queryForList("SELECT id,line_no AS "lineNo",quantity,days,description,rate,amount,reference FROM billing_bill_lines WHERE bill_id=? ORDER BY line_no",id);
    return Map.of("bill",bill,"lines",lines);
  }

  @Transactional
  public Map<String,Object> issue(UUID id, BillingCommands.Issue input) {
    Map<String,Object> bill = one("SELECT * FROM billing_bills WHERE id=? FOR UPDATE",id);
    String status=(String)bill.get("status");
    if ("CANCELLED".equals(status)) throw ApiException.conflict("BILL_CANCELLED","Cancelled bills cannot be issued.");
    if (!"DRAFT".equals(status)) return get(id);
    UUID existing = (UUID)bill.get("canonical_invoice_id");
    if (existing != null) return get(id);

    BigDecimal subtotal=(BigDecimal)bill.get("subtotal");
    BigDecimal discount=(BigDecimal)bill.get("discount");
    BigDecimal freight=(BigDecimal)bill.get("freight");
    BigDecimal base=subtotal.subtract(discount).add(freight);
    String taxMode=(String)bill.get("tax_mode");
    BigDecimal cgstRate=(BigDecimal)bill.get("cgst_rate");
    BigDecimal sgstRate=(BigDecimal)bill.get("sgst_rate");
    BigDecimal igstRate=(BigDecimal)bill.get("igst_rate");

    FinanceCommands.Invoice command = new FinanceCommands.Invoice(
      input.idempotencyKey(),
      (String)bill.get("bill_number"),
      (String)bill.get("financial_year"),
      ((java.sql.Date)bill.get("bill_date")).toLocalDate(),
      (UUID)bill.get("counterparty_id"),
      (UUID)bill.get("production_id"),
      (String)bill.get("gstin"),
      taxMode,
      base,cgstRate,sgstRate,igstRate,
      BigDecimal.ZERO);

    Map<String,Object> posted=finance.invoice(command);
    UUID transactionId=(UUID)posted.get("id");
    UUID invoiceId = jdbc.queryForObject("SELECT id FROM finance_invoices WHERE source_transaction_id=?",UUID.class,transactionId);
    jdbc.update("UPDATE billing_bills SET status='ISSUED',canonical_invoice_id=?,issued_at=now(),updated_at=now() WHERE id=?",invoiceId,id);
    return get(id);
  }

  @Transactional
  public Map<String,Object> cancel(UUID id) {
    Map<String,Object> bill=one("SELECT * FROM billing_bills WHERE id=? FOR UPDATE",id);
    if (!"DRAFT".equals(bill.get("status"))) throw ApiException.conflict("BILL_NOT_DRAFT","Only draft bills can be cancelled.");
    jdbc.update("UPDATE billing_bills SET status='CANCELLED',cancelled_at=now(),updated_at=now() WHERE id=?",id);
    return get(id);
  }

  @Transactional(readOnly=true)
  public Map<String,Object> export(UUID id) {
    Map<String,Object> data=get(id);
    @SuppressWarnings("unchecked")
    Map<String,Object> bill=(Map<String,Object>)data.get("bill");
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> lines=(List<Map<String,Object>>)data.get("lines");
    try (XSSFWorkbook wb=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
      Sheet sheet=wb.createSheet("INVOICE");
      sheet.setPrintGridlines(false);
      sheet.getPrintSetup().setLandscape(false);
      sheet.getPrintSetup().setFitWidth((short)1);
      sheet.getPrintSetup().setFitHeight((short)0);
      sheet.setAutobreaks(true);

      Font title=wb.createFont(); title.setBold(true); title.setFontHeightInPoints((short)20);
      Font header=wb.createFont(); header.setBold(true); header.setColor(IndexedColors.WHITE.getIndex());
      Font small=wb.createFont(); small.setFontHeightInPoints((short)9);
      CellStyle titleStyle=wb.createCellStyle(); titleStyle.setFont(title);
      CellStyle headerStyle=wb.createCellStyle(); headerStyle.setFont(header); headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      CellStyle money=wb.createCellStyle(); money.setDataFormat(wb.createDataFormat().getFormat("₹#,##0.00"));
      CellStyle date=wb.createCellStyle(); date.setDataFormat(wb.createDataFormat().getFormat("dd-mmm-yyyy"));
      CellStyle wrap=wb.createCellStyle(); wrap.setWrapText(true); wrap.setVerticalAlignment(VerticalAlignment.TOP);

      sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0,0,0,5));
      sheet.getRow(0)==null ? sheet.createRow(0) : sheet.getRow(0);
      sheet.getRow(0).createCell(0).setCellValue("SA PRODUCTIONS"); sheet.getRow(0).getCell(0).setCellStyle(titleStyle);
      sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1,1,0,5));
      sheet.getRow(1).createCell(0).setCellValue("INVOICE");
      sheet.getRow(1).getCell(0).setCellStyle(titleStyle);

      row(sheet,2,0,"Bill No.",bill.get("bill_number")); row(sheet,2,2,"Date",bill.get("bill_date"));
      row(sheet,3,0,"Customer",bill.get("customer")); row(sheet,3,2,"Financial Year",bill.get("financial_year"));
      row(sheet,4,0,"Event",bill.get("event_name")); row(sheet,4,2,"Venue",bill.get("venue"));
      row(sheet,5,0,"GSTIN",bill.get("gstin")); row(sheet,5,2,"Status",bill.get("status"));

      Row h=sheet.createRow(7);
      String[] heads={"SR.","QTY / DAYS","PRODUCT-DESCRIPTION","RATE","AMOUNT","REF"};
      for(int i=0;i<heads.length;i++){Cell cell=h.createCell(i);cell.setCellValue(heads[i]);cell.setCellStyle(headerStyle);}
      int r=8;
      for(Map<String,Object> line:lines){
        Row rr=sheet.createRow(r++);
        rr.createCell(0).setCellValue(((Number)line.get("lineNo")).doubleValue());
        rr.createCell(1).setCellValue(((Number)line.get("quantity")).doubleValue()+" × "+((Number)line.get("days")).doubleValue());
        rr.createCell(2).setCellValue(String.valueOf(line.get("description"))); rr.getCell(2).setCellStyle(wrap);
        rr.createCell(3).setCellValue(((Number)line.get("rate")).doubleValue()); rr.getCell(3).setCellStyle(money);
        rr.createCell(4).setCellValue(((Number)line.get("amount")).doubleValue()); rr.getCell(4).setCellStyle(money);
        rr.createCell(5).setCellValue(String.valueOf(line.get("reference") == null ? "" : line.get("reference")));
      }
      r++;
      row(sheet,r,2,"Subtotal",bill.get("subtotal")); sheet.getRow(r).getCell(3).setCellValue("Discount"); sheet.getRow(r).getCell(4).setCellValue(((Number)bill.get("discount")).doubleValue()); sheet.getRow(r).getCell(4).setCellStyle(money);
      r++; row(sheet,r,2,"Freight / Transport",bill.get("freight"));
      r++; row(sheet,r,2,"Tax",bill.get("tax_amount"));
      r++; row(sheet,r,2,"GROSS TOTAL",bill.get("gross_total"));
      r++; row(sheet,r,2,"Advance / Already Received",bill.get("advance_paid"));
      r++; row(sheet,r,2,"NET BALANCE DUE",((BigDecimal)bill.get("gross_total")).subtract((BigDecimal)bill.get("advance_paid")));

      r+=2; row(sheet,r,0,"Notes",bill.get("notes")); sheet.getRow(r).getCell(0).setCellStyle(wrap);
      r++; row(sheet,r,0,"Payment Terms",bill.get("payment_terms")); sheet.getRow(r).getCell(0).setCellStyle(wrap);

      for(int i=0;i<6;i++) sheet.autoSizeColumn(i);
      sheet.setColumnWidth(2,9000); sheet.setColumnWidth(3,4000); sheet.setColumnWidth(4,4500);
      wb.write(out);
      String filename=String.valueOf(bill.get("bill_number")).replaceAll("[^A-Za-z0-9._-]","_")+".xlsx";
      return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=""+filename+""")
        .body(out.toByteArray());
    } catch(Exception e) {
      throw new IllegalStateException("Could not generate the editable invoice workbook.",e);
    }
  }

  private void refreshPaymentStatus(UUID id, Map<String,Object> bill) {
    UUID invoice=(UUID)bill.get("canonical_invoice_id");
    if(invoice==null) return;
    BigDecimal total=(BigDecimal)bill.get("gross_total");
    BigDecimal paid=jdbc.queryForObject("SELECT coalesce(sum(a.amount),0) FROM finance_invoice_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.invoice_id=? AND t.status='POSTED'",BigDecimal.class,invoice);
    if(paid==null) paid=ZERO();
    String status=paid.compareTo(total)>=0 ? "PAID" : paid.signum()>0 ? "PARTIALLY_PAID" : "ISSUED";
    jdbc.update("UPDATE billing_bills SET status=?,updated_at=now() WHERE id=? AND status NOT IN ('CANCELLED','DRAFT')",status,id);
  }

  private void validateTaxMode(String mode,BigDecimal cgst,BigDecimal sgst,BigDecimal igst){
    if(!Set.of("NONE","CGST_SGST","IGST","CUSTOM").contains(mode)) throw ApiException.badRequest("BILL_TAX_MODE_INVALID","Unsupported GST mode.");
    if("NONE".equals(mode) && (cgst.signum()!=0 || sgst.signum()!=0 || igst.signum()!=0)) throw ApiException.badRequest("BILL_TAX_MODE_MISMATCH","NONE cannot carry tax rates.");
    if("CGST_SGST".equals(mode) && igst.signum()!=0) throw ApiException.badRequest("BILL_TAX_MODE_MISMATCH","CGST/SGST mode cannot carry IGST.");
    if("IGST".equals(mode) && (cgst.signum()!=0 || sgst.signum()!=0)) throw ApiException.badRequest("BILL_TAX_MODE_MISMATCH","IGST mode cannot carry CGST/SGST.");
  }
  private BigDecimal tax(BigDecimal base,BigDecimal rate){ if(rate.compareTo(HUNDRED)>0) throw ApiException.badRequest("BILL_TAX_RATE_INVALID","Tax rate cannot exceed 100%."); return base.multiply(rate).divide(HUNDRED,2,RoundingMode.HALF_UP); }
  private BigDecimal money(BigDecimal n){ return n.setScale(2,RoundingMode.HALF_UP); }
  private BigDecimal nonnegative(BigDecimal n){ if(n.signum()<0) throw ApiException.badRequest("BILL_NEGATIVE_AMOUNT","Bill amounts cannot be negative."); return n.setScale(2,RoundingMode.HALF_UP); }
  private BigDecimal ZERO(){return BigDecimal.ZERO.setScale(2);}
  private String blank(String s){return s==null||s.isBlank()?null:s.trim();}
  private void requireParty(UUID id){if(id==null||jdbc.queryForObject("SELECT count(*) FROM finance_counterparties WHERE id=?",Long.class,id)==0) throw ApiException.notFound("BILL_CUSTOMER_NOT_FOUND","Customer was not found.");}
  private void requireProduction(UUID id){if(jdbc.queryForObject("SELECT count(*) FROM productions WHERE id=?",Long.class,id)==0) throw ApiException.notFound("BILL_PRODUCTION_NOT_FOUND","Production was not found.");}
  private Map<String,Object> one(String sql,Object...args){return jdbc.queryForList(sql,args).stream().findFirst().orElseThrow(()->ApiException.notFound("BILL_NOT_FOUND","Bill was not found."));}
  private void row(Sheet sheet,int row,int col,String label,Object value){Row r=sheet.getRow(row);if(r==null)r=sheet.createRow(row);r.createCell(col).setCellValue(label);Cell v=r.createCell(col+1);if(value instanceof Number n)v.setCellValue(n.doubleValue());else v.setCellValue(value==null?"":String.valueOf(value));}
}
