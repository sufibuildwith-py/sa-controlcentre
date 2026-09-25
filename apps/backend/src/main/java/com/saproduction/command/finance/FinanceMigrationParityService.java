package com.saproduction.command.finance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Source controls are read from staged workbook cells, never copied as constants. */
@Service
public class FinanceMigrationParityService {
  private final JdbcTemplate jdbc;

  public FinanceMigrationParityService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  private record Control(String metric,String sheet,int row,String cell,String event,String direction) {}
  private static final List<Control> CONTROLS = List.of(
      new Control("Jan 26 Total","Jan 26",3,"E","CONTRACTED_REVENUE",null),
      new Control("Jan 26 Add","Jan 26",3,"F","LEGACY_ADJUSTMENT",null),
      new Control("Jan 26 Payment","Jan 26",3,"G","CLIENT_RECEIPT",null),
      new Control("Jan 26 Expense","Jan 26",3,"I","PRODUCTION_EXPENSE_ACCRUAL",null),
      new Control("Jan 26 Budget","Jan 26",3,"J",null,null),
      new Control("Jan 26 Azeem allocation","Jan 26",3,"B",null,null),
      new Control("Jan 26 Akash allocation","Jan 26",2,"K",null,null),
      new Control("JUL+DEC Total","JUL+DEC",3,"E","CONTRACTED_REVENUE",null),
      new Control("JUL+DEC Add","JUL+DEC",3,"F","LEGACY_ADJUSTMENT",null),
      new Control("JUL+DEC Payment","JUL+DEC",3,"G","CLIENT_RECEIPT",null),
      new Control("JUL+DEC Expense","JUL+DEC",3,"I","PRODUCTION_EXPENSE_ACCRUAL",null),
      new Control("JUL+DEC Budget","JUL+DEC",3,"J",null,null),
      new Control("JUL+DEC Azeem allocation","JUL+DEC",3,"B",null,null),
      new Control("JUL+DEC Akash allocation","JUL+DEC",2,"K",null,null),
      new Control("P3 result","jully dec P3 Led",3,"J",null,null),
      new Control("AZ-2 Credit","az-2",3,"C",null,"IN"),
      new Control("AZ-2 Expense","az-2",3,"D",null,"OUT"),
      new Control("AZ-2 Position","az-2",3,"B",null,null),
      new Control("AK-2 From","Ak-2",2,"M",null,"IN"),
      new Control("AK-2 To","Ak-2",2,"N",null,"OUT"),
      new Control("AK-2 Position","Ak-2",2,"L",null,null),
      new Control("LED outstanding","LED",2,"H",null,null),
      new Control("Final control difference","Final",8,"L",null,null));

  public List<Map<String,Object>> report(UUID batch) {
    List<Map<String,Object>> result = new ArrayList<>();
    for (Control control : CONTROLS) {
      var raw = jdbc.queryForList("SELECT raw_values->'cells'->>? AS value,raw_values->'formulas'->>? AS formula FROM finance_migration_rows WHERE batch_id=? AND trim(sheet_name)=? AND source_row=? LIMIT 1",control.cell,control.cell,batch,control.sheet,control.row);
      BigDecimal expected = decimal(raw.isEmpty() ? null : raw.getFirst().get("value"));
      String formula = raw.isEmpty() ? null : (String)raw.getFirst().get("formula");
      BigDecimal canonical = null;
      BigDecimal unresolved = null;
      BigDecimal sourceLineSum = null;
      long sourceFacts = 0;
      long postedFacts = 0;
      if (control.event != null || control.direction != null) {
        String filter = control.event != null ? "f.event_type=?" : "f.source_role='OWNER' AND f.direction=?";
        if (control.sheet.equals("Ak-2")) filter += control.direction.equals("IN") ? " AND f.slot='AK_10_IN'" : " AND f.slot='AK_10_OUT'";
        Object[] args = {batch,control.sheet,control.event != null ? control.event : control.direction};
        var facts = jdbc.queryForMap("SELECT count(*) AS source_count,count(*) FILTER (WHERE f.canonical_transaction_id IS NOT NULL) AS posted_count,coalesce(sum(f.amount),0) AS source_sum,coalesce(sum(f.amount) FILTER (WHERE f.canonical_transaction_id IS NULL),0) AS unresolved_amount FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id WHERE f.batch_id=? AND trim(r.sheet_name)=? AND " + filter,args);
        sourceFacts = ((Number)facts.get("source_count")).longValue();
        postedFacts = ((Number)facts.get("posted_count")).longValue();
        unresolved = (BigDecimal)facts.get("unresolved_amount");
        sourceLineSum = (BigDecimal)facts.get("source_sum");
        if (sourceFacts>0 && sourceFacts==postedFacts) {
          canonical = jdbc.queryForObject("SELECT coalesce(sum(t.amount),0) FROM finance_transactions t WHERE t.id IN (SELECT DISTINCT f.canonical_transaction_id FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id WHERE f.batch_id=? AND trim(r.sheet_name)=? AND " + filter + ")",BigDecimal.class,args);
        }
      }
      BigDecimal formulaReplay = formulaSourceSum(batch,control.sheet,formula);
      if (formulaReplay != null) {
        sourceLineSum = formulaReplay;
        // A control may intentionally exclude rows or add a static adjustment.
        // Do not report the broader staged-column sum as its unresolved balance.
        unresolved = sourceFacts > 0 && postedFacts == 0 ? formulaReplay : null;
      }
      Map<String,Object> row = new LinkedHashMap<>();
      row.put("metric",control.metric);
      row.put("workbook",expected);
      row.put("finance",canonical);
      row.put("difference",expected == null || canonical == null ? null : canonical.subtract(expected));
      row.put("sourceLineSum",sourceLineSum);
      row.put("controlFormula",formula);
      row.put("sourceMethod",formulaReplay == null ? "CLASSIFIED_FACTS" : "EXCEL_FORMULA_REPLAY");
      row.put("status",expected == null ? "SOURCE_MISSING"
          : sourceLineSum != null && sourceFacts>0 && sourceLineSum.compareTo(expected)!=0 ? (formulaReplay == null && formula != null ? "EXTRACTION_SCOPE_MISMATCH" : formulaReplay != null ? "FORMULA_STATIC_CONFLICT" : "SOURCE_CONFLICT")
          : canonical == null ? "UNVERIFIED" : canonical.compareTo(expected)==0 ? "MATCH" : "DIFFERENCE");
      row.put("sourceFactCount",sourceFacts);
      row.put("postedFactCount",postedFacts);
      row.put("unresolvedContribution",unresolved);
      result.add(row);
    }
    return result;
  }

  private static BigDecimal decimal(Object raw) {
    if (raw == null) return null;
    try { return new BigDecimal(raw.toString()); }
    catch (NumberFormatException ignored) { return null; }
  }

  private static final Pattern SUM_FORMULA = Pattern.compile("(?i)^=?\\s*SUM\\(([A-Z]+)(\\d+):([A-Z]+)(\\d+)\\)((?:\\s*[+-]\\s*[A-Z]+\\d+)*)\\s*$");
  private static final Pattern EXTRA_CELL = Pattern.compile("([+-])\\s*([A-Z]+)(\\d+)",Pattern.CASE_INSENSITIVE);

  private BigDecimal formulaSourceSum(UUID batch, String sheet, String formula) {
    if (formula == null) return null;
    var match = SUM_FORMULA.matcher(formula);
    if (!match.matches() || !match.group(1).equalsIgnoreCase(match.group(3))) return null;
    int from = Integer.parseInt(match.group(2)), to = Integer.parseInt(match.group(4));
    if (from<1 || to<from || to-from>5000) return null;
    String column = match.group(1).toUpperCase();
    BigDecimal total = BigDecimal.ZERO;
    for (var row : jdbc.queryForList("SELECT raw_values->'cells'->>? AS value FROM finance_migration_rows WHERE batch_id=? AND trim(sheet_name)=? AND source_row BETWEEN ? AND ?",column,batch,sheet,from,to)) {
      BigDecimal value = decimal(row.get("value"));
      if (value != null) total = total.add(value);
    }
    var extras = EXTRA_CELL.matcher(match.group(5));
    while (extras.find()) {
      String extraColumn = extras.group(2).toUpperCase();
      int extraRow = Integer.parseInt(extras.group(3));
      var cell = jdbc.queryForList("SELECT raw_values->'cells'->>? AS value FROM finance_migration_rows WHERE batch_id=? AND trim(sheet_name)=? AND source_row=? LIMIT 1",extraColumn,batch,sheet,extraRow);
      BigDecimal value = decimal(cell.isEmpty() ? null : cell.getFirst().get("value"));
      if (value == null) return null;
      total = extras.group(1).equals("+") ? total.add(value) : total.subtract(value);
    }
    return total;
  }
}
