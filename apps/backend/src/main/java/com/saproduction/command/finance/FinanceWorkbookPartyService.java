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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only party ledger sourced solely from the wide workbook sheet named "शीट4 ". */
@Service
@ConditionalOnProperty(name = "app.mode", havingValue = "demo")
public class FinanceWorkbookPartyService {
  private static final String SHEET = "शीट4 "; // The unsuffixed "शीट4" is accessories, not parties.
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  private record SourceRow(UUID id, int number, Map<String,Object> cells, Map<String,Object> formulas) {
    Object get(String col) { return cells.get(col); }
  }
  private record Entry(UUID id, UUID block, UUID row, int sourceRow, String partyKey, LocalDate date,
                       String rawDate, String venue, String service, String rate, BigDecimal amount, BigDecimal payment) {
    String signature() { return Objects.toString(rawDate,"")+"|"+normalize(venue)+"|"+normalize(service)+"|"+normalize(rate)
        +"|"+amount.stripTrailingZeros()+"|"+payment.stripTrailingZeros(); }
  }
  private record Block(UUID id, int index, String key, String name, String start, String end, List<Entry> entries) {}
  private record Fact(UUID id, String event, BigDecimal amount, LocalDate date, String name, String venue, UUID rowId) {}

  public FinanceWorkbookPartyService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

  private UUID latestBatch() {
    var rows=jdbc.queryForList("SELECT id FROM finance_migration_batches ORDER BY created_at DESC,id DESC LIMIT 1");
    return rows.isEmpty()?null:(UUID)rows.getFirst().get("id");
  }

  @Transactional
  public void prepare(UUID batch) { ensure(batch); }

  @Transactional
  public Map<String,Object> summary() {
    UUID batch=latestBatch();
    if (batch==null) return Map.of("available",false,"partyBlocks",0,"parties",0);
    ensure(batch);
    var totals=jdbc.queryForMap("SELECT count(*) AS blocks,count(DISTINCT party_key) AS parties,"
        +"coalesce(sum(projection_amount) FILTER (WHERE disposition='ACTIVE_SOURCE'),0) AS business,"
        +"coalesce(sum(projection_payment) FILTER (WHERE disposition='ACTIVE_SOURCE'),0) AS received,"
        +"coalesce(sum(projection_balance) FILTER (WHERE disposition='ACTIVE_SOURCE'),0) AS outstanding,"
        +"count(*) FILTER (WHERE disposition='DUPLICATE_SNAPSHOT') AS duplicate_blocks,"
        +"count(*) FILTER (WHERE parity_status='MISMATCH') AS parity_mismatches "
        +"FROM finance_workbook_party_blocks WHERE batch_id=?",batch);
    var status=jdbc.queryForMap("SELECT count(*) FILTER (WHERE outstanding>0) AS outstanding,"
        +"count(*) FILTER (WHERE outstanding=0) AS settled,count(*) FILTER (WHERE outstanding<0) AS negative "
        +"FROM (SELECT party_key,sum(projection_balance) AS outstanding FROM finance_workbook_party_blocks "
        +"WHERE batch_id=? AND disposition='ACTIVE_SOURCE' GROUP BY party_key) grouped",batch);
    var links=jdbc.queryForMap("SELECT count(*) FILTER (WHERE relation_type LIKE 'PRODUCTION_%') AS production,"
        +"count(*) FILTER (WHERE relation_type='OWNER_RECEIPT') AS owner FROM finance_workbook_party_links WHERE batch_id=?",batch);
    var evidence=jdbc.queryForMap("SELECT count(*) FILTER (WHERE b.disposition='ACTIVE_SOURCE') AS active_entries,"
        +"count(*) FILTER (WHERE e.duplicate_of_entry_id IS NOT NULL) AS duplicate_representations,"
        +"count(*) FILTER (WHERE b.disposition='ACTIVE_SOURCE' AND e.payment>0 AND lower(coalesce(e.venue,'')) NOT LIKE '%discount%' AND NOT EXISTS "
        +"(SELECT 1 FROM finance_workbook_party_links l WHERE l.entry_id=e.id AND l.relation_type='OWNER_RECEIPT')) AS unlinked_payments "
        +"FROM finance_workbook_party_entries e JOIN finance_workbook_party_blocks b ON b.id=e.block_id WHERE e.batch_id=?",batch);
    long threeWay=jdbc.queryForObject("SELECT count(*) FROM finance_workbook_party_entries e WHERE e.batch_id=? "
        +"AND EXISTS(SELECT 1 FROM finance_workbook_party_links l WHERE l.entry_id=e.id AND l.relation_type='PRODUCTION_RECEIPT') "
        +"AND EXISTS(SELECT 1 FROM finance_workbook_party_links l WHERE l.entry_id=e.id AND l.relation_type='OWNER_RECEIPT')",Long.class,batch);
    var result=new LinkedHashMap<String,Object>();
    result.put("available",true); result.put("workbookSha256",jdbc.queryForObject("SELECT workbook_sha256 FROM finance_migration_batches WHERE id=?",String.class,batch));
    result.put("partyBlocks",totals.get("blocks")); result.put("parties",totals.get("parties"));
    result.put("business",totals.get("business")); result.put("received",totals.get("received")); result.put("outstanding",totals.get("outstanding"));
    result.put("duplicateBlocks",totals.get("duplicate_blocks")); result.put("parityMismatches",totals.get("parity_mismatches"));
    result.put("outstandingParties",status.get("outstanding")); result.put("settledParties",status.get("settled")); result.put("negativeParties",status.get("negative"));
    result.put("productionLinks",links.get("production")); result.put("ownerLinks",links.get("owner")); result.put("threeWayReceipts",threeWay);
    result.put("activeEntries",evidence.get("active_entries"));result.put("duplicateRepresentations",evidence.get("duplicate_representations"));
    result.put("unlinkedPayments",evidence.get("unlinked_payments"));
    result.put("discountSettlement",jdbc.queryForObject("SELECT coalesce(sum(e.payment),0) FROM finance_workbook_party_entries e "
        +"JOIN finance_workbook_party_blocks b ON b.id=e.block_id WHERE e.batch_id=? AND b.disposition='ACTIVE_SOURCE' "
        +"AND lower(coalesce(e.venue,'')) LIKE '%discount%'",BigDecimal.class,batch));
    return result;
  }

  @Transactional
  public Map<String,Object> parties(String status,String link,String search,int page,int size) {
    page(page,size); UUID batch=latestBatch();
    if (batch==null) return Map.of("available",false,"items",List.of(),"total",0,"page",page,"size",size);
    ensure(batch);
    if (!Set.of("","OUTSTANDING","SETTLED","NEGATIVE").contains(status)) throw ApiException.badRequest("INVALID_STATUS","Choose a party balance filter.");
    if (!Set.of("","PRODUCTION","OWNER","FULLY_LINKED","LEGACY").contains(link)) throw ApiException.badRequest("INVALID_LINK","Choose a link filter.");
    if (search.length()>100) throw ApiException.badRequest("INVALID_SEARCH","Search is too long.");
    String q="%"+escape(search.toLowerCase(Locale.ROOT))+"%";
    String base="WITH grouped AS (SELECT party_key AS key,(array_agg(raw_name ORDER BY block_index))[1] AS name,"
        +"sum(projection_amount) AS business,sum(projection_payment) AS received,sum(projection_balance) AS outstanding,"
        +"count(*) AS blocks,coalesce(sum((SELECT count(*) FROM finance_workbook_party_entries e WHERE e.block_id=b.id)),0) AS entries,"
        +"max((SELECT max(event_date) FROM finance_workbook_party_entries e WHERE e.block_id=b.id)) AS \"lastActivity\","
        +"coalesce(sum((SELECT count(*) FROM finance_workbook_party_links l JOIN finance_workbook_party_entries e ON e.id=l.entry_id WHERE e.block_id=b.id AND l.relation_type LIKE 'PRODUCTION_%')),0) AS \"productionLinks\","
        +"coalesce(sum((SELECT count(*) FROM finance_workbook_party_links l JOIN finance_workbook_party_entries e ON e.id=l.entry_id WHERE e.block_id=b.id AND l.relation_type='OWNER_RECEIPT')),0) AS \"ownerLinks\" "
        +"FROM finance_workbook_party_blocks b WHERE batch_id=? AND disposition='ACTIVE_SOURCE' GROUP BY party_key) ";
    String where="WHERE (?='' OR (?='OUTSTANDING' AND outstanding>0) OR (?='SETTLED' AND outstanding=0) OR (?='NEGATIVE' AND outstanding<0)) "
        +"AND (?='' OR (?='PRODUCTION' AND \"productionLinks\">0) OR (?='OWNER' AND \"ownerLinks\">0) "
        +"OR (?='FULLY_LINKED' AND \"productionLinks\">0 AND \"ownerLinks\">0) OR (?='LEGACY' AND \"productionLinks\"=0 AND \"ownerLinks\"=0)) "
        +"AND (?='' OR lower(name) LIKE ? ESCAPE '\\' OR EXISTS (SELECT 1 FROM finance_workbook_party_blocks b "
        +"JOIN finance_workbook_party_entries e ON e.block_id=b.id WHERE b.party_key=grouped.key AND b.batch_id=? "
        +"AND lower(concat_ws(' ',e.venue,e.service,e.amount::text,e.payment::text,e.raw_date)) LIKE ? ESCAPE '\\'))";
    Object[] args={batch,status,status,status,status,link,link,link,link,link,search,q,batch,q};
    long total=jdbc.queryForObject(base+"SELECT count(*) FROM grouped "+where,Long.class,args);
    var items=jdbc.queryForList(base+"SELECT * FROM grouped "+where+" ORDER BY name LIMIT ? OFFSET ?",append(args,size,page*size));
    return Map.of("available",true,"items",items,"total",total,"page",page,"size",size);
  }

  @Transactional
  public Map<String,Object> party(String key) {
    UUID batch=latestBatch();
    if (batch==null) throw ApiException.notFound("WORKBOOK_NOT_FOUND","No party workbook is loaded.");
    ensure(batch);
    var blocks=jdbc.queryForList("SELECT id,block_index AS \"blockIndex\",raw_name AS name,source_range AS \"sourceRange\","
        +"start_column AS \"startColumn\",end_column AS \"endColumn\",role,disposition,duplicate_of_block_id AS \"duplicateOfBlockId\","
        +"layout::text AS layout_json,workbook_amount AS \"workbookAmount\",workbook_payment AS \"workbookPayment\",workbook_balance AS \"workbookBalance\","
        +"projection_amount AS \"projectionAmount\",projection_payment AS \"projectionPayment\",projection_balance AS \"projectionBalance\","
        +"parity_status AS \"parityStatus\" FROM finance_workbook_party_blocks WHERE batch_id=? AND party_key=? ORDER BY block_index",batch,key);
    if (blocks.isEmpty()) throw ApiException.notFound("PARTY_NOT_FOUND","Workbook party not found.");
    for(var source:blocks)source.put("layout",raw((String)source.remove("layout_json")));
    BigDecimal business=BigDecimal.ZERO,received=BigDecimal.ZERO;
    for (var block:blocks) if ("ACTIVE_SOURCE".equals(block.get("disposition"))) {
      business=business.add((BigDecimal)block.get("projectionAmount"));received=received.add((BigDecimal)block.get("projectionPayment"));
    }
    var result=new LinkedHashMap<String,Object>();
    result.put("key",key);result.put("name",blocks.getFirst().get("name"));result.put("role","BUSINESS_PARTY");
    result.put("business",business);result.put("received",received);result.put("outstanding",business.subtract(received));result.put("blocks",blocks);
    return result;
  }

  @Transactional
  public Map<String,Object> entries(String key,String block,String from,String to,String search,int page,int size) {
    page(page,size);UUID batch=latestBatch();
    if (batch==null) return Map.of("available",false,"items",List.of(),"total",0,"page",page,"size",size);
    ensure(batch);String fromDate=date(from),toDate=date(to);
    if (fromDate!=null && toDate!=null && fromDate.compareTo(toDate)>0) throw ApiException.badRequest("INVALID_PERIOD","Start date must precede end date.");
    if (search.length()>100) throw ApiException.badRequest("INVALID_SEARCH","Search is too long.");
    String where="e.batch_id=? AND b.party_key=? AND (?='' OR b.id::text=?) "
        +"AND (CAST(? AS date) IS NULL OR e.event_date>=CAST(? AS date)) AND (CAST(? AS date) IS NULL OR e.event_date<=CAST(? AS date)) "
        +"AND (?='' OR lower(concat_ws(' ',e.venue,e.service,e.raw_date,e.amount::text,e.payment::text)) LIKE ? ESCAPE '\\')";
    String q="%"+escape(search.toLowerCase(Locale.ROOT))+"%";
    Object[] args={batch,key,block,block,fromDate,fromDate,toDate,toDate,search,q};
    long total=jdbc.queryForObject("SELECT count(*) FROM finance_workbook_party_entries e JOIN finance_workbook_party_blocks b ON b.id=e.block_id WHERE "+where,Long.class,args);
    var items=jdbc.queryForList("SELECT e.id,e.event_date AS date,e.raw_date AS \"rawDate\",e.venue,e.service,e.rate,e.amount,e.payment,"
        +"(SELECT sum(prior.amount-prior.payment) FROM finance_workbook_party_entries prior WHERE prior.block_id=e.block_id AND prior.source_row<=e.source_row) AS \"balanceAfter\","
        +"e.source_row AS \"sourceRow\",e.row_id AS \"rowId\",b.id AS \"blockId\",b.block_index AS \"blockIndex\","
        +"b.raw_name AS \"partyName\",b.disposition,b.start_column AS \"startColumn\",e.duplicate_of_entry_id AS \"duplicateOfEntryId\","
        +"(SELECT count(*) FROM finance_workbook_party_links l WHERE l.entry_id=e.id AND l.relation_type LIKE 'PRODUCTION_%') AS \"productionLinks\","
        +"(SELECT count(*) FROM finance_workbook_party_links l WHERE l.entry_id=e.id AND l.relation_type='OWNER_RECEIPT') AS \"ownerLinks\" "
        +"FROM finance_workbook_party_entries e JOIN finance_workbook_party_blocks b ON b.id=e.block_id WHERE "+where
        +" ORDER BY e.event_date NULLS LAST,b.block_index,e.source_row LIMIT ? OFFSET ?",append(args,size,page*size));
    return Map.of("available",true,"items",items,"total",total,"page",page,"size",size);
  }

  @Transactional
  public Map<String,Object> entry(UUID id) {
    UUID batch=latestBatch();if (batch==null) throw ApiException.notFound("WORKBOOK_NOT_FOUND","No party workbook is loaded.");
    ensure(batch);
    var rows=jdbc.queryForList("SELECT e.id,e.event_date AS date,e.raw_date AS \"rawDate\",e.venue,e.service,e.rate,e.amount,e.payment,"
        +"(SELECT sum(prior.amount-prior.payment) FROM finance_workbook_party_entries prior WHERE prior.block_id=e.block_id AND prior.source_row<=e.source_row) AS \"balanceAfter\","
        +"e.source_row AS \"sourceRow\",e.row_id AS \"rowId\",b.id AS \"blockId\",b.block_index AS \"blockIndex\","
        +"b.raw_name AS \"partyName\",b.party_key AS \"partyKey\",b.source_range AS \"blockRange\",b.disposition,"
        +"b.layout::text AS layout_json,e.duplicate_of_entry_id AS \"duplicateOfEntryId\",r.raw_values::text AS raw_json,"
        +"x.workbook_sha256 AS \"workbookSha256\" FROM finance_workbook_party_entries e "
        +"JOIN finance_workbook_party_blocks b ON b.id=e.block_id JOIN finance_migration_rows r ON r.id=e.row_id "
        +"JOIN finance_migration_batches x ON x.id=e.batch_id WHERE e.id=? AND e.batch_id=?",id,batch);
    if (rows.isEmpty()) throw ApiException.notFound("PARTY_ENTRY_NOT_FOUND","Party source entry not found.");
    var result=new LinkedHashMap<String,Object>(rows.getFirst());
    result.put("raw",raw((String)result.remove("raw_json")));
    result.put("layout",raw((String)result.remove("layout_json")));
    var links=jdbc.queryForList("SELECT l.relation_type AS \"relationType\",l.allocated_amount AS amount,l.confidence,"
        +"f.id AS \"factId\",f.row_id AS \"linkedRowId\",f.account_code AS \"accountCode\","
        +"r.sheet_name AS sheet,r.source_range AS \"sourceRange\",f.source_range AS \"sourceCell\" "
        +"FROM finance_workbook_party_links l JOIN finance_migration_facts f ON f.id=l.fact_id "
        +"JOIN finance_migration_rows r ON r.id=f.row_id WHERE l.entry_id=? ORDER BY l.relation_type",id);
    result.put("links",links);
    result.put("settlementKind",normalize(result.get("venue")).contains("discount")?"DISCOUNT":"PAYMENT");
    if(((BigDecimal)result.get("payment")).signum()>0 && !"DISCOUNT".equals(result.get("settlementKind"))) {
      result.put("historicalReceiptId",Objects.requireNonNullElse(result.get("duplicateOfEntryId"),id));
      result.put("receiptEvidenceCount",1+links.stream().filter(l->!"PRODUCTION_CHARGE".equals(l.get("relationType"))).count());
      result.put("receiptContribution",result.get("duplicateOfEntryId")==null?result.get("payment"):BigDecimal.ZERO);
    }
    return result;
  }

  @Transactional
  public Map<String,Object> blocks(int page,int size) {
    page(page,size);UUID batch=latestBatch();if(batch==null)return Map.of("available",false,"items",List.of(),"total",0,"page",page,"size",size);
    ensure(batch);long total=jdbc.queryForObject("SELECT count(*) FROM finance_workbook_party_blocks WHERE batch_id=?",Long.class,batch);
    var items=jdbc.queryForList("SELECT id,block_index AS \"blockIndex\",raw_name AS name,party_key AS \"partyKey\",source_range AS \"sourceRange\","
        +"disposition,workbook_amount AS \"workbookAmount\",workbook_payment AS \"workbookPayment\",workbook_balance AS \"workbookBalance\","
        +"projection_amount AS \"projectionAmount\",projection_payment AS \"projectionPayment\",projection_balance AS \"projectionBalance\","
        +"parity_status AS \"parityStatus\" FROM finance_workbook_party_blocks WHERE batch_id=? ORDER BY block_index LIMIT ? OFFSET ?",batch,size,page*size);
    return Map.of("available",true,"items",items,"total",total,"page",page,"size",size);
  }

  private void ensure(UUID batch) {
    jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "finance-party-"+batch);
    Integer present=jdbc.queryForObject("SELECT count(*) FROM finance_workbook_party_blocks WHERE batch_id=?",Integer.class,batch);
    if (present!=null && present>0) return;
    var sources=jdbc.queryForList("SELECT id,source_row,raw_values::text AS raw_json FROM finance_migration_rows "
        +"WHERE batch_id=? AND sheet_name=? ORDER BY source_row",batch,SHEET);
    if (sources.isEmpty()) return;
    List<SourceRow> rows=new ArrayList<>();
    for (var source:sources) {
      Map<String,Object> raw=raw((String)source.get("raw_json"));
      rows.add(new SourceRow((UUID)source.get("id"),((Number)source.get("source_row")).intValue(),
          object(raw.get("cells")),object(raw.get("formulas"))));
    }
    SourceRow titles=rows.stream().filter(r->r.number()==1).findFirst().orElseThrow();
    SourceRow headers=rows.stream().filter(r->r.number()==2).findFirst().orElseThrow();
    SourceRow controls=rows.stream().filter(r->r.number()==3).findFirst().orElseThrow();
    List<String> starts=titles.cells().entrySet().stream().filter(e->nonblank(e.getValue()))
        .map(Map.Entry::getKey).filter(c->c.matches("[A-Z]{1,3}"))
        .sorted(Comparator.comparingInt(FinanceWorkbookPartyService::columnNumber)).toList();
    List<Block> blocks=new ArrayList<>();
    for (int index=0;index<starts.size();index++) {
      String start=starts.get(index);
      int first=columnNumber(start),last=index+1<starts.size()?columnNumber(starts.get(index+1))-1:171;
      String end=column(last),name=String.valueOf(titles.get(start)).trim(),key=identity(name);
      Map<String,String> fields=new LinkedHashMap<>();
      for(int c=first;c<=last;c++) {
        String col=column(c),header=normalize(headers.get(col));
        if(header.equals("date"))fields.put("date",col);
        else if(header.equals("venue"))fields.put("venue",col);
        else if(header.contains("led wall") || header.equals("service"))fields.put("service",col);
        else if(header.equals("rate"))fields.put("rate",col);
        else if(header.equals("amount"))fields.put("amount",col);
        else if(header.equals("payment"))fields.put("payment",col);
        else if(header.equals("balance"))fields.put("balance",col);
      }
      if(!fields.keySet().containsAll(Set.of("date","venue","service","amount","payment","balance")))
        throw new IllegalStateException("Unrecognized party workbook block at "+start);
      String amountCol=fields.get("amount"),paymentCol=fields.get("payment"),balanceCol=fields.get("balance");
      BigDecimal amount=BigDecimal.ZERO,payment=BigDecimal.ZERO;
      UUID blockId=UUID.randomUUID();List<Entry> entries=new ArrayList<>();
      for(SourceRow row:rows) {
        if(row.number()<4)continue;
        BigDecimal a=money(row.get(amountCol)),p=money(row.get(paymentCol));
        amount=amount.add(a);payment=payment.add(p);
        if(a.signum()==0 && p.signum()==0)continue;
        String rawDate=string(row.get(fields.get("date")));
        Entry entry=new Entry(UUID.randomUUID(),blockId,row.id(),row.number(),key,parseDate(rawDate),rawDate,
            string(row.get(fields.get("venue"))),string(row.get(fields.get("service"))),
            string(row.get(fields.get("rate"))),a,p);
        entries.add(entry);
      }
      BigDecimal workbookAmount=moneyOrNull(controls.get(amountCol));
      BigDecimal workbookPayment=moneyOrNull(controls.get(paymentCol));
      BigDecimal workbookBalance=moneyOrNull(controls.get(balanceCol));
      BigDecimal balance=amount.subtract(payment);
      String parity=workbookAmount==null || workbookPayment==null || workbookBalance==null?"NO_SOURCE_CONTROL":
          workbookAmount.compareTo(amount)==0 && workbookPayment.compareTo(payment)==0 && workbookBalance.compareTo(balance)==0?"MATCH":"MISMATCH";
      String sourceRange=SHEET+"!"+start+"1:"+end+rows.getLast().number();
      jdbc.update("INSERT INTO finance_workbook_party_blocks(id,batch_id,sheet_name,block_index,start_column,end_column,source_range,party_key,raw_name,role,disposition,layout,workbook_amount,workbook_payment,workbook_balance,projection_amount,projection_payment,projection_balance,parity_status) "
          +"VALUES (?,?,?,?,?,?,?,?,?,'BUSINESS_PARTY','ACTIVE_SOURCE',?::jsonb,?,?,?,?,?,?,?)",
          blockId,batch,SHEET,index+1,start,end,sourceRange,key,name,
          encode(Map.of("columns",fields,"controlCells",Map.of(amountCol,Objects.toString(controls.get(amountCol),""),paymentCol,Objects.toString(controls.get(paymentCol),""),balanceCol,Objects.toString(controls.get(balanceCol),"")),
              "controlFormulas",Map.of(amountCol,Objects.toString(controls.formulas().get(amountCol),""),paymentCol,Objects.toString(controls.formulas().get(paymentCol),""),balanceCol,Objects.toString(controls.formulas().get(balanceCol),"")))),
          workbookAmount,workbookPayment,workbookBalance,amount,payment,balance,parity);
      for(Entry entry:entries)jdbc.update("INSERT INTO finance_workbook_party_entries(id,batch_id,block_id,row_id,source_row,event_date,raw_date,venue,service,rate,amount,payment) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
          entry.id(),batch,blockId,entry.row(),entry.sourceRow(),entry.date(),entry.rawDate(),entry.venue(),entry.service(),entry.rate(),entry.amount(),entry.payment());
      blocks.add(new Block(blockId,index+1,key,name,start,end,entries));
    }
    markDuplicateSnapshots(blocks);
    linkFacts(batch,blocks);
  }

  private void markDuplicateSnapshots(List<Block> blocks) {
    for(Block candidate:blocks) {
      if(candidate.entries().size()<3)continue;
      for(Block larger:blocks) {
        if(candidate==larger || !candidate.key().equals(larger.key()) || candidate.entries().size()>=larger.entries().size())continue;
        Map<String,List<Entry>> signatures=new HashMap<>();
        for(Entry e:larger.entries())signatures.computeIfAbsent(e.signature(),ignored->new ArrayList<>()).add(e);
        Map<UUID,UUID> duplicates=new HashMap<>();
        for(Entry e:candidate.entries()) {
          var matches=signatures.get(e.signature());
          if(matches==null || matches.isEmpty())break;
          duplicates.put(e.id(),matches.removeFirst().id());
        }
        if(duplicates.size()!=candidate.entries().size())continue;
        jdbc.update("UPDATE finance_workbook_party_blocks SET disposition='DUPLICATE_SNAPSHOT',duplicate_of_block_id=? WHERE id=?",larger.id(),candidate.id());
        duplicates.forEach((entry,original)->jdbc.update("UPDATE finance_workbook_party_entries SET duplicate_of_entry_id=? WHERE id=?",original,entry));
        break;
      }
    }
  }

  private void linkFacts(UUID batch,List<Block> blocks) {
    List<Fact> production=new ArrayList<>(),owners=new ArrayList<>();
    for(var row:jdbc.queryForList("SELECT f.id,f.event_type,f.amount,f.event_date,f.raw_name,r.id AS row_id,"
        +"r.raw_values->'cells'->>'B' AS venue,f.source_role,f.direction FROM finance_migration_facts f "
        +"JOIN finance_migration_rows r ON r.id=f.row_id WHERE f.batch_id=? AND f.amount>0 "
        +"AND ((f.source_role='DOMAIN' AND f.event_type IN ('CONTRACTED_REVENUE','CLIENT_RECEIPT')) "
        +"OR (f.source_role='OWNER' AND f.direction='IN'))",batch)) {
      Fact fact=new Fact((UUID)row.get("id"),(String)row.get("event_type"),(BigDecimal)row.get("amount"),
          row.get("event_date")==null?null:((java.sql.Date)row.get("event_date")).toLocalDate(),
          (String)row.get("raw_name"),(String)row.get("venue"),(UUID)row.get("row_id"));
      if("OWNER".equals(row.get("source_role")))owners.add(fact);else production.add(fact);
    }
    Set<UUID> usedProduction=new HashSet<>(),usedOwners=new HashSet<>();
    for(Block block:blocks) {
      for(Entry entry:block.entries()) {
        if(entry.date()==null)continue;
        if(entry.amount().signum()>0) {
          var candidates=production.stream().filter(f->"CONTRACTED_REVENUE".equals(f.event()) && !usedProduction.contains(f.id())
              && exact(entry.amount(),entry.date(),f) && contextMatches(block,entry,f)).toList();
          if(candidates.size()==1){link(batch,entry,candidates.getFirst(),"PRODUCTION_CHARGE","EXACT","amount/date/party-or-venue");usedProduction.add(candidates.getFirst().id());}
        }
        if(entry.payment().signum()<=0 || normalize(entry.venue()).contains("discount"))continue;
        var candidates=production.stream().filter(f->"CLIENT_RECEIPT".equals(f.event()) && !usedProduction.contains(f.id())
            && exact(entry.payment(),entry.date(),f) && contextMatches(block,entry,f)).toList();
        Fact linkedProduction=null;
        if(candidates.size()==1){linkedProduction=candidates.getFirst();link(batch,entry,linkedProduction,"PRODUCTION_RECEIPT","EXACT","amount/date/party-or-venue");usedProduction.add(linkedProduction.id());}
        Fact target=linkedProduction;
        List<Fact> ownerCandidates;
        if(target!=null) {
          Set<UUID> ids=new HashSet<>(jdbc.queryForList("SELECT owner_fact_id FROM finance_migration_links WHERE domain_fact_id=?",UUID.class,target.id()));
          ownerCandidates=owners.stream().filter(f->ids.contains(f.id()) && !usedOwners.contains(f.id()) && exact(entry.payment(),entry.date(),f)).toList();
        } else ownerCandidates=owners.stream().filter(f->!usedOwners.contains(f.id()) && exact(entry.payment(),entry.date(),f)
            && identity(f.name()).equals(block.key())).toList();
        if(ownerCandidates.size()==1){link(batch,entry,ownerCandidates.getFirst(),"OWNER_RECEIPT","EXACT",target==null?"amount/date/party":"proven-production-owner-link");usedOwners.add(ownerCandidates.getFirst().id());}
      }
    }
  }

  private static boolean exact(BigDecimal amount,LocalDate date,Fact fact) {return fact.date()!=null && date.equals(fact.date()) && amount.compareTo(fact.amount())==0;}
  private static boolean contextMatches(Block block,Entry entry,Fact fact) {
    return identity(fact.name()).equals(block.key()) || (!normalize(entry.venue()).isBlank() && normalize(entry.venue()).equals(normalize(fact.venue())));
  }
  private void link(UUID batch,Entry entry,Fact fact,String type,String confidence,String reason) {
    BigDecimal amount="PRODUCTION_CHARGE".equals(type)?entry.amount():entry.payment();
    jdbc.update("INSERT INTO finance_workbook_party_links(batch_id,entry_id,fact_id,relation_type,allocated_amount,confidence,evidence) VALUES (?,?,?,?,?,?,?::jsonb)",
        batch,entry.id(),fact.id(),type,amount,confidence,encode(Map.of("rule",reason,"partySourceRow",entry.sourceRow(),"factId",fact.id().toString())));
  }
  private static void page(int page,int size) {if(page<0 || page>100000 || size<1 || size>100)throw ApiException.badRequest("INVALID_PAGE","Choose a valid page and size up to 100.");}
  private static String date(String value) {if(value==null || value.isBlank())return null;try{return LocalDate.parse(value).toString();}catch(DateTimeParseException ex){throw ApiException.badRequest("INVALID_DATE","Use YYYY-MM-DD dates.");}}
  private static LocalDate parseDate(String value) {try{return value==null?null:LocalDate.parse(value.substring(0,10));}catch(RuntimeException ex){return null;}}
  private static String escape(String value) {return value.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
  private static Object[] append(Object[] values,Object... more) {Object[] all=java.util.Arrays.copyOf(values,values.length+more.length);System.arraycopy(more,0,all,values.length,more.length);return all;}
  private static boolean nonblank(Object value) {return value!=null && !value.toString().isBlank();}
  private static String string(Object value) {return value==null?null:value.toString().trim();}
  private static String normalize(Object value) {return value==null?"":value.toString().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim().replaceAll(" +"," ");}
  private static String identity(String value) {return normalize(value==null?"":value.replaceAll("(?i)\\s*\\(2026(?:\\s+[a-z]+)?\\)\\s*$","")).replaceFirst("(?: ji)$","");}
  private static BigDecimal money(Object value) {BigDecimal result=moneyOrNull(value);return result==null?BigDecimal.ZERO:result;}
  private static BigDecimal moneyOrNull(Object value) {if(value==null || value.toString().isBlank())return null;try{return new BigDecimal(value.toString().replace(",","").replace("₹","").trim());}catch(NumberFormatException ex){return null;}}
  private static int columnNumber(String label) {int n=0;for(char c:label.toCharArray())n=n*26+c-'A'+1;return n;}
  private static String column(int number) {StringBuilder result=new StringBuilder();for(int n=number;n>0;n=(n-1)/26)result.insert(0,(char)('A'+(n-1)%26));return result.toString();}
  @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) {return value instanceof Map<?,?>?(Map<String,Object>)value:Map.of();}
  private Map<String,Object> raw(String value) {try{return json.readValue(value,new TypeReference<>(){});}catch(Exception ex){throw new IllegalStateException("Invalid staged workbook JSON",ex);}}
  private String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException("Cannot encode workbook evidence",ex);}}
}
