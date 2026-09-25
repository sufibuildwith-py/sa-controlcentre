package com.saproduction.command.finance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workbook-only commercial projections. They retain the evidence of GST, LED, Sound and the
 * unsuffixed शीट4 reference sheet without manufacturing canonical postings or equipment stock.
 */
@Service
@ConditionalOnProperty(name = "app.mode", havingValue = "demo")
public class FinanceWorkbookCommercialService {
  private static final Pattern SUM_F = Pattern.compile("(?i)sum\\(f(\\d+):f(\\d+)\\)");
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  private record SourceRow(UUID id, int number, Map<String, Object> cells, Map<String, Object> formulas) {
    Object cell(String column) { return cells.get(column); }
    String formula(String column) { return string(formulas.get(column)); }
  }
  private record Purchase(UUID id, SourceRow row, String book, BigDecimal amount) {}

  public FinanceWorkbookCommercialService(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Transactional
  public Map<String, Object> gstSummary() {
    UUID batch = latestBatch();
    if (batch == null) return Map.of("available", false);
    ensure(batch);
    var totals = jdbc.queryForMap("SELECT count(*) AS invoices,coalesce(sum(base_amount),0) AS base,coalesce(sum(igst+cgst+sgst),0) AS tax,"
        + "coalesce(sum(total),0) AS total,coalesce(sum(tds),0) AS tds,coalesce(sum(cash_received),0) AS cash,"
        + "coalesce(sum(outstanding),0) AS outstanding,count(*) FILTER (WHERE parity_status='MISMATCH') AS parity_mismatches,"
        + "count(*) FILTER (WHERE party_key_link IS NOT NULL) AS party_links,count(*) FILTER (WHERE production_row_id IS NOT NULL) AS production_links "
        + "FROM finance_workbook_gst_invoices WHERE batch_id=?", batch);
    Long ownerLinks = jdbc.queryForObject("SELECT count(*) FROM finance_workbook_gst_settlements WHERE batch_id=? AND owner_fact_id IS NOT NULL", Long.class, batch);
    return map("available", true, "workbookSha256", sha(batch), "invoiceCount", totals.get("invoices"), "base", totals.get("base"),
        "tax", totals.get("tax"), "total", totals.get("total"), "tds", totals.get("tds"), "cash", totals.get("cash"),
        "outstanding", totals.get("outstanding"), "parityMismatches", totals.get("parity_mismatches"), "partyLinks", totals.get("party_links"),
        "productionLinks", totals.get("production_links"), "ownerLinks", ownerLinks);
  }

  @Transactional
  public Map<String, Object> invoices(String search, String status, int page, int size) {
    page(page, size);
    UUID batch = latestBatch();
    if (batch == null) return page(false, List.of(), 0L, page, size);
    ensure(batch);
    if (!List.of("", "OUTSTANDING", "SETTLED", "LEGACY").contains(status)) throw ApiException.badRequest("INVALID_STATUS", "Choose a GST status filter.");
    if (search.length() > 100) throw ApiException.badRequest("INVALID_SEARCH", "Search is too long.");
    String q = "%" + escape(search.toLowerCase(Locale.ROOT)) + "%";
    String where = "batch_id=? AND (?='' OR (?='OUTSTANDING' AND outstanding>0) OR (?='SETTLED' AND outstanding=0) OR (?='LEGACY' AND parity_status<>'MATCH')) "
        + "AND (?='' OR lower(concat_ws(' ',invoice_number,party_name,gstin,base_amount::text,total::text)) LIKE ? ESCAPE '\\')";
    Object[] args = {batch, status, status, status, status, search, q};
    Long total = jdbc.queryForObject("SELECT count(*) FROM finance_workbook_gst_invoices WHERE " + where, Long.class, args);
    var rows = jdbc.queryForList("SELECT id,invoice_number AS \"invoiceNumber\",invoice_date AS date,raw_date AS \"rawDate\",party_name AS \"partyName\",gstin,"
        + "base_amount AS base,igst,cgst,sgst,total,tds,cash_received AS cash,outstanding,workbook_outstanding AS \"workbookOutstanding\","
        + "parity_status AS \"parityStatus\",party_key_link AS \"partyKey\",production_row_id AS \"productionRowId\",source_row AS \"sourceRow\" "
        + "FROM finance_workbook_gst_invoices WHERE " + where + " ORDER BY invoice_date NULLS LAST,source_row LIMIT ? OFFSET ?", append(args, size, page * size));
    return page(true, rows, total, page, size);
  }

  @Transactional
  public Map<String, Object> invoice(UUID id) {
    UUID batch = requiredBatch(); ensure(batch);
    var rows = jdbc.queryForList("SELECT i.id,i.invoice_number AS \"invoiceNumber\",i.invoice_date AS date,i.raw_date AS \"rawDate\",i.party_name AS \"partyName\",i.party_key AS \"partyKey\",i.gstin,"
        + "i.base_amount AS base,i.igst,i.cgst,i.sgst,i.total,i.tds,i.cash_received AS cash,i.outstanding,i.workbook_outstanding AS \"workbookOutstanding\",i.parity_status AS \"parityStatus\","
        + "i.party_key_link AS \"partyKeyLink\",i.production_row_id AS \"productionRowId\",i.source_row AS \"sourceRow\",r.raw_values::text AS raw_json,x.workbook_sha256 AS \"workbookSha256\" "
        + "FROM finance_workbook_gst_invoices i JOIN finance_migration_rows r ON r.id=i.row_id JOIN finance_migration_batches x ON x.id=i.batch_id WHERE i.id=? AND i.batch_id=?", id, batch);
    if (rows.isEmpty()) throw ApiException.notFound("GST_INVOICE_NOT_FOUND", "Workbook invoice not found.");
    var result = new LinkedHashMap<String, Object>(rows.getFirst());
    result.put("raw", raw((String) result.remove("raw_json")));
    result.put("settlements", jdbc.queryForList("SELECT s.id,s.settlement_kind AS kind,s.payment_slot AS \"slot\",s.amount,f.account_code AS \"accountCode\","
        + "f.row_id AS \"ownerRowId\",r.sheet_name AS \"ownerSheet\",f.source_range AS \"ownerCell\" FROM finance_workbook_gst_settlements s "
        + "LEFT JOIN finance_migration_facts f ON f.id=s.owner_fact_id LEFT JOIN finance_migration_rows r ON r.id=f.row_id WHERE s.invoice_id=? ORDER BY CASE WHEN s.settlement_kind='TDS' THEN 0 ELSE 1 END,s.payment_slot", id));
    return result;
  }

  @Transactional
  public Map<String, Object> purchaseSummary() {
    UUID batch = latestBatch(); if (batch == null) return Map.of("available", false); ensure(batch);
    var books = jdbc.queryForList("SELECT book,count(*) AS records,coalesce(sum(purchase_amount),0) AS purchased,coalesce(sum(paid),0) AS allocated_paid,"
        + "coalesce(sum(outstanding),0) AS line_outstanding FROM finance_workbook_purchase_records WHERE batch_id=? GROUP BY book ORDER BY book", batch);
    var payments = jdbc.queryForList("SELECT book,coalesce(sum(amount),0) AS paid,count(*) AS payment_records,coalesce(sum(amount) FILTER (WHERE owner_fact_id IS NOT NULL),0) AS owner_linked FROM finance_workbook_purchase_payments WHERE batch_id=? GROUP BY book", batch);
    Map<String, Map<String, Object>> paymentByBook = new HashMap<>(); for (var item : payments) paymentByBook.put((String) item.get("book"), item);
    for (var book : books) {
      var paid = paymentByBook.getOrDefault((String) book.get("book"), Map.of("paid", BigDecimal.ZERO, "payment_records", 0L, "owner_linked", BigDecimal.ZERO));
      BigDecimal purchased = (BigDecimal) book.get("purchased"), paidValue = (BigDecimal) paid.get("paid");
      book.put("paid", paidValue); book.put("outstanding", purchased.subtract(paidValue)); book.put("paymentRecords", paid.get("payment_records")); book.put("ownerLinked", paid.get("owner_linked"));
    }
    var accessory = jdbc.queryForMap("SELECT count(*) FILTER (WHERE disposition='ACTIVE_SOURCE') AS records,coalesce(sum(quantity) FILTER (WHERE disposition='ACTIVE_SOURCE' AND reference_kind='ACCESSORY'),0) AS accessory_quantity,"
        + "coalesce(sum(quantity) FILTER (WHERE disposition='ACTIVE_SOURCE' AND reference_kind='EQUIPMENT_REFERENCE'),0) AS equipment_quantity,"
        + "count(*) FILTER (WHERE disposition='DUPLICATE_SNAPSHOT') AS duplicate_snapshots,count(*) FILTER (WHERE hq_equipment_id IS NOT NULL) AS hq_links "
        + "FROM finance_workbook_accessory_references WHERE batch_id=?", batch);
    return map("available", true, "workbookSha256", sha(batch), "books", books, "accessories", accessory);
  }

  @Transactional
  public Map<String, Object> purchases(String book, String search, int page, int size) {
    page(page, size); UUID batch = latestBatch(); if (batch == null) return page(false, List.of(), 0L, page, size); ensure(batch);
    if (!List.of("ALL", "LED", "SOUND").contains(book)) throw ApiException.badRequest("INVALID_BOOK", "Choose LED, Sound, or all purchases.");
    if (search.length() > 100) throw ApiException.badRequest("INVALID_SEARCH", "Search is too long.");
    String q = "%" + escape(search.toLowerCase(Locale.ROOT)) + "%";
    String where = "batch_id=? AND (?='ALL' OR book=?) AND (?='' OR lower(concat_ws(' ',description,raw_date,purchase_amount::text)) LIKE ? ESCAPE '\\')";
    Object[] args = {batch, book, book, search, q};
    Long total = jdbc.queryForObject("SELECT count(*) FROM finance_workbook_purchase_records WHERE " + where, Long.class, args);
    var rows = jdbc.queryForList("SELECT id,book,source_row AS \"sourceRow\",purchase_date AS date,raw_date AS \"rawDate\",description,rate,tax,quantity,purchase_amount AS \"purchaseAmount\",paid,outstanding,"
        + "hq_equipment_id AS \"hqEquipmentId\",hq_link_confidence AS \"hqLinkConfidence\" FROM finance_workbook_purchase_records WHERE " + where + " ORDER BY book,purchase_date NULLS LAST,source_row LIMIT ? OFFSET ?", append(args, size, page * size));
    return page(true, rows, total, page, size);
  }

  @Transactional
  public Map<String, Object> purchase(UUID id) {
    UUID batch = requiredBatch(); ensure(batch);
    var rows = jdbc.queryForList("SELECT p.id,p.book,p.source_row AS \"sourceRow\",p.purchase_date AS date,p.raw_date AS \"rawDate\",p.description,p.rate,p.tax,p.quantity,p.purchase_amount AS \"purchaseAmount\",p.paid,p.outstanding,"
        + "p.hq_equipment_id AS \"hqEquipmentId\",p.hq_link_confidence AS \"hqLinkConfidence\",r.raw_values::text AS raw_json,x.workbook_sha256 AS \"workbookSha256\" "
        + "FROM finance_workbook_purchase_records p JOIN finance_migration_rows r ON r.id=p.row_id JOIN finance_migration_batches x ON x.id=p.batch_id WHERE p.id=? AND p.batch_id=?", id, batch);
    if (rows.isEmpty()) throw ApiException.notFound("PURCHASE_NOT_FOUND", "Workbook purchase not found.");
    var result = new LinkedHashMap<String, Object>(rows.getFirst()); result.put("raw", raw((String) result.remove("raw_json")));
    result.put("payments", jdbc.queryForList("SELECT pay.id,pay.payment_date AS date,pay.raw_date AS \"rawDate\",pay.description,pay.amount,pay.source_row AS \"sourceRow\","
        + "f.account_code AS \"accountCode\",f.row_id AS \"ownerRowId\",row.sheet_name AS \"ownerSheet\",f.source_range AS \"ownerCell\" FROM finance_workbook_purchase_payment_allocations a "
        + "JOIN finance_workbook_purchase_payments pay ON pay.id=a.payment_id LEFT JOIN finance_migration_facts f ON f.id=pay.owner_fact_id LEFT JOIN finance_migration_rows row ON row.id=f.row_id WHERE a.purchase_id=? ORDER BY pay.source_row", id));
    return result;
  }

  @Transactional
  public Map<String, Object> accessories(String kind, String search, int page, int size) {
    page(page, size); UUID batch = latestBatch(); if (batch == null) return page(false, List.of(), 0L, page, size); ensure(batch);
    if (!List.of("ALL", "ACCESSORY", "EQUIPMENT_REFERENCE").contains(kind)) throw ApiException.badRequest("INVALID_KIND", "Choose an equipment reference type.");
    if (search.length() > 100) throw ApiException.badRequest("INVALID_SEARCH", "Search is too long.");
    String q = "%" + escape(search.toLowerCase(Locale.ROOT)) + "%";
    String where = "batch_id=? AND disposition='ACTIVE_SOURCE' AND (?='ALL' OR reference_kind=?) AND (?='' OR lower(concat_ws(' ',description,quantity::text)) LIKE ? ESCAPE '\\')";
    Object[] args = {batch, kind, kind, search, q};
    Long total = jdbc.queryForObject("SELECT count(*) FROM finance_workbook_accessory_references WHERE " + where, Long.class, args);
    var rows = jdbc.queryForList("SELECT id,reference_kind AS \"kind\",source_row AS \"sourceRow\",source_block AS \"sourceBlock\",description,quantity,list_price AS \"listPrice\",pre_tax_price AS \"preTaxPrice\",value_amount AS \"valueAmount\",hq_equipment_id AS \"hqEquipmentId\",hq_link_confidence AS \"hqLinkConfidence\" FROM finance_workbook_accessory_references WHERE " + where + " ORDER BY reference_kind,source_row LIMIT ? OFFSET ?", append(args, size, page * size));
    return page(true, rows, total, page, size);
  }

  private void ensure(UUID batch) {
    jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "finance-commercial-" + batch);
    Integer present = jdbc.queryForObject("SELECT count(*) FROM finance_workbook_gst_invoices WHERE batch_id=?", Integer.class, batch);
    if (present != null && present > 0) return;
    parseGst(batch, rows(batch, "GST"));
    parsePurchases(batch, "LED", rows(batch, "LED"));
    parsePurchases(batch, "SOUND", rows(batch, "Sound"));
    parseAccessories(batch, rows(batch, "शीट4"));
  }

  private void parseGst(UUID batch, List<SourceRow> rows) {
    Map<String, String> header = Map.of();
    for (SourceRow row : rows) {
      if (looksLikeGstHeader(row)) { header = gstHeader(row); continue; }
      if (header.isEmpty()) continue;
      String invoice = string(row.cell(header.get("invoice")));
      BigDecimal base = money(row.cell(header.get("base"))), total = money(row.cell(header.get("total")));
      String party = string(row.cell(header.get("party"))), rawDate = string(row.cell(header.get("date")));
      if (invoice.isBlank() || party.isBlank() || base.signum() <= 0 || total.signum() <= 0) continue;
      UUID id = UUID.randomUUID(); BigDecimal tds = money(row.cell(header.get("tds"))), cash = BigDecimal.ZERO;
      List<Map.Entry<String,String>> slots = header.entrySet().stream().filter(e -> e.getKey().startsWith("cash_")).sorted(Map.Entry.comparingByKey()).toList();
      for (var slot : slots) cash = cash.add(money(row.cell(slot.getValue())));
      BigDecimal outstanding = total.subtract(tds).subtract(cash); BigDecimal workbookOutstanding = moneyOrNull(row.cell(header.get("balance")));
      String parity = workbookOutstanding == null ? "NO_SOURCE_CONTROL" : workbookOutstanding.compareTo(outstanding) == 0 ? "MATCH" : "MISMATCH";
      jdbc.update("INSERT INTO finance_workbook_gst_invoices(id,batch_id,row_id,source_row,invoice_number,invoice_date,raw_date,party_name,party_key,gstin,base_amount,igst,cgst,sgst,total,tds,cash_received,outstanding,workbook_outstanding,parity_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          id,batch,row.id(),row.number(),invoice,date(rawDate),rawDate,party,key(party),string(row.cell(header.get("gstin"))),base,money(row.cell(header.get("igst"))),money(row.cell(header.get("cgst"))),money(row.cell(header.get("sgst"))),total,tds,cash,outstanding,workbookOutstanding,parity);
      if (tds.signum() > 0) insertGstSettlement(batch,id,"TDS","TDS",tds,null);
      for (var slot : slots) { BigDecimal amount = money(row.cell(slot.getValue())); if (amount.signum() > 0) insertGstSettlement(batch,id,"CASH",slot.getKey().substring(5),amount,null); }
    }
    strictGstLinks(batch);
  }

  private void insertGstSettlement(UUID batch, UUID invoice, String kind, String slot, BigDecimal amount, UUID ownerFact) {
    jdbc.update("INSERT INTO finance_workbook_gst_settlements(id,invoice_id,batch_id,settlement_kind,payment_slot,amount,owner_fact_id,evidence) VALUES (?,?,?,?,?,?,?,?::jsonb)", UUID.randomUUID(),invoice,batch,kind,slot,amount,ownerFact,"{}");
  }

  private void strictGstLinks(UUID batch) {
    // Exact normalized party identity is enough for a party ledger relationship; it makes no cash claim.
    jdbc.update("UPDATE finance_workbook_gst_invoices i SET party_key_link=i.party_key WHERE i.batch_id=? AND EXISTS (SELECT 1 FROM finance_workbook_party_blocks p WHERE p.batch_id=i.batch_id AND p.party_key=i.party_key)", batch);
    // Only a unique, same-date, same-amount owner receipt with the same normalized raw name is linked.
    var settlements = jdbc.queryForList("SELECT s.id,i.invoice_date,i.party_key,s.amount FROM finance_workbook_gst_settlements s JOIN finance_workbook_gst_invoices i ON i.id=s.invoice_id WHERE s.batch_id=? AND s.settlement_kind='CASH'", batch);
    for (var settlement : settlements) {
      var matches = jdbc.queryForList("SELECT f.id FROM finance_migration_facts f WHERE f.batch_id=? AND f.source_role='OWNER' AND f.direction='IN' AND f.amount=? AND f.event_date=? AND regexp_replace(lower(coalesce(f.raw_name,'')),'[^a-z0-9]+','','g')=?", batch, settlement.get("amount"), settlement.get("invoice_date"), settlement.get("party_key"));
      if (matches.size() == 1) jdbc.update("UPDATE finance_workbook_gst_settlements SET owner_fact_id=?,evidence=?::jsonb WHERE id=?",matches.getFirst().get("id"),"{\"match\":\"exact date amount party\"}",settlement.get("id"));
    }
  }

  private void parsePurchases(UUID batch, String book, List<SourceRow> rows) {
    List<Purchase> purchases = new ArrayList<>();
    for (SourceRow row : rows) {
      if (row.number() <= 2) continue;
      String description = string(row.cell("B")); BigDecimal amount = money(row.cell("F"));
      if (!description.isBlank() && amount.signum() > 0) {
        UUID id = UUID.randomUUID(); UUID hq = headquartersMatch(key(description));
        jdbc.update("INSERT INTO finance_workbook_purchase_records(id,batch_id,row_id,book,source_row,purchase_date,raw_date,description,description_key,rate,tax,quantity,purchase_amount,paid,outstanding,hq_equipment_id,hq_link_confidence) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'EXACT')",
            id,batch,row.id(),book,row.number(),date(string(row.cell("A"))),string(row.cell("A")),description,key(description),moneyOrNull(row.cell("C")),moneyOrNull(row.cell("D")),decimalOrNull(row.cell("E")),amount,BigDecimal.ZERO,amount,hq);
        purchases.add(new Purchase(id,row,book,amount));
      }
    }
    for (SourceRow row : rows) {
      if (row.number() <= 2) continue;
      BigDecimal amount = money(row.cell("G")); if (amount.signum() <= 0) continue;
      Matcher matcher = SUM_F.matcher(row.formula("G")); Integer start = null, end = null;
      if (matcher.find()) { start = Integer.valueOf(matcher.group(1)); end = Integer.valueOf(matcher.group(2)); }
      UUID payment = UUID.randomUUID(); UUID owner = strictOwnerPayment(batch, amount, date(string(row.cell("A"))), key(string(row.cell("B"))));
      jdbc.update("INSERT INTO finance_workbook_purchase_payments(id,batch_id,book,row_id,source_row,payment_date,raw_date,description,amount,formula_group_start,formula_group_end,owner_fact_id,evidence) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)",
          payment,batch,book,row.id(),row.number(),date(string(row.cell("A"))),string(row.cell("A")),string(row.cell("B")),amount,start,end,owner,owner==null?"{}":"{\"match\":\"exact date amount description\"}");
      if (start != null) allocateFormulaGroup(payment, purchases, start, end, amount);
      else {
        var same = purchases.stream().filter(p -> p.row().number()==row.number() && p.amount().compareTo(amount)==0).toList();
        if (same.size()==1) allocate(payment,same.getFirst().id(),amount);
      }
    }
    jdbc.update("UPDATE finance_workbook_purchase_records p SET paid=coalesce((SELECT sum(a.amount) FROM finance_workbook_purchase_payment_allocations a WHERE a.purchase_id=p.id),0),outstanding=p.purchase_amount-coalesce((SELECT sum(a.amount) FROM finance_workbook_purchase_payment_allocations a WHERE a.purchase_id=p.id),0) WHERE p.batch_id=? AND p.book=?",batch,book);
  }

  private void allocateFormulaGroup(UUID payment, List<Purchase> purchases, int start, int end, BigDecimal amount) {
    var group = purchases.stream().filter(p -> p.row().number() >= start && p.row().number() <= end).toList();
    BigDecimal sum = group.stream().map(Purchase::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (!group.isEmpty() && sum.compareTo(amount)==0) for (var purchase : group) allocate(payment,purchase.id(),purchase.amount());
  }
  private void allocate(UUID payment, UUID purchase, BigDecimal amount) { jdbc.update("INSERT INTO finance_workbook_purchase_payment_allocations(payment_id,purchase_id,amount) VALUES (?,?,?)",payment,purchase,amount); }

  private void parseAccessories(UUID batch, List<SourceRow> rows) {
    List<List<SourceRow>> snapshots = new ArrayList<>();
    for (int i=0;i<rows.size();i++) if (normalize(rows.get(i).cell("A")).contains("accessories box")) {
      List<SourceRow> snapshot=new ArrayList<>(); for(int j=i+2;j<rows.size();j++) { var row=rows.get(j); if (string(row.cell("B")).isBlank() || decimalOrNull(row.cell("C"))==null) break; snapshot.add(row); } if(!snapshot.isEmpty()) snapshots.add(snapshot);
    }
    List<SourceRow> first = snapshots.isEmpty()?List.of():snapshots.getFirst();
    for (var snapshot : snapshots) for (var row : snapshot) {
      boolean active = snapshot == first; UUID id=UUID.randomUUID(); UUID duplicate = null;
      if (!active) duplicate = jdbc.queryForObject("SELECT id FROM finance_workbook_accessory_references WHERE batch_id=? AND reference_kind='ACCESSORY' AND disposition='ACTIVE_SOURCE' AND description_key=? AND quantity=? LIMIT 1",UUID.class,batch,key(string(row.cell("B"))),decimalOrNull(row.cell("C")));
      UUID hq=headquartersMatch(key(string(row.cell("B"))));
      jdbc.update("INSERT INTO finance_workbook_accessory_references(id,batch_id,row_id,source_row,source_block,reference_kind,description,description_key,quantity,disposition,duplicate_of_id,hq_equipment_id,hq_link_confidence) VALUES (?,?,?,?,?,'ACCESSORY',?,?,?,?,?,?,'EXACT')",id,batch,row.id(),row.number(),"Accessories Box",string(row.cell("B")),key(string(row.cell("B"))),decimalOrNull(row.cell("C")),active?"ACTIVE_SOURCE":"DUPLICATE_SNAPSHOT",duplicate,hq);
    }
    for (var row : rows) {
      BigDecimal quantity=decimalOrNull(row.cell("H")), value=moneyOrNull(row.cell("K")); String description=string(row.cell("G"));
      if (row.number()<1 || description.isBlank() || quantity==null || value==null || value.signum()<=0) continue;
      UUID hq=headquartersMatch(key(description));
      jdbc.update("INSERT INTO finance_workbook_accessory_references(id,batch_id,row_id,source_row,source_block,reference_kind,description,description_key,quantity,list_price,pre_tax_price,value_amount,disposition,hq_equipment_id,hq_link_confidence) VALUES (?,?,?,?,?,'EQUIPMENT_REFERENCE',?,?,?,?,?,?, 'ACTIVE_SOURCE',?,'EXACT')",UUID.randomUUID(),batch,row.id(),row.number(),"Equipment costing reference",description,key(description),quantity,moneyOrNull(row.cell("I")),moneyOrNull(row.cell("J")),value,hq);
    }
  }

  private UUID strictOwnerPayment(UUID batch, BigDecimal amount, LocalDate date, String description) {
    if (date==null || description.isBlank()) return null;
    var matches=jdbc.queryForList("SELECT id FROM finance_migration_facts WHERE batch_id=? AND source_role='OWNER' AND direction='OUT' AND amount=? AND event_date=? AND regexp_replace(lower(coalesce(raw_name,'')),'[^a-z0-9]+','','g')=?",batch,amount,date,description);
    return matches.size()==1?(UUID)matches.getFirst().get("id"):null;
  }
  private UUID headquartersMatch(String description) {
    if (description.isBlank()) return null;
    var rows=jdbc.queryForList("SELECT id,name FROM hq_equipment WHERE active=true");
    var matches=rows.stream().filter(row->description.equals(key((String)row.get("name")))).toList();
    return matches.size()==1?(UUID)matches.getFirst().get("id"):null;
  }

  private List<SourceRow> rows(UUID batch,String sheet) {
    var source=jdbc.queryForList("SELECT id,source_row,raw_values::text AS raw_json FROM finance_migration_rows WHERE batch_id=? AND sheet_name=? ORDER BY source_row",batch,sheet);
    List<SourceRow> rows=new ArrayList<>(); for(var row:source) { Map<String,Object> raw=raw((String)row.get("raw_json")); rows.add(new SourceRow((UUID)row.get("id"),((Number)row.get("source_row")).intValue(),object(raw.get("cells")),object(raw.get("formulas")))); } return rows;
  }
  private static boolean looksLikeGstHeader(SourceRow row) { return normalize(row.cell("A")).contains("invoce no") || normalize(row.cell("A")).contains("invoice no"); }
  private static Map<String,String> gstHeader(SourceRow row) {
    Map<String,String> result=new HashMap<>(); for(var e:row.cells().entrySet()) { String label=normalize(e.getValue()), col=e.getKey();
      if(label.contains("invoce no")||label.contains("invoice no")) result.put("invoice",col); else if(label.equals("date"))result.put("date",col); else if(label.contains("party"))result.put("party",col); else if(label.contains("gst no"))result.put("gstin",col); else if(label.equals("amount"))result.put("base",col); else if(label.equals("igst"))result.put("igst",col); else if(label.equals("cgst"))result.put("cgst",col); else if(label.equals("sgst"))result.put("sgst",col); else if(label.equals("total"))result.put("total",col); else if(label.equals("tds"))result.put("tds",col); else if(label.contains("payment"))result.put("cash_"+col,col); else if(label.equals("balance")||label.equals("remaining"))result.put("balance",col);
    } return result;
  }
  private UUID latestBatch(){var rows=jdbc.queryForList("SELECT id FROM finance_migration_batches ORDER BY created_at DESC,id DESC LIMIT 1");return rows.isEmpty()?null:(UUID)rows.getFirst().get("id");}
  private UUID requiredBatch(){UUID batch=latestBatch();if(batch==null)throw ApiException.notFound("WORKBOOK_NOT_FOUND","No workbook is loaded.");return batch;}
  private String sha(UUID batch){return jdbc.queryForObject("SELECT workbook_sha256 FROM finance_migration_batches WHERE id=?",String.class,batch);}
  private static Map<String,Object> map(Object...v){Map<String,Object> out=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)out.put((String)v[i],v[i+1]);return out;}
  private static Map<String,Object> page(boolean available,List<?>items,long total,int page,int size){return map("available",available,"items",items,"total",total,"page",page,"size",size);}
  private static Object[] append(Object[] values,Object...more){Object[] all=java.util.Arrays.copyOf(values,values.length+more.length);System.arraycopy(more,0,all,values.length,more.length);return all;}
  private static void page(int page,int size){if(page<0||size<1||size>100)throw ApiException.badRequest("INVALID_PAGE","Use a page size from 1 to 100.");}
  private Map<String,Object> raw(String input){try{return json.readValue(input,new TypeReference<>(){});}catch(Exception ex){throw new IllegalStateException("Invalid staged workbook row",ex);}}
  @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value){return value instanceof Map<?,?> map?(Map<String,Object>)map:Map.of();}
  private static String string(Object value){return value==null?"":String.valueOf(value).trim();}
  private static String normalize(Object value){return string(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
  private static String key(Object value){return string(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","");}
  private static BigDecimal money(Object value){BigDecimal result=moneyOrNull(value);return result==null?BigDecimal.ZERO:result;}
  private static BigDecimal moneyOrNull(Object value){if(value==null||string(value).isBlank())return null;try{return new BigDecimal(string(value).replace(",","").replace("₹","").trim());}catch(NumberFormatException ex){return null;}}
  private static BigDecimal decimalOrNull(Object value){return moneyOrNull(value);}
  private static LocalDate date(String raw){if(raw==null||raw.isBlank())return null;try{return LocalDate.parse(raw);}catch(DateTimeParseException ex){return null;}}
  private static String escape(String value){return value.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
}
