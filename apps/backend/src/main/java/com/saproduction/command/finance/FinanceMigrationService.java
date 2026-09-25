package com.saproduction.command.finance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.shared.ApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Demo-only workbook staging. Ambiguous owner attribution is recorded, never guessed. */
@Service
public class FinanceMigrationService {
  private static final int MAX_BYTES = 8_000_000;
  private static final Set<String> PRODUCTION_SHEETS = Set.of("Jan 26", "JUL+DEC", "Jul-Dec", "jully dec P3 Led", "Sound Jul+dec", "All Date 26");
  private static final Set<String> OWNER_SHEETS = Set.of("az 26", "az-2", "G Pay Aakash 26", "Ak-2");
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final FinanceMigrationResolver resolver;
  private final FinanceMigrationParityService parity;
  private final FinanceMigrationCanonicalizer canonicalizer;

  public FinanceMigrationService(JdbcTemplate jdbc, ObjectMapper json, FinanceMigrationResolver resolver, FinanceMigrationParityService parity,
                                 FinanceMigrationCanonicalizer canonicalizer) { this.jdbc = jdbc; this.json = json; this.resolver = resolver; this.parity = parity; this.canonicalizer = canonicalizer; }

  @Transactional
  public Map<String,Object> preview(String filename, String encoded) {
    if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx") || encoded == null || encoded.length() > MAX_BYTES * 2)
      throw ApiException.badRequest("WORKBOOK_REQUIRED", "Choose an XLSX workbook under 8 MB.");
    byte[] bytes;
    try { bytes = Base64.getDecoder().decode(encoded); }
    catch (IllegalArgumentException exception) { throw ApiException.badRequest("WORKBOOK_INVALID", "The workbook upload is invalid."); }
    if (bytes.length == 0 || bytes.length > MAX_BYTES) throw ApiException.badRequest("WORKBOOK_TOO_LARGE", "Choose an XLSX workbook under 8 MB.");
    String sha = sha256(bytes);
    jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "finance-import-" + sha);
    var existing = jdbc.queryForList("SELECT id FROM finance_migration_batches WHERE workbook_sha256=? ORDER BY created_at DESC LIMIT 1", sha);
    if (!existing.isEmpty()) return report((UUID)existing.getFirst().get("id"));
    UUID batch = UUID.randomUUID();
    jdbc.update("INSERT INTO finance_migration_batches(id,workbook_sha256,workbook_name,status) VALUES(?,?,?,'PREVIEW')", batch, sha, filename);
    try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      if (workbook.getNumberOfSheets() > 40) throw ApiException.badRequest("WORKBOOK_TOO_MANY_SHEETS", "This workbook has too many sheets.");
      for (Sheet sheet : workbook) stageSheet(batch, sheet);
    } catch (IOException | POIXMLException exception) {
      throw ApiException.badRequest("WORKBOOK_PARSE_FAILED", "The XLSX workbook could not be read.");
    }
    resolver.resolve(batch);
    return report(batch);
  }

  @Transactional
  public Map<String,Object> validate(UUID batch) {
    var existing = one("SELECT id FROM finance_migration_batches WHERE id=? FOR UPDATE", batch);
    if (existing.isEmpty()) throw ApiException.notFound("MIGRATION_NOT_FOUND", "Migration batch not found.");
    resolver.resolve(batch);
    return report(batch);
  }

  public Map<String,Object> report(UUID batch) {
    var rows = one("SELECT id,workbook_sha256,workbook_name,status,created_at FROM finance_migration_batches WHERE id=?", batch);
    if (rows.isEmpty()) throw ApiException.notFound("MIGRATION_NOT_FOUND", "Migration batch not found.");
    var sheets = jdbc.queryForList("SELECT sheet_name AS \"sheetName\",count(*) AS rows,count(*) FILTER (WHERE match_confidence='REVIEW_REQUIRED') AS \"reviewRows\" FROM finance_migration_rows WHERE batch_id=? GROUP BY sheet_name ORDER BY sheet_name", batch);
    var issues = jdbc.queryForList("SELECT i.id,r.sheet_name AS \"sheetName\",r.source_row AS \"sourceRow\",i.issue_code AS code,i.detail,i.status FROM finance_migration_issues i LEFT JOIN finance_migration_rows r ON r.id=i.row_id WHERE i.batch_id=? ORDER BY r.sheet_name,r.source_row LIMIT 100", batch);
    long issueCount = jdbc.queryForObject("SELECT count(*) FROM finance_migration_issues WHERE batch_id=? AND status='OPEN'", Long.class, batch);
    long unmapped = jdbc.queryForObject("SELECT count(*) FROM finance_migration_rows WHERE batch_id=? AND match_confidence='REVIEW_REQUIRED'", Long.class, batch);
    var parityRows = parity.report(batch);
    var resolution = new HashMap<String,Object>(resolver.counts(batch));
    if (parityRows.stream().anyMatch(row -> List.of("SOURCE_CONFLICT","FORMULA_STATIC_CONFLICT","EXTRACTION_SCOPE_MISMATCH").contains(row.get("status")))) {
      @SuppressWarnings("unchecked") var groups = new ArrayList<Map<String,Object>>((List<Map<String,Object>>)resolution.get("issueGroups"));
      for (String reason : List.of("SOURCE_CONFLICT","FORMULA_STATIC_CONFLICT","EXTRACTION_SCOPE_MISMATCH")) {
        long count = parityRows.stream().filter(row -> reason.equals(row.get("status"))).count();
        if (count == 0) continue;
        java.math.BigDecimal mismatch = parityRows.stream().filter(row -> reason.equals(row.get("status")))
            .map(row -> ((java.math.BigDecimal)row.get("sourceLineSum")).subtract((java.math.BigDecimal)row.get("workbook")).abs())
            .reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
        groups.add(Map.of("reason",reason,"count",count,"amount",mismatch));
      }
      resolution.put("issueGroups",groups);
    }
    return Map.of("batch", rows.getFirst(), "sheets", sheets, "openIssueCount", issueCount, "unmappedRowCount", unmapped, "issues", issues, "resolution", resolution, "parity", parityRows,
        "expected", jdbc.queryForList("SELECT sheet_name AS \"sheetName\",source_row AS \"sourceRow\",raw_values->'cells' AS cells FROM finance_migration_rows WHERE batch_id=? AND ((trim(sheet_name)='Jan 26' AND source_row=3) OR (trim(sheet_name)='JUL+DEC' AND source_row=3) OR (trim(sheet_name)='jully dec P3 Led' AND source_row=3) OR (trim(sheet_name)='az-2' AND source_row=3) OR (trim(sheet_name)='Ak-2' AND source_row=2) OR (trim(sheet_name)='LED' AND source_row=2) OR (trim(sheet_name)='Final' AND source_row=8)) ORDER BY sheet_name", batch));
  }

  public Map<String,Object> rows(UUID batch, String sheet, int page) {
    if (page < 0 || page > 100000) throw ApiException.badRequest("INVALID_PAGE", "Choose a valid page.");
    long total = jdbc.queryForObject("SELECT count(*) FROM finance_migration_rows WHERE batch_id=? AND sheet_name=?", Long.class, batch, sheet);
    return Map.of("total", total, "page", page, "items", jdbc.queryForList("SELECT id,source_row AS \"sourceRow\",source_range AS \"sourceRange\",raw_values AS \"rawValues\",legacy_type AS \"legacyType\",match_confidence AS \"matchConfidence\" FROM finance_migration_rows WHERE batch_id=? AND sheet_name=? ORDER BY source_row LIMIT 100 OFFSET ?", batch, sheet, page * 100));
  }

  public Map<String,Object> review(UUID batch, int page) { return resolver.review(batch,page); }
  public Map<String,Object> postProven(UUID batch) {
    Map<String,Object> posted = canonicalizer.postProven(batch);
    Map<String,Object> result = new LinkedHashMap<>(report(batch));
    result.put("postedNow",posted);
    return result;
  }
  public Map<String,Object> decide(UUID batch, String sheet, int sourceRow, String slot, String action, String target, String reason) {
    return resolver.decide(batch,sheet,sourceRow,slot,action,target,reason);
  }

  @Transactional
  public Map<String,Object> resetStaging() {
    long posted = jdbc.queryForObject("SELECT count(*) FROM finance_migration_facts WHERE canonical_transaction_id IS NOT NULL",Long.class);
    if (posted > 0) throw ApiException.conflict("MIGRATION_ALREADY_POSTED","Staged evidence is linked to posted transactions and cannot be discarded.");
    jdbc.execute("TRUNCATE TABLE finance_migration_batches CASCADE");
    jdbc.update("DELETE FROM finance_migration_aliases WHERE source='WORKBOOK_NORMALIZATION' AND NOT reviewed");
    return Map.of("reset",true,"preservedReviewedOverrides",jdbc.queryForObject("SELECT count(*) FROM finance_migration_overrides",Long.class));
  }

  private void stageSheet(UUID batch, Sheet sheet) {
    String name = sheet.getSheetName();
    String logicalName = name.trim();
    if (sheet.getLastRowNum() > 15000) throw ApiException.badRequest("WORKBOOK_TOO_MANY_ROWS", "One workbook sheet exceeds the safe import limit.");
    for (Row row : sheet) {
      var cells = new LinkedHashMap<String,Object>();
      var formulas = new LinkedHashMap<String,String>();
      for (Cell cell : row) {
        Object value = value(cell);
        if (value != null && !value.toString().isBlank()) cells.put(cell.getAddress().formatAsString().replaceAll("[0-9]", ""), value);
        if (cell.getCellType() == CellType.FORMULA) formulas.put(cell.getAddress().formatAsString().replaceAll("[0-9]", ""), cell.getCellFormula());
      }
      if (cells.isEmpty() && formulas.isEmpty()) continue;
      int sourceRow = row.getRowNum() + 1;
      UUID id = UUID.randomUUID();
      String kind = PRODUCTION_SHEETS.contains(logicalName) ? "PRODUCTION" : OWNER_SHEETS.contains(logicalName) ? "OWNER_ACCOUNT" : switch (logicalName) {
        case "GST" -> "INVOICE";
        case "LED", "Sound" -> "EQUIPMENT_PURCHASE";
        case "Final" -> "GOLDEN_CONTROL";
        case "Varma ji" -> "QUOTATION_DRAFT";
        case "Expence" -> "LEGACY_EXPENSE";
        case "शीट4", "शीट4 (2)" -> "COUNTERPARTY";
        default -> "UNCLASSIFIED";
      };
      String confidence = kind.equals("GOLDEN_CONTROL") || kind.equals("QUOTATION_DRAFT") ? "DISTINCT" : "REVIEW_REQUIRED";
      Map<String,Object> raw = Map.of("cells", cells, "formulas", formulas);
      jdbc.update("INSERT INTO finance_migration_rows(id,batch_id,sheet_name,source_row,source_range,raw_values,legacy_type,match_confidence) VALUES(?,?,?,?,?,?::jsonb,?,?)",
          id, batch, name, sourceRow, "A" + sourceRow + ":" + column(Math.max(0,row.getLastCellNum()-1)) + sourceRow, serialize(raw), kind, confidence);
      resolver.stageRow(batch,id,name,sourceRow,row);
    }
  }

  private Object value(Cell cell) {
    CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
    return switch (type) {
      case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? cell.getLocalDateTimeCellValue().toLocalDate().toString() : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
      case STRING -> cell.getRichStringCellValue().getString().trim();
      case BOOLEAN -> cell.getBooleanCellValue();
      default -> null;
    };
  }


  private List<Map<String,Object>> one(String sql, Object... args) { return jdbc.queryForList(sql,args); }

  private String serialize(Object value) {
    try { return json.writeValueAsString(value); }
    catch (JsonProcessingException exception) { throw new IllegalStateException("Could not stage workbook row", exception); }
  }

  private String sha256(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
  }

  private String column(int index) {
    StringBuilder text = new StringBuilder();
    do { text.insert(0, (char)('A' + index % 26)); index = index / 26 - 1; } while (index >= 0);
    return text.toString();
  }
}
