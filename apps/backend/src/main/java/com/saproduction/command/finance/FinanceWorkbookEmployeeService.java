package com.saproduction.command.finance;

import com.saproduction.command.shared.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Historical staff projection from source columns. This does not create journal entries. */
@Service
@ConditionalOnProperty(name = "app.mode", havingValue = "demo")
public class FinanceWorkbookEmployeeService {
  private record Pair(String key, String name, String payment, String earning, boolean disputed) {}
  private static final List<Pair> STANDARD = List.of(
      new Pair("DOST_UNRESOLVED", "Dost Pay · identity unproven", "M", "", true),
      new Pair("ROSHAN", "Roshan", "N", "O", false),
      new Pair("RAJAN", "Rajan", "P", "Q", false),
      new Pair("AAKASH_STAFF", "Aakash · staff", "R", "S", false),
      new Pair("ASIF_2", "Asif 2.0", "T", "U", false),
      new Pair("ANAND", "Anand", "V", "W", false),
      new Pair("ASIF", "Asif", "X", "Y", false),
      new Pair("VICKY_GADI", "Vicky Gadi", "Z", "AA", false),
      new Pair("SHAKEEL", "Shakeel", "AC", "AB", false),
      new Pair("FAKHRE", "Fakhre", "AE", "AD", false),
      new Pair("SANJAY", "Sanjay", "AG", "AF", false));
  private static final List<Pair> P3 = List.of(
      new Pair("ROSHAN", "Roshan", "M", "N", false),
      new Pair("RAJAN", "Rajan", "O", "P", false),
      new Pair("AAKASH_STAFF", "Aakash · staff", "Q", "R", false),
      new Pair("PK_UNRESOLVED", "PK Pay · identity unproven", "S", "", true),
      new Pair("ASIF_2", "Asif 2.0", "", "T", false),
      new Pair("HIMANSHU", "Himanshu", "V", "U", false),
      new Pair("VICKY_GADI", "Vicky Gadi", "W", "X", false),
      new Pair("VIK_UNRESOLVED", "Vik Pay · identity unproven", "Z", "", true),
      new Pair("SHAKEEL", "Shakeel", "", "Y", false),
      new Pair("FAKHRE", "Fakhre", "AB", "AA", false),
      new Pair("SANJAY", "Sanjay", "AD", "AC", false));
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public FinanceWorkbookEmployeeService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

  private UUID latestBatch() {
    var rows = jdbc.queryForList("SELECT id FROM finance_migration_batches ORDER BY created_at DESC,id DESC LIMIT 1");
    return rows.isEmpty() ? null : (UUID) rows.getFirst().get("id");
  }

  @Transactional
  public void prepare(UUID batch) { ensure(batch); }

  @Transactional
  public Map<String,Object> summary() {
    UUID batch = latestBatch();
    if (batch == null) return Map.of("available",false,"employees",List.of(),"parity",List.of());
    ensure(batch);
    var employees = jdbc.queryForList("SELECT employee_key AS \"key\",max(display_name) AS name,"
        + "coalesce(sum(amount) FILTER (WHERE kind='EARNING'),0) AS earned,"
        + "coalesce(sum(amount) FILTER (WHERE kind='PAYMENT'),0) AS paid,"
        + "coalesce(sum(amount) FILTER (WHERE kind='EARNING'),0)-coalesce(sum(amount) FILTER (WHERE kind='PAYMENT'),0) AS outstanding,"
        + "count(*) AS facts FROM finance_workbook_employee_facts WHERE batch_id=? AND identity_status='FORMULA_PAIRED' "
        + "GROUP BY employee_key ORDER BY name", batch);
    BigDecimal earned = BigDecimal.ZERO, paid = BigDecimal.ZERO;
    for (var person : employees) {
      earned = earned.add((BigDecimal) person.get("earned"));
      paid = paid.add((BigDecimal) person.get("paid"));
    }
    long reviewFacts = jdbc.queryForObject("SELECT count(*) FROM finance_workbook_employee_facts WHERE batch_id=? AND identity_status='IDENTITY_REVIEW'",Long.class,batch);
    long linkedPayments = jdbc.queryForObject("SELECT count(*) FROM finance_workbook_employee_facts e JOIN finance_migration_rows r ON r.id=e.row_id "
        + "WHERE e.batch_id=? AND e.kind='PAYMENT' AND EXISTS (SELECT 1 FROM finance_migration_facts f "
        + "JOIN finance_migration_links l ON l.domain_fact_id=f.id WHERE f.row_id=e.row_id "
        + "AND f.source_range=e.source_column||r.source_row::text)",Long.class,batch);
    var result = new LinkedHashMap<String,Object>();
    result.put("available",true); result.put("employees",employees);
    result.put("earned",earned); result.put("paid",paid); result.put("outstanding",earned.subtract(paid));
    result.put("reviewFacts",reviewFacts); result.put("linkedPayments",linkedPayments);
    result.put("workbookSha256",jdbc.queryForObject("SELECT workbook_sha256 FROM finance_migration_batches WHERE id=?",String.class,batch));
    result.put("parity",parity(batch));
    return result;
  }

  @Transactional
  public Map<String,Object> events(String employee, String sheet, String kind, String search, int page, int size) {
    return events(employee,sheet,kind,search,"","",page,size);
  }

  @Transactional
  public Map<String,Object> events(String employee, String sheet, String kind, String search, String from, String to, int page, int size) {
    if (page < 0 || page > 100_000 || size < 1 || size > 100) throw ApiException.badRequest("INVALID_PAGE","Choose a page size up to 100.");
    UUID batch = latestBatch();
    if (batch == null) return Map.of("available",false,"items",List.of(),"total",0,"page",page,"size",size);
    ensure(batch);
    if (!kind.isBlank() && !List.of("EARNING","PAYMENT").contains(kind)) throw ApiException.badRequest("INVALID_KIND","Choose earning or payment.");
    if (search.length() > 100) throw ApiException.badRequest("INVALID_SEARCH","Search is too long.");
    String fromDate = date(from), toDate = date(to);
    if (fromDate != null && toDate != null && fromDate.compareTo(toDate)>0) throw ApiException.badRequest("INVALID_PERIOD","Start date must precede end date.");
    String pattern = "%" + search.toLowerCase().replace("\\","\\\\").replace("%","\\%").replace("_","\\_") + "%";
    String where = "e.batch_id=? AND (?='' OR e.employee_key=?) AND (?='' OR trim(r.sheet_name)=?) "
        + "AND (?='' OR e.kind=?) AND (CAST(? AS date) IS NULL OR e.event_date>=CAST(? AS date)) "
        + "AND (CAST(? AS date) IS NULL OR e.event_date<=CAST(? AS date)) "
        + "AND (?='' OR lower(concat_ws(' ',e.display_name,r.raw_values->'cells'->>'B',r.raw_values->'cells'->>'C',r.raw_values->'cells'->>'D',e.amount::text)) LIKE ? ESCAPE '\\')";
    Object[] args = {batch,employee,employee,sheet,sheet,kind,kind,fromDate,fromDate,toDate,toDate,search,pattern};
    long total = jdbc.queryForObject("SELECT count(*) FROM finance_workbook_employee_facts e JOIN finance_migration_rows r ON r.id=e.row_id WHERE "+where,Long.class,args);
    String running = "WITH balances AS (SELECT f.id, sum(CASE WHEN f.kind='EARNING' THEN f.amount ELSE -f.amount END) "
        + "OVER(PARTITION BY f.employee_key ORDER BY f.event_date NULLS LAST,rr.sheet_name,rr.source_row,"
        + "CASE WHEN f.kind='EARNING' THEN 0 ELSE 1 END,f.source_column,f.id) AS \"balanceAfter\" "
        + "FROM finance_workbook_employee_facts f JOIN finance_migration_rows rr ON rr.id=f.row_id "
        + "WHERE f.batch_id=? AND f.identity_status='FORMULA_PAIRED') ";
    var items = jdbc.queryForList(running+"SELECT e.id,e.employee_key AS \"employeeKey\",e.display_name AS \"employeeName\",e.kind,e.amount,e.event_date AS date,"
        + "e.identity_status AS \"identityStatus\",e.source_column AS \"sourceColumn\",e.source_heading AS \"sourceHeading\","
        + "r.id AS \"productionRowId\",trim(r.sheet_name) AS sheet,r.source_row AS \"sourceRow\","
        + "r.raw_values->'cells'->>'B' AS venue,r.raw_values->'cells'->>'C' AS client,b.\"balanceAfter\" "
        + "FROM finance_workbook_employee_facts e JOIN finance_migration_rows r ON r.id=e.row_id LEFT JOIN balances b ON b.id=e.id WHERE "+where
        + " ORDER BY e.event_date NULLS LAST,r.sheet_name,r.source_row,CASE WHEN e.kind='EARNING' THEN 0 ELSE 1 END,e.source_column,e.id LIMIT ? OFFSET ?",append(new Object[]{batch},append(args,size,page*size)));
    return Map.of("available",true,"items",items,"total",total,"page",page,"size",size);
  }

  @Transactional
  public Map<String,Object> detail(UUID id) {
    UUID batch = latestBatch();
    if (batch == null) throw ApiException.notFound("WORKBOOK_NOT_FOUND","No workbook is loaded.");
    ensure(batch);
    var rows = jdbc.queryForList("SELECT e.id,e.employee_key AS \"employeeKey\",e.display_name AS \"employeeName\",e.kind,e.amount,e.event_date AS date,"
        + "e.identity_status AS \"identityStatus\",e.source_column AS \"sourceColumn\",e.source_heading AS \"sourceHeading\","
        + "r.id AS \"productionRowId\",trim(r.sheet_name) AS sheet,r.source_row AS \"sourceRow\",r.source_range AS \"sourceRange\","
        + "r.raw_values::text AS raw_json,b.workbook_sha256 AS \"workbookSha256\","
        + "r.raw_values->'cells'->>'B' AS venue,r.raw_values->'cells'->>'C' AS client "
        + "FROM finance_workbook_employee_facts e JOIN finance_migration_rows r ON r.id=e.row_id JOIN finance_migration_batches b ON b.id=e.batch_id "
        + "WHERE e.id=? AND e.batch_id=?",id,batch);
    if (rows.isEmpty()) throw ApiException.notFound("EMPLOYEE_FACT_NOT_FOUND","Workbook staff event not found.");
    var result = new LinkedHashMap<String,Object>(rows.getFirst());
    try { result.put("raw",json.readValue((String)result.remove("raw_json"),new TypeReference<Map<String,Object>>(){})); }
    catch (Exception error) { throw new IllegalStateException("Stored workbook row cannot be read",error); }
    String col = (String) result.get("sourceColumn");
    int index = 0;
    for (char c : col.toCharArray()) index = index*26+(c-'A'+1);
    String slot = ("PAYMENT_"+(index-1));
    result.put("linkedOwnerEvidence", "PAYMENT".equals(result.get("kind")) ? jdbc.queryForList(
        "SELECT owner.id AS \"movementId\",owner.account_code AS \"accountCode\",owner_row.sheet_name AS sheet,"
        + "owner.source_range AS \"sourceCell\",l.allocated_amount AS amount,l.confidence "
        + "FROM finance_migration_facts d JOIN finance_migration_links l ON l.domain_fact_id=d.id "
        + "JOIN finance_migration_facts owner ON owner.id=l.owner_fact_id JOIN finance_migration_rows owner_row ON owner_row.id=owner.row_id "
        + "WHERE d.row_id=? AND d.slot=?",result.get("productionRowId"),slot) : List.of());
    return result;
  }

  public Map<String,Object> salaryEvidence(int page, int size) {
    if (page < 0 || page > 100_000 || size < 1 || size > 100) throw ApiException.badRequest("INVALID_PAGE","Choose a page size up to 100.");
    UUID batch = latestBatch();
    if (batch == null) return Map.of("available",false,"items",List.of(),"total",0,"page",page,"size",size);
    String where = "batch_id=? AND trim(sheet_name) IN ('Jan 26','JUL+DEC','Jul-Dec') "
        + "AND lower(trim(coalesce(raw_values->'cells'->>'C','')))='month' "
        + "AND raw_values->'cells'->>'I' ~ '^[0-9]+(\\.[0-9]+)?$'";
    long total = jdbc.queryForObject("SELECT count(*) FROM finance_migration_rows WHERE "+where,Long.class,batch);
    var items = jdbc.queryForList("SELECT id AS \"productionRowId\",trim(sheet_name) AS sheet,source_row AS \"sourceRow\","
        + "raw_values->'cells'->>'A' AS date,raw_values->'cells'->>'B' AS name,"
        + "(raw_values->'cells'->>'I')::numeric AS amount "
        + "FROM finance_migration_rows WHERE "+where+" ORDER BY sheet_name,source_row LIMIT ? OFFSET ?",batch,size,page*size);
    return Map.of("available",true,"items",items,"total",total,"page",page,"size",size);
  }

  private void ensure(UUID batch) {
    jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","finance-staff-"+batch);
    Long count = jdbc.queryForObject("SELECT count(*) FROM finance_workbook_employee_facts WHERE batch_id=?",Long.class,batch);
    if (count != null && count > 0) return;
    for (String sheet : List.of("Jan 26","JUL+DEC","Jul-Dec","jully dec P3 Led")) {
      List<Pair> pairs = sheet.equals("jully dec P3 Led") ? P3 : STANDARD;
      for (Pair pair : pairs) {
        // JUL+DEC uses Himanshu in V/W; Jul-Dec and Jan 26 use Anand.
        Pair actual = sheet.equals("JUL+DEC") && pair.key.equals("ANAND")
            ? new Pair("HIMANSHU","Himanshu",pair.payment,pair.earning,false) : pair;
        add(batch,sheet,actual,actual.payment,"PAYMENT");
        add(batch,sheet,actual,actual.earning,"EARNING");
      }
    }
  }

  private void add(UUID batch, String sheet, Pair pair, String col, String kind) {
    if (col.isEmpty()) return;
    String source = "r.raw_values->'cells'->>?";
    String sql = "INSERT INTO finance_workbook_employee_facts(batch_id,row_id,employee_key,display_name,kind,source_column,source_heading,amount,event_date,identity_status) "
        + "SELECT r.batch_id,r.id,?,?,?, ?, h.raw_values->'cells'->>?, ("+source+")::numeric,"
        + "CASE WHEN r.raw_values->'cells'->>'A' ~ '^\\d{4}-\\d{2}-\\d{2}$' THEN (r.raw_values->'cells'->>'A')::date ELSE NULL END,? "
        + "FROM finance_migration_rows r LEFT JOIN finance_migration_rows h ON h.batch_id=r.batch_id AND h.sheet_name=r.sheet_name AND h.source_row=1 "
        + "WHERE r.batch_id=? AND trim(r.sheet_name)=? AND r.source_row>=5 "
        + "AND "+source+" ~ '^-?[0-9]+(\\.[0-9]+)?$' AND ("+source+")::numeric>0 "
        + "ON CONFLICT (batch_id,row_id,source_column) DO NOTHING";
    jdbc.update(sql,pair.key,pair.name,kind,col,col,col,pair.disputed ? "IDENTITY_REVIEW":"FORMULA_PAIRED",batch,sheet,col,col);
  }

  private List<Map<String,Object>> parity(UUID batch) {
    var result = new ArrayList<Map<String,Object>>();
    for (String sheet : List.of("Jan 26","JUL+DEC","Jul-Dec","jully dec P3 Led")) {
      for (Pair pair : sheet.equals("jully dec P3 Led") ? P3 : STANDARD) {
        for (String col : List.of(pair.payment,pair.earning)) {
          if (col.isEmpty()) continue;
          BigDecimal source = jdbc.queryForObject("SELECT nullif(raw_values->'cells'->>?, '')::numeric FROM finance_migration_rows WHERE batch_id=? AND trim(sheet_name)=? AND source_row=3",BigDecimal.class,col,batch,sheet);
          BigDecimal projected = jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM finance_workbook_employee_facts e JOIN finance_migration_rows r ON r.id=e.row_id WHERE e.batch_id=? AND trim(r.sheet_name)=? AND e.source_column=?",BigDecimal.class,batch,sheet,col);
          if (source == null) continue;
          result.add(Map.of("sheet",sheet,"column",col,"employee",pair.name,"kind",col.equals(pair.payment)?"PAYMENT":"EARNING",
              "workbook",source,"projection",projected,"difference",projected.subtract(source),"status",projected.compareTo(source)==0?"MATCH":"MISMATCH"));
        }
      }
    }
    return result;
  }

  private Object[] append(Object[] args, Object... tail) {
    Object[] combined = java.util.Arrays.copyOf(args,args.length+tail.length);
    System.arraycopy(tail,0,combined,args.length,tail.length);
    return combined;
  }

  private String date(String text) {
    if (text == null || text.isBlank()) return null;
    try { return LocalDate.parse(text).toString(); }
    catch (DateTimeParseException error) { throw ApiException.badRequest("INVALID_PERIOD","Choose a valid ISO date."); }
  }
}
