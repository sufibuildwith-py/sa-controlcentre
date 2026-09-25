package com.saproduction.command.finance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only production ledger backed by the original workbook cells. */
@Service
@ConditionalOnProperty(name = "app.mode", havingValue = "demo")
public class FinanceWorkbookProductionService {
  private static final List<String> SHEETS = List.of("All Date 26", "Jan 26", "JUL+DEC", "Jul-Dec", "jully dec P3 Led", "Sound Jul+dec");
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final FinanceWorkbookEmployeeService employees;
  private final FinanceWorkbookPartyService parties;

  public FinanceWorkbookProductionService(JdbcTemplate jdbc, ObjectMapper json, FinanceWorkbookEmployeeService employees, FinanceWorkbookPartyService parties) {
    this.jdbc = jdbc;
    this.json = json;
    this.employees = employees;
    this.parties = parties;
  }

  public Map<String, Object> list(String sheet, String search, int page, int size) {
    if (page < 0 || page > 100_000 || size < 1 || size > 100) {
      throw ApiException.badRequest("INVALID_PAGE", "Choose a valid page and page size up to 100.");
    }
    if (sheet != null && !sheet.isBlank() && !SHEETS.contains(sheet)) {
      throw ApiException.badRequest("INVALID_SHEET", "Choose a production workbook sheet.");
    }
    String query = search == null ? "" : search.trim();
    if (query.length() > 100) throw ApiException.badRequest("INVALID_SEARCH", "Search is too long.");
    UUID batch = latestBatch();
    if (batch == null) return Map.of("available", false, "sheets", SHEETS, "items", List.of(), "page", page, "size", size, "total", 0);
    String where = "r.batch_id=? AND trim(r.sheet_name) IN ('All Date 26','Jan 26','JUL+DEC','Jul-Dec','jully dec P3 Led','Sound Jul+dec') "
        + "AND r.source_row >= CASE WHEN trim(r.sheet_name)='All Date 26' THEN 4 ELSE 5 END "
        + "AND (coalesce(r.raw_values->'cells'->>'B','')<>'' OR coalesce(r.raw_values->'cells'->>'C','')<>'') "
        + "AND (?='' OR trim(r.sheet_name)=?) AND (?='' OR lower(concat_ws(' ',r.raw_values->'cells'->>'B',r.raw_values->'cells'->>'C',r.raw_values->'cells'->>'D',r.raw_values->'cells'->>'L')) LIKE ? ESCAPE '\\')";
    String selected = sheet == null ? "" : sheet;
    String pattern = "%" + query.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    long total = jdbc.queryForObject("SELECT count(*) FROM finance_migration_rows r WHERE " + where, Long.class, batch, selected, selected, query, pattern);
    var rows = jdbc.queryForList("SELECT r.id,r.sheet_name,r.source_row,r.source_range,r.raw_values::text AS raw_json "
            + "FROM finance_migration_rows r WHERE " + where + " "
            + "ORDER BY r.sheet_name,r.source_row LIMIT ? OFFSET ?", batch, selected, selected, query, pattern, size, page * size);
    List<Map<String, Object>> items = new ArrayList<>();
    for (var row : rows) items.add(project(row, false));
    var result = new LinkedHashMap<String, Object>();
    result.put("available", true);
    result.put("batchId", batch);
    result.put("sheets", SHEETS);
    result.put("items", items);
    result.put("page", page);
    result.put("size", size);
    result.put("total", total);
    if (!selected.isBlank()) result.put("sourceControl", control(batch, selected));
    return result;
  }

  public Map<String, Object> detail(UUID id) {
    UUID batch = latestBatch();
    if (batch == null) throw ApiException.notFound("WORKBOOK_NOT_FOUND", "No production workbook is loaded.");
    employees.prepare(batch);
    parties.prepare(batch);
    var rows = jdbc.queryForList("SELECT r.id,r.sheet_name,r.source_row,r.source_range,r.raw_values::text AS raw_json "
        + "FROM finance_migration_rows r WHERE r.batch_id=? AND r.id=? AND trim(r.sheet_name) IN "
        + "('All Date 26','Jan 26','JUL+DEC','Jul-Dec','jully dec P3 Led','Sound Jul+dec')", batch, id);
    if (rows.isEmpty()) throw ApiException.notFound("PRODUCTION_ROW_NOT_FOUND", "Workbook row not found.");
    Map<String, Object> result = project(rows.getFirst(), true);
    result.put("sourceFacts", jdbc.queryForList("SELECT slot,event_type,amount,event_date,account_code,classification,issue_code "
        + "FROM finance_migration_facts WHERE row_id=? ORDER BY slot,event_type", id));
    result.put("linkedOwnerEvidence", jdbc.queryForList("SELECT owner.id AS \"movementId\", owner.account_code AS \"accountCode\", owner_row.sheet_name AS sheet, "
        + "owner_row.source_range AS \"sourceRange\", owner.source_range AS \"sourceCell\", link.allocated_amount AS amount, link.confidence "
        + "FROM finance_migration_facts domain_fact "
        + "JOIN finance_migration_links link ON link.domain_fact_id=domain_fact.id "
        + "JOIN finance_migration_facts owner ON owner.id=link.owner_fact_id "
        + "JOIN finance_migration_rows owner_row ON owner_row.id=owner.row_id "
        + "WHERE domain_fact.row_id=? ORDER BY owner_row.sheet_name,owner_row.source_row", id));
    result.put("employeeEvidence",jdbc.queryForList("SELECT id,employee_key AS \"employeeKey\",display_name AS name,kind,amount,"
        + "source_column AS \"sourceColumn\",identity_status AS \"identityStatus\" "
        + "FROM finance_workbook_employee_facts WHERE row_id=? ORDER BY source_column",id));
    result.put("partyEvidence",jdbc.queryForList("SELECT e.id AS \"entryId\",b.party_key AS \"partyKey\",b.raw_name AS \"partyName\","
        + "l.relation_type AS \"relationType\",l.allocated_amount AS amount,l.confidence,b.disposition "
        + "FROM finance_workbook_party_links l JOIN finance_workbook_party_entries e ON e.id=l.entry_id "
        + "JOIN finance_workbook_party_blocks b ON b.id=e.block_id JOIN finance_migration_facts f ON f.id=l.fact_id "
        + "WHERE f.row_id=? ORDER BY b.block_index,e.source_row",id));
    return result;
  }

  private UUID latestBatch() {
    var batches = jdbc.queryForList("SELECT id FROM finance_migration_batches ORDER BY created_at DESC,id DESC LIMIT 1");
    return batches.isEmpty() ? null : (UUID) batches.getFirst().get("id");
  }

  private Map<String, Object> control(UUID batch, String sheet) {
    int row = sheet.equals("All Date 26") ? 2 : 3;
    var rows = jdbc.queryForList("SELECT raw_values::text AS raw_json FROM finance_migration_rows "
        + "WHERE batch_id=? AND trim(sheet_name)=? AND source_row=? LIMIT 1", batch, sheet, row);
    if (rows.isEmpty()) return Map.of();
    Map<String, Object> raw = readRaw((String) rows.getFirst().get("raw_json"));
    @SuppressWarnings("unchecked") Map<String, Object> cells = (Map<String, Object>) raw.get("cells");
    var result = new LinkedHashMap<String, Object>();
    result.put("sourceRow", row);
    boolean allDate = sheet.equals("All Date 26");
    result.put("contracted", moneyOrNull(cells.get(allDate ? "I" : "E")));
    result.put("add", moneyOrNull(cells.get(allDate ? "J" : "F")));
    result.put("payment", moneyOrNull(cells.get(allDate ? "K" : "G")));
    result.put("balance", moneyOrNull(cells.get(allDate ? "L" : "H")));
    result.put("expense", allDate ? null : moneyOrNull(cells.get("I")));
    result.put("legacyBudget", allDate ? null : moneyOrNull(cells.get("J")));
    return result;
  }

  private Map<String, Object> project(Map<String, Object> row, boolean detail) {
    Map<String, Object> raw = readRaw((String) row.get("raw_json"));
    @SuppressWarnings("unchecked") Map<String, Object> cells = (Map<String, Object>) raw.get("cells");
    @SuppressWarnings("unchecked") Map<String, Object> formulas = (Map<String, Object>) raw.get("formulas");
    String sheet = (String) row.get("sheet_name");
    boolean allDate = sheet.trim().equals("All Date 26");
    BigDecimal total = money(cells.get(allDate ? "I" : "E"));
    BigDecimal add = money(cells.get(allDate ? "J" : "F"));
    BigDecimal payment = money(cells.get(allDate ? "K" : "G"));
    BigDecimal received = add.add(payment);
    BigDecimal outstanding = total.subtract(received);
    BigDecimal expense = allDate ? null : money(cells.get("I"));
    BigDecimal storedBalance = moneyOrNull(cells.get(allDate ? "L" : "H"));
    BigDecimal storedBudget = allDate ? null : moneyOrNull(cells.get("J"));
    var result = new LinkedHashMap<String, Object>();
    result.put("id", row.get("id"));
    result.put("sheet", sheet);
    result.put("sourceRow", row.get("source_row"));
    result.put("sourceRange", row.get("source_range"));
    result.put("date", cells.get("A"));
    result.put("venue", cells.get("B"));
    result.put("client", cells.get("C"));
    result.put("service", allDate ? List.of("D", "E", "F", "G", "H").stream().map(cells::get).filter(v -> v != null && !v.toString().isBlank()).toList() : cells.get("D"));
    result.put("total", total);
    result.put("add", add);
    result.put("payment", payment);
    result.put("received", received);
    result.put("outstanding", outstanding);
    result.put("expense", expense);
    result.put("legacyBudget", expense == null ? null : received.subtract(expense));
    result.put("contractedMargin", expense == null ? null : total.subtract(expense));
    result.put("sourceBalance", storedBalance);
    result.put("balanceParity", storedBalance == null ? "NO_SOURCE_VALUE" : storedBalance.compareTo(outstanding) == 0 ? "MATCH" : "MISMATCH");
    result.put("sourceBudget", storedBudget);
    result.put("budgetParity", storedBudget == null || expense == null ? "NO_SOURCE_VALUE" : storedBudget.compareTo(received.subtract(expense)) == 0 ? "MATCH" : "MISMATCH");
    if (detail) {
      result.put("cells", cells);
      result.put("formulas", formulas);
    }
    return result;
  }

  private Map<String, Object> readRaw(String value) {
    try { return json.readValue(value, new TypeReference<>() {}); }
    catch (Exception error) { throw new IllegalStateException("Stored workbook row cannot be read", error); }
  }

  private BigDecimal money(Object value) {
    BigDecimal result = moneyOrNull(value);
    return result == null ? BigDecimal.ZERO : result;
  }

  private BigDecimal moneyOrNull(Object value) {
    if (value instanceof Number || value instanceof String) {
      try { return new BigDecimal(value.toString()); }
      catch (NumberFormatException ignored) { return null; }
    }
    return null;
  }
}
