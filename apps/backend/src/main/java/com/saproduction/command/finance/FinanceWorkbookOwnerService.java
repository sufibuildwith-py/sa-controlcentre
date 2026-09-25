package com.saproduction.command.finance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Workbook-backed owner positions. Source movements are never posted a second time. */
@Service
@ConditionalOnProperty(name = "app.mode", havingValue = "demo")
public class FinanceWorkbookOwnerService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final FinanceWorkbookEmployeeService employees;
  private final FinanceWorkbookPartyService parties;

  public FinanceWorkbookOwnerService(JdbcTemplate jdbc, ObjectMapper json, FinanceWorkbookEmployeeService employees, FinanceWorkbookPartyService parties) {
    this.jdbc = jdbc;
    this.json = json;
    this.employees = employees;
    this.parties = parties;
  }

  public Map<String, Object> summary() {
    Map<String, Object> batch = latestBatch();
    if (batch == null) return Map.of("available", false, "owners", List.of(), "transfers", Map.of());
    UUID id = (UUID) batch.get("id");
    List<Map<String, Object>> az = new ArrayList<>();
    az.add(segment(id, "az 26", "AZ_CASH", "Earlier · Cash", 3, "B", "C", "D", null));
    az.add(segment(id, "az 26", "AZ_BANK", "Earlier · Bank", 3, "G", "H", "I", null));
    az.add(segment(id, "az-2", "AZ_CURRENT", "Current history", 3, "B", "C", "D", null));
    List<Map<String, Object>> ak = new ArrayList<>();
    for (String sheet : List.of("G Pay Aakash 26", "Ak-2")) {
      for (int start = 0; start <= 25; start += 5) {
        String balanceColumn = column(start + 1);
        String inColumn = column(start + 2);
        String outColumn = column(start + 3);
        String slot = "AK_" + start;
        Map<String, Object> control = cells(id, sheet, 2);
        boolean hasActivity = money(control.get(inColumn)).signum() != 0 || money(control.get(outColumn)).signum() != 0;
        if (!hasActivity) continue;
        String label = monthLabel(id, sheet, start);
        ak.add(segment(id, sheet, slot, label, 2, balanceColumn, inColumn, outColumn, label));
      }
    }
    BigDecimal azEarlier = money(cells(id, "az 26", 3).get("L"));
    BigDecimal azOpening = money(cells(id, "az-2", 6).get("C"));
    BigDecimal akEarlier = ak.stream().filter(s -> "G Pay Aakash 26".equals(s.get("sheet")))
        .map(s -> (BigDecimal) s.get("projection")).reduce((left, right) -> right).orElse(BigDecimal.ZERO);
    Map<String, Object> julyOpeningCells = cells(id, "Ak-2", 4);
    BigDecimal akJulyOpening = "last balance".equalsIgnoreCase(String.valueOf(julyOpeningCells.get("B")))
        ? money(julyOpeningCells.get("C")) : BigDecimal.ZERO;
    Map<String, Object> azCurrent = az.getLast();
    Map<String, Object> akCurrent = ak.stream().filter(s -> "Ak-2".equals(s.get("sheet")))
        .reduce((left, right) -> right).orElse(null);
    List<Map<String, Object>> owners = new ArrayList<>();
    owners.add(owner("AZ", "Azeem", az, azEarlier, azOpening, azCurrent, id,
        azEarlier.compareTo(azOpening) == 0 ? "VERIFIED" : "DIFFERS_FROM_EARLIER"));
    owners.add(owner("AK", "Akash", ak, akEarlier, akJulyOpening, akCurrent, id,
        akEarlier.compareTo(akJulyOpening) == 0 ? "VERIFIED" : "NOT_PROVEN_BY_EARLIER_SHEET"));
    var result = new LinkedHashMap<String, Object>();
    result.put("available", true);
    result.put("batchId", id);
    result.put("workbookSha256", batch.get("workbook_sha256"));
    result.put("owners", owners);
    result.put("transferCount", count("SELECT count(*) FROM finance_migration_identity_links WHERE batch_id=? AND relation_type='OWNER_TRANSFER'", id));
    result.put("transferTotal", amount("SELECT coalesce(sum(p.amount),0) FROM finance_migration_identity_links l JOIN finance_migration_facts p ON p.id=l.primary_fact_id WHERE l.batch_id=? AND l.relation_type='OWNER_TRANSFER'", id));
    result.put("parity", parity(id, owners));
    return result;
  }

  public Map<String, Object> movements(String owner, String month, String from, String to, String direction,
      String status, String businessType, String search, int page, int size) {
    checkOwner(owner);
    if (page < 0 || page > 100_000 || size < 1 || size > 100) throw ApiException.badRequest("INVALID_PAGE", "Choose a valid page and size up to 100.");
    if (!List.of("", "IN", "OUT").contains(direction)) throw ApiException.badRequest("INVALID_DIRECTION", "Choose money in or money out.");
    if (!List.of("", "LINKED", "LEGACY", "REVIEW_REQUIRED", "TRANSFER").contains(status)) throw ApiException.badRequest("INVALID_STATUS", "Choose a valid link status.");
    if (!List.of("", "PRODUCTION", "EMPLOYEE", "PARTY", "EQUIPMENT", "EXPENSE", "INVOICE", "TRANSFER", "OTHER").contains(businessType)) throw ApiException.badRequest("INVALID_BUSINESS_TYPE", "Choose a valid business type.");
    if (search != null && search.length() > 100) throw ApiException.badRequest("INVALID_SEARCH", "Search is too long.");
    if (!month.isBlank() && !month.matches("2026-(0[1-9]|1[0-2])")) throw ApiException.badRequest("INVALID_MONTH", "Choose a valid 2026 workbook month.");
    LocalDate first = parseDate(from);
    LocalDate last = parseDate(to);
    Map<String, Object> batch = latestBatch();
    if (batch == null) return Map.of("available", false, "items", List.of(), "total", 0, "page", page, "size", size);
    String account = owner.equals("AZ") ? "AZ-2" : "AK-2";
    String block = month.isBlank() ? "" : monthSlot(owner, month);
    String searchPattern = "%" + (search == null ? "" : search.trim().toLowerCase(Locale.ROOT)).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    String base = """
      WITH movements AS (
        SELECT f.id,f.row_id AS "rowId",r.sheet_name AS sheet,r.source_row AS "sourceRow",r.source_range AS "sourceRange",f.source_range AS "sourceCell",
          f.slot,f.event_date AS date,f.raw_name AS description,f.amount,f.direction,f.classification,f.issue_code AS "issueCode",
          regexp_replace(f.slot,'_(IN|OUT)$','') AS block,
          sum(CASE WHEN f.direction='IN' THEN f.amount ELSE -f.amount END) OVER
            (PARTITION BY r.sheet_name,regexp_replace(f.slot,'_(IN|OUT)$','') ORDER BY r.source_row,f.slot ROWS UNBOUNDED PRECEDING) AS "positionAfter",
          EXISTS(SELECT 1 FROM finance_migration_links l WHERE l.owner_fact_id=f.id) AS linked,
          EXISTS(SELECT 1 FROM finance_migration_identity_links l WHERE l.relation_type='OWNER_TRANSFER'
            AND (l.primary_fact_id=f.id OR l.duplicate_fact_id=f.id)) AS transfer,
          (SELECT d.event_type FROM finance_migration_links l JOIN finance_migration_facts d ON d.id=l.domain_fact_id
            WHERE l.owner_fact_id=f.id ORDER BY d.id LIMIT 1) AS "linkedEventType"
        FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id
        WHERE f.batch_id=? AND f.source_role='OWNER' AND f.account_code=? AND f.amount>0
      )
      """;
    String where = " WHERE 1=1"
        + " AND (?::date IS NULL OR date>=?::date) AND (?::date IS NULL OR date<=?::date)"
        + " AND (?='' OR direction=?)"
        + " AND (?='' OR CASE WHEN transfer THEN 'TRANSFER' WHEN linked THEN 'LINKED' WHEN classification='REVIEW_REQUIRED' THEN 'REVIEW_REQUIRED' WHEN lower(trim(description))='last balance' THEN 'OPENING_POSITION' ELSE 'LEGACY' END=?)"
        + " AND (?='' OR CASE WHEN transfer THEN 'TRANSFER' WHEN \"linkedEventType\" IN ('CLIENT_RECEIPT','CONTRACTED_REVENUE') OR \"linkedEventType\" LIKE 'PRODUCTION_%' THEN 'PRODUCTION' WHEN \"linkedEventType\" LIKE 'EMPLOYEE_%' OR \"linkedEventType\" LIKE '%SALARY%' THEN 'EMPLOYEE' WHEN \"linkedEventType\" LIKE 'EQUIPMENT_%' THEN 'EQUIPMENT' WHEN \"linkedEventType\" LIKE 'INVOICE%' THEN 'INVOICE' WHEN \"linkedEventType\" LIKE 'COUNTERPARTY_%' THEN 'PARTY' WHEN \"linkedEventType\" LIKE '%EXPENSE%' THEN 'EXPENSE' ELSE 'OTHER' END=?)"
        + " AND (?='' OR lower(concat_ws(' ',description,sheet,amount::text,coalesce(\"linkedEventType\",''))) LIKE ? ESCAPE '\\'"
        + " OR EXISTS(SELECT 1 FROM finance_migration_links ml JOIN finance_migration_facts df ON df.id=ml.domain_fact_id JOIN finance_migration_rows dr ON dr.id=df.row_id WHERE ml.owner_fact_id=movements.id AND lower(concat_ws(' ',dr.raw_values->'cells'->>'B',dr.raw_values->'cells'->>'C',dr.raw_values->'cells'->>'D')) LIKE ? ESCAPE '\\'))";
    // AK month columns are independent source blocks. Missing dates must not hide their movements.
    if (!month.isBlank()) where += owner.equals("AZ") ? " AND to_char(date,'YYYY-MM')=?" : " AND sheet=? AND block=?";
    List<Object> args = new ArrayList<>(List.of(batch.get("id"), account));
    args.add(first); args.add(first); args.add(last); args.add(last);
    args.add(direction); args.add(direction); args.add(status); args.add(status);
    args.add(businessType); args.add(businessType);
    args.add(search == null ? "" : search.trim()); args.add(searchPattern); args.add(searchPattern);
    if (!month.isBlank()) {
      if (owner.equals("AZ")) args.add(month);
      else { args.add(Integer.parseInt(month.substring(5)) <= 6 ? "G Pay Aakash 26" : "Ak-2"); args.add(block); }
    }
    long total = jdbc.queryForObject(base + "SELECT count(*) FROM movements" + where, Long.class, args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add(size); pageArgs.add(page * size);
    List<Map<String, Object>> items = jdbc.queryForList(base + "SELECT * FROM movements" + where
        + " ORDER BY sheet,block,\"sourceRow\",slot LIMIT ? OFFSET ?", pageArgs.toArray());
    Map<String, String> monthLabels = new LinkedHashMap<>();
    for (Map<String, Object> item : items) decorateMovement(item, (UUID) batch.get("id"), monthLabels);
    return Map.of("available", true, "items", items, "total", total, "page", page, "size", size);
  }

  public Map<String, Object> movement(UUID id) {
    Map<String, Object> batch = latestBatch();
    if (batch == null) throw ApiException.notFound("WORKBOOK_NOT_FOUND", "No owner workbook is loaded.");
    employees.prepare((UUID)batch.get("id"));
    parties.prepare((UUID)batch.get("id"));
    var rows = jdbc.queryForList("SELECT f.id,f.row_id AS \"rowId\",f.account_code AS \"accountCode\",f.slot,f.event_date AS date,"
        + "f.raw_name AS description,f.amount,f.direction,f.classification,f.issue_code AS \"issueCode\","
        + "(SELECT coalesce(sum(CASE WHEN prior.direction='IN' THEN prior.amount ELSE -prior.amount END),0) FROM finance_migration_facts prior JOIN finance_migration_rows prior_row ON prior_row.id=prior.row_id WHERE prior.batch_id=f.batch_id AND prior_row.sheet_name=r.sheet_name AND regexp_replace(prior.slot,'_(IN|OUT)$','')=regexp_replace(f.slot,'_(IN|OUT)$','') AND (prior_row.source_row<r.source_row OR (prior_row.source_row=r.source_row AND prior.slot<=f.slot))) AS \"positionAfter\","
        + "f.canonical_transaction_id AS \"canonicalTransactionId\",r.sheet_name AS sheet,r.source_row AS \"sourceRow\","
        + "r.source_range AS \"sourceRange\",f.source_range AS \"sourceCell\",r.raw_values::text AS raw_json,b.workbook_sha256 AS \"workbookSha256\" "
        + "FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id "
        + "JOIN finance_migration_batches b ON b.id=f.batch_id "
        + "WHERE f.id=? AND f.batch_id=? AND f.source_role='OWNER'", id, batch.get("id"));
    if (rows.isEmpty()) throw ApiException.notFound("MOVEMENT_NOT_FOUND", "Owner movement not found.");
    Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
    result.remove("raw_json");
    String sheet = (String) result.get("sheet");
    String slot = (String) result.get("slot");
    result.put("period", sheet.equals("az 26") ? "Earlier history" : sheet.equals("az-2") ? "Current history"
        : monthLabel((UUID) batch.get("id"), sheet, Integer.parseInt(slot.substring(3, slot.lastIndexOf('_')))));
    result.put("raw", readRaw((String) rows.getFirst().get("raw_json")));
    result.put("linkedBusiness", jdbc.queryForList("SELECT d.id AS \"factId\",d.event_type AS \"eventType\",d.amount,"
        + "d.classification,link.confidence,domain_row.id AS \"rowId\",trim(domain_row.sheet_name) AS sheet,"
        + "domain_row.source_row AS \"sourceRow\",domain_row.source_range AS \"sourceRange\","
        + "employee.id AS \"employeeEventId\",employee.employee_key AS \"employeeKey\","
        + "domain_row.raw_values->'cells'->>'B' AS venue,domain_row.raw_values->'cells'->>'C' AS client "
        + "FROM finance_migration_links link JOIN finance_migration_facts d ON d.id=link.domain_fact_id "
        + "JOIN finance_migration_rows domain_row ON domain_row.id=d.row_id "
        + "LEFT JOIN finance_workbook_employee_facts employee ON employee.row_id=d.row_id "
        + "AND employee.source_column||domain_row.source_row::text=d.source_range "
        + "WHERE link.owner_fact_id=? "
        + "ORDER BY domain_row.sheet_name,domain_row.source_row", id));
    result.put("partyEvidence",jdbc.queryForList("SELECT e.id AS \"entryId\",b.party_key AS \"partyKey\",b.raw_name AS \"partyName\","
        + "l.allocated_amount AS amount,l.confidence FROM finance_workbook_party_links l "
        + "JOIN finance_workbook_party_entries e ON e.id=l.entry_id JOIN finance_workbook_party_blocks b ON b.id=e.block_id "
        + "WHERE l.fact_id=? AND l.relation_type='OWNER_RECEIPT' ORDER BY b.block_index,e.source_row",id));
    result.put("transferPartner", jdbc.queryForList("SELECT CASE WHEN l.primary_fact_id=? THEN l.duplicate_fact_id ELSE l.primary_fact_id END AS \"movementId\","
        + "l.confidence,l.evidence::text AS evidence FROM finance_migration_identity_links l "
        + "WHERE l.relation_type='OWNER_TRANSFER' AND (l.primary_fact_id=? OR l.duplicate_fact_id=?)", id, id, id));
    result.put("status", !((List<?>) result.get("transferPartner")).isEmpty() ? "TRANSFER"
        : !((List<?>) result.get("linkedBusiness")).isEmpty() ? "LINKED"
        : "REVIEW_REQUIRED".equals(result.get("classification")) ? "REVIEW_REQUIRED"
        : "last balance".equalsIgnoreCase(String.valueOf(result.get("description")).trim()) ? "OPENING_POSITION" : "LEGACY");
    return result;
  }

  public Map<String, Object> transfers(int page, int size) {
    if (page < 0 || page > 100_000 || size < 1 || size > 100) throw ApiException.badRequest("INVALID_PAGE", "Choose a valid page and size up to 100.");
    Map<String, Object> batch = latestBatch();
    if (batch == null) return Map.of("available", false, "items", List.of(), "total", 0);
    UUID id = (UUID) batch.get("id");
    long total = count("SELECT count(*) FROM finance_migration_identity_links WHERE batch_id=? AND relation_type='OWNER_TRANSFER'", id);
    var items = jdbc.queryForList("SELECT l.id,p.id AS \"primaryMovementId\",d.id AS \"otherMovementId\","
        + "CASE WHEN p.direction='OUT' THEN p.account_code ELSE d.account_code END AS \"fromAccount\","
        + "CASE WHEN p.direction='IN' THEN p.account_code ELSE d.account_code END AS \"toAccount\","
        + "p.amount,coalesce(p.event_date,d.event_date) AS date,l.confidence,l.evidence::text AS evidence,"
        + "pr.sheet_name AS \"primarySheet\",pr.source_range AS \"primaryRange\","
        + "dr.sheet_name AS \"otherSheet\",dr.source_range AS \"otherRange\" "
        + "FROM finance_migration_identity_links l "
        + "JOIN finance_migration_facts p ON p.id=l.primary_fact_id "
        + "JOIN finance_migration_facts d ON d.id=l.duplicate_fact_id "
        + "JOIN finance_migration_rows pr ON pr.id=p.row_id "
        + "JOIN finance_migration_rows dr ON dr.id=d.row_id "
        + "WHERE l.batch_id=? AND l.relation_type='OWNER_TRANSFER' "
        + "ORDER BY date,p.id LIMIT ? OFFSET ?", id, size, page * size);
    return Map.of("available", true, "items", items, "total", total, "page", page, "size", size);
  }

  private Map<String, Object> owner(String code, String name, List<Map<String, Object>> segments,
      BigDecimal previous, BigDecimal opening, Map<String, Object> current, UUID batch, String continuity) {
    String account = code.equals("AZ") ? "AZ-2" : "AK-2";
    var result = new LinkedHashMap<String, Object>();
    result.put("code", code);
    result.put("name", name);
    result.put("previousPosition", previous);
    result.put("openingReference", opening);
    result.put("continuity", continuity);
    result.put("position", current == null ? BigDecimal.ZERO : current.get("projection"));
    result.put("moneyIn", current == null ? BigDecimal.ZERO : current.get("moneyIn"));
    result.put("moneyOut", current == null ? BigDecimal.ZERO : current.get("moneyOut"));
    result.put("newMoneyIn", current == null ? BigDecimal.ZERO : ((BigDecimal) current.get("moneyIn")).subtract(code.equals("AZ") ? opening : BigDecimal.ZERO));
    result.put("segments", segments);
    result.put("earlierRows", count("SELECT count(*) FROM finance_migration_rows WHERE batch_id=? AND trim(sheet_name)=?", batch, code.equals("AZ") ? "az 26" : "G Pay Aakash 26"));
    result.put("currentRows", count("SELECT count(*) FROM finance_migration_rows WHERE batch_id=? AND trim(sheet_name)=?", batch, code.equals("AZ") ? "az-2" : "Ak-2"));
    result.put("linkedMovements", count("SELECT count(DISTINCT f.id) FROM finance_migration_facts f JOIN finance_migration_links l ON l.owner_fact_id=f.id WHERE f.batch_id=? AND f.account_code=?", batch, account));
    result.put("unlinkedMovements", count("SELECT count(*) FROM finance_migration_facts f WHERE f.batch_id=? AND f.source_role='OWNER' AND f.account_code=? AND lower(trim(coalesce(f.raw_name,'')))<>'last balance' AND NOT EXISTS(SELECT 1 FROM finance_migration_links l WHERE l.owner_fact_id=f.id) AND NOT EXISTS(SELECT 1 FROM finance_migration_identity_links l WHERE l.relation_type='OWNER_TRANSFER' AND (l.primary_fact_id=f.id OR l.duplicate_fact_id=f.id))", batch, account));
    return result;
  }

  private Map<String, Object> segment(UUID batch, String sheet, String slot, String label, int row,
      String balanceColumn, String inColumn, String outColumn, String month) {
    Map<String, Object> control = cells(batch, sheet, row);
    BigDecimal workbook = money(control.get(balanceColumn));
    BigDecimal incoming = amount("SELECT coalesce(sum(amount),0) FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id WHERE f.batch_id=? AND trim(r.sheet_name)=? AND f.slot=?", batch, sheet, slot + "_IN");
    BigDecimal outgoing = amount("SELECT coalesce(sum(amount),0) FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id WHERE f.batch_id=? AND trim(r.sheet_name)=? AND f.slot=?", batch, sheet, slot + "_OUT");
    var result = new LinkedHashMap<String, Object>();
    result.put("sheet", sheet);
    result.put("block", slot);
    result.put("label", label);
    result.put("month", month);
    if (month != null) {
      int monthNumber = -1;
      for (Month candidate : Month.values()) {
        if (candidate.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).equalsIgnoreCase(month.substring(0, Math.min(3, month.length())))) {
          monthNumber = candidate.getValue();
          break;
        }
      }
      if (monthNumber > 0) result.put("monthKey", "2026-%02d".formatted(monthNumber));
    }
    result.put("moneyIn", incoming);
    result.put("moneyOut", outgoing);
    result.put("projection", incoming.subtract(outgoing));
    result.put("workbookPosition", workbook);
    result.put("difference", incoming.subtract(outgoing).subtract(workbook));
    result.put("controlCells", Map.of("position", balanceColumn + row, "in", inColumn + row, "out", outColumn + row));
    result.put("movementCount", count("SELECT count(*) FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id WHERE f.batch_id=? AND trim(r.sheet_name)=? AND f.slot IN (?,?)", batch, sheet, slot + "_IN", slot + "_OUT"));
    return result;
  }

  private List<Map<String, Object>> parity(UUID batch, List<Map<String, Object>> owners) {
    var result = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> owner : owners) {
      @SuppressWarnings("unchecked") var segments = (List<Map<String, Object>>) owner.get("segments");
      for (Map<String, Object> segment : segments) {
        var item = new LinkedHashMap<String, Object>();
        item.put("metric", owner.get("code") + " " + segment.get("label"));
        item.put("workbook", segment.get("workbookPosition"));
        item.put("projection", segment.get("projection"));
        item.put("difference", segment.get("difference"));
        item.put("status", ((BigDecimal) segment.get("difference")).signum() == 0 ? "MATCH" : "MISMATCH");
        result.add(item);
      }
    }
    for (int row : List.of(12, 13)) {
      Map<String, Object> cells = cells(batch, "Final", row);
      BigDecimal workbook = money(cells.get("J"));
      String owner = row == 12 ? "AZ" : "AK";
      BigDecimal projection = (BigDecimal) owners.get(row == 12 ? 0 : 1).get("position");
      var item = new LinkedHashMap<String, Object>();
      item.put("metric", "Final " + owner + " reference");
      item.put("workbook", workbook);
      item.put("projection", projection);
      item.put("difference", projection.subtract(workbook));
      item.put("status", projection.compareTo(workbook) == 0 ? "MATCH" : "MISMATCH");
      result.add(item);
    }
    return result;
  }

  private String monthLabel(UUID batch, String sheet, int start) {
    Map<String, Object> header = cells(batch, sheet, 1);
    Object title = header.get(column(start));
    if (title instanceof String name && !name.isBlank()) return name.trim();
    var dates = jdbc.queryForList("SELECT min(f.event_date) AS first_date FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id WHERE f.batch_id=? AND trim(r.sheet_name)=? AND f.slot IN (?,?)", batch, sheet, "AK_" + start + "_IN", "AK_" + start + "_OUT");
    Object first = dates.getFirst().get("first_date");
    if (first instanceof java.sql.Date date) return date.toLocalDate().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    return Month.of(start / 5 + (sheet.equals("Ak-2") ? 7 : 1)).getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
  }

  private String monthSlot(String owner, String month) {
    int number = Integer.parseInt(month.substring(5));
    if (owner.equals("AZ")) return "";
    if (number <= 6) return "AK_" + (number - 1) * 5;
    return "AK_" + (number - 7) * 5;
  }

  private void decorateMovement(Map<String, Object> movement, UUID batch, Map<String, String> monthLabels) {
    String sheet = (String) movement.get("sheet");
    String block = (String) movement.get("block");
    movement.put("owner", sheet.startsWith("az") ? "AZ" : "AK");
    String period = sheet.equals("az 26") ? "Earlier history" : sheet.equals("az-2") ? "Current history"
        : monthLabels.computeIfAbsent(sheet + block, ignored -> monthLabel(batch, sheet, Integer.parseInt(block.substring(3))));
    movement.put("period", period);
    movement.put("status", Boolean.TRUE.equals(movement.get("transfer")) ? "TRANSFER" : Boolean.TRUE.equals(movement.get("linked")) ? "LINKED" : "REVIEW_REQUIRED".equals(movement.get("classification")) ? "REVIEW_REQUIRED" : "last balance".equalsIgnoreCase(String.valueOf(movement.get("description")).trim()) ? "OPENING_POSITION" : "LEGACY");
    movement.put("businessType", businessType((String) movement.get("linkedEventType"), Boolean.TRUE.equals(movement.get("transfer"))));
  }

  private String businessType(String event, boolean transfer) {
    if (transfer) return "TRANSFER";
    if (event == null) return "OTHER";
    if (event.contains("PRODUCTION") || event.contains("CLIENT_RECEIPT")) return "PRODUCTION";
    if (event.contains("EMPLOYEE") || event.contains("SALARY")) return "EMPLOYEE";
    if (event.contains("EQUIPMENT")) return "EQUIPMENT";
    if (event.contains("INVOICE")) return "INVOICE";
    if (event.contains("COUNTERPARTY")) return "PARTY";
    if (event.contains("EXPENSE")) return "EXPENSE";
    return "OTHER";
  }

  private void checkOwner(String owner) {
    if (!List.of("AZ", "AK").contains(owner)) throw ApiException.badRequest("INVALID_OWNER", "Choose AZ or AK.");
  }

  private LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) return null;
    try { return LocalDate.parse(value); }
    catch (Exception error) { throw ApiException.badRequest("INVALID_DATE", "Choose a valid ISO date."); }
  }

  private Map<String, Object> latestBatch() {
    var rows = jdbc.queryForList("SELECT id,workbook_sha256 FROM finance_migration_batches ORDER BY created_at DESC,id DESC LIMIT 1");
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private Map<String, Object> cells(UUID batch, String sheet, int row) {
    var rows = jdbc.queryForList("SELECT raw_values::text AS raw_json FROM finance_migration_rows WHERE batch_id=? AND trim(sheet_name)=? AND source_row=? LIMIT 1", batch, sheet, row);
    if (rows.isEmpty()) return Map.of();
    @SuppressWarnings("unchecked") var cells = (Map<String, Object>) readRaw((String) rows.getFirst().get("raw_json")).get("cells");
    return cells;
  }

  private Map<String, Object> readRaw(String value) {
    try { return json.readValue(value, new TypeReference<>() {}); }
    catch (Exception error) { throw new IllegalStateException("Stored workbook row cannot be read", error); }
  }

  private BigDecimal money(Object value) {
    return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }

  private BigDecimal amount(String sql, Object... args) {
    return jdbc.queryForObject(sql, BigDecimal.class, args);
  }

  private long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  private String column(int index) {
    StringBuilder value = new StringBuilder();
    do { value.insert(0, (char) ('A' + index % 26)); index = index / 26 - 1; } while (index >= 0);
    return value.toString();
  }
}
