package com.saproduction.command.finance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.sql.Date;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Workbook-only evidence. Nothing here posts a normal Finance transaction. */
@Service
public class FinanceMigrationResolver {
  private static final Set<String> PRODUCTION = Set.of("Jan 26", "JUL+DEC", "Jul-Dec", "jully dec P3 Led", "Sound Jul+dec", "All Date 26");
  private static final Set<String> AK = Set.of("G Pay Aakash 26", "Ak-2");
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public FinanceMigrationResolver(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  public void stageRow(UUID batch, UUID rowId, String sheet, int rowNumber, Row row) {
    String name = sheet.trim();
    int count = 0;
    if (name.equals("az 26")) {
      if (rowNumber >= 5) {
        count += ownerLane(batch, rowId, row, 0, 1, 2, 3, "AZ-2", "AZ_CASH");
        count += ownerLane(batch, rowId, row, 5, 6, 7, 8, "AZ-2", "AZ_BANK");
      }
    } else if (name.equals("az-2")) {
      if (rowNumber >= 6) count += ownerLane(batch, rowId, row, 0, 1, 2, 3, "AZ-2", "AZ_CURRENT");
    } else if (AK.contains(name)) {
      if (rowNumber >= 4) {
        for (int start = 0; start + 3 <= row.getLastCellNum(); start += 5)
          count += ownerLane(batch, rowId, row, start, start + 1, start + 2, start + 3, "AK-2", "AK_" + start);
      }
    } else if (PRODUCTION.contains(name)) {
      if (rowNumber >= (name.equals("All Date 26") ? 4 : 5)) {
        int total = name.equals("All Date 26") ? 8 : 4;
        int add = name.equals("All Date 26") ? 9 : 5;
        int receipt = name.equals("All Date 26") ? 10 : 6;
        String party = text(row, 2);
        count += fact(batch, rowId, "CONTRACT", row, 0, 2, total, "CONTRACTED_REVENUE", "NONE", "DOMAIN", null, party);
        // Add is a component of the contract total, not a second contract.
        count += fact(batch, rowId, "ADD_COMPONENT", row, 0, 2, add, "LEGACY_ADJUSTMENT", "NONE", "CONTROL", null, party);
        count += fact(batch, rowId, "RECEIPT", row, 0, 2, receipt, "CLIENT_RECEIPT", "IN", "DOMAIN", null, party);
        if (!name.equals("All Date 26"))
          count += fact(batch, rowId, "EXPENSE_ACCRUAL", row, 0, 2, 8, "PRODUCTION_EXPENSE_ACCRUAL", "NONE", "DOMAIN", null, party);
        Row header = row.getSheet().getRow(0);
        if (header != null && !name.equals("All Date 26")) {
          for (int c=11;c<row.getLastCellNum();c++) {
            String heading = text(header,c);
            if (heading == null) continue;
            String normalized = normalize(heading);
            if (isPaymentHeading(normalized)) {
              String payee = paymentName(normalized);
              boolean employee = pairedEarning(header,c,payee);
              String event = employee ? "EMPLOYEE_PAYMENT" : normalized.equals("extra pay") || normalized.equals("cam+led pay")
                  ? "PRODUCTION_EXPENSE_PAYMENT" : "UNKNOWN";
              count += fact(batch,rowId,"PAYMENT_"+c,row,0,c,c,event,"OUT","DOMAIN",null,payee);
            } else if (c>11 && !Set.of("boy name","extr","extra","akash").contains(normalized)
                && (isPaymentHeading(normalize(text(header,c-1))) || isPaymentHeading(normalize(text(header,c+1))))) {
              count += fact(batch,rowId,"EMPLOYEE_EARNING_"+c,row,0,c,c,"EMPLOYEE_EARNING","NONE","DOMAIN",null,heading);
            }
          }
        }
      }
    } else if (name.equals("GST")) {
      if (rowNumber >= 3 && amount(row, 8) != null) {
        String party = text(row, 2);
        count += fact(batch, rowId, "INVOICE", row, 1, 2, 8, "INVOICE", "NONE", "DOMAIN", null, party);
        count += fact(batch, rowId, "PAYMENT_1", row, 1, 2, 10, "INVOICE_PAYMENT", "IN", "DOMAIN", null, party);
        count += fact(batch, rowId, "PAYMENT_2", row, 1, 2, 11, "INVOICE_PAYMENT", "IN", "DOMAIN", null, party);
        count += fact(batch, rowId, "TDS", row, 1, 2, 9, "TDS", "NONE", "DOMAIN", null, party);
      }
    } else if (name.equals("LED") || name.equals("Sound")) {
      if (rowNumber >= 3) {
        count += fact(batch, rowId, "PURCHASE", row, 0, 1, 5, "EQUIPMENT_PURCHASE", "NONE", "DOMAIN", null, text(row, 1));
        count += fact(batch, rowId, "PAYMENT", row, 0, 1, 6, "EQUIPMENT_PAYMENT", "OUT", "DOMAIN", null, text(row, 1));
      }
    } else if (name.equals("शीट4") || name.equals("शीट4 (2)")) {
      if (rowNumber >= 5) {
        for (int start = 0; start + 6 < row.getLastCellNum(); start += 8) {
          String party = text(row.getSheet().getRow(0), start);
          count += fact(batch, rowId, "PARTY_" + start + "_CHARGE", row, start, start + 1, start + 4, "COUNTERPARTY_CHARGE", "NONE", "DOMAIN", null, party);
          count += fact(batch, rowId, "PARTY_" + start + "_RECEIPT", row, start, start + 1, start + 5, "COUNTERPARTY_RECEIPT", "IN", "DOMAIN", null, party);
        }
      }
    } else if (name.equals("Expence")) {
      if (rowNumber >= 3) {
        String label = text(row, 2);
        String account = label != null && normalize(label).equals("az") ? "AZ-2" : label != null && normalize(label).equals("ak") ? "AK-2" : null;
        count += fact(batch, rowId, "EXPENSE", row, 0, 1, 3, "GENERAL_EXPENSE", "OUT", "DOMAIN", account, text(row, 1));
      }
    } else if (name.equals("Varma ji")) {
      add(batch, rowId, "QUOTATION", "A" + rowNumber, "QUOTATION_ONLY", null, null, null, text(row, 1), "NONE", "CONTROL", null);
      count++;
    }
    if (count == 0) add(batch, rowId, "CONTROL", "A" + rowNumber, "NON_FINANCIAL", null, null, null, null, "NONE", "CONTROL", null);
  }

  private static boolean isPaymentHeading(String heading) {
    return heading.endsWith(" pay") || heading.endsWith(" payment") || heading.endsWith("pay");
  }

  private static String paymentName(String heading) {
    return heading.replaceFirst("(?:\\s*payment|\\s*pay)$", "").trim();
  }

  private static boolean pairedEarning(Row header, int column, String payee) {
    return !payee.isBlank() && (payee.equals(normalize(text(header,column-1))) || payee.equals(normalize(text(header,column+1))));
  }

  private int ownerLane(UUID batch, UUID rowId, Row row, int date, int name, int incoming, int outgoing, String account, String slot) {
    int count = 0;
    String label = text(row, name);
    String normalized = normalize(label);
    String eventIn = Set.of("bal", "balance", "credit", "last balance", "old").contains(normalized) ? "OPENING_BALANCE" : "OWNER_ACCOUNT_INFLOW";
    count += fact(batch, rowId, slot + "_IN", row, date, name, incoming, eventIn, "IN", "OWNER", account, label);
    count += fact(batch, rowId, slot + "_OUT", row, date, name, outgoing, "OWNER_ACCOUNT_OUTFLOW", "OUT", "OWNER", account, label);
    return count;
  }

  private int fact(UUID batch, UUID rowId, String slot, Row row, int dateColumn, int nameColumn, int amountColumn,
                   String event, String direction, String role, String account, String name) {
    BigDecimal amount = amount(row, amountColumn);
    if (amount == null || amount.signum() == 0) return 0;
    LocalDate date = date(row, dateColumn);
    String rawDate = text(row, dateColumn);
    add(batch, rowId, slot, column(amountColumn) + (row.getRowNum() + 1), event, amount.abs(), date, rawDate,
        name == null ? text(row, nameColumn) : name, direction, role, account);
    if (amount.signum() < 0) jdbc.update("UPDATE finance_migration_facts SET classification='REVIEW_REQUIRED',issue_code='NEGATIVE_SOURCE_AMOUNT' WHERE batch_id=? AND row_id=? AND slot=?", batch, rowId, slot);
    return 1;
  }

  private void add(UUID batch, UUID rowId, String slot, String range, String event, BigDecimal amount,
                   LocalDate date, String rawDate, String name, String direction, String role, String account) {
    String classification = direction.equals("NONE") ? (event.equals("NON_FINANCIAL") ? "IGNORED_NON_FINANCIAL" : "NON_CASH")
        : role.equals("OWNER") ? "DISTINCT" : "LEGACY_UNALLOCATED";
    jdbc.update("INSERT INTO finance_migration_facts(batch_id,row_id,slot,source_range,event_type,amount,event_date,raw_date,raw_name,normalized_name,direction,source_role,account_code,classification) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        batch, rowId, slot, range, event, amount, date == null ? null : Date.valueOf(date), rawDate, name, normalize(name), direction, role, account, classification);
  }

  @Transactional
  public void resolve(UUID batch) {
    jdbc.queryForList("SELECT id FROM finance_migration_batches WHERE id=? FOR UPDATE", batch);
    jdbc.update("INSERT INTO finance_migration_aliases(raw_name,normalized_identity,entity_type,confidence,source) SELECT DISTINCT raw_name,normalized_name,CASE WHEN event_type LIKE 'EMPLOYEE_%' THEN 'EMPLOYEE' WHEN event_type IN ('INVOICE','COUNTERPARTY_CHARGE','COUNTERPARTY_RECEIPT') THEN 'COUNTERPARTY' ELSE 'UNCLASSIFIED' END,'EXACT','WORKBOOK_NORMALIZATION' FROM finance_migration_facts WHERE batch_id=? AND raw_name IS NOT NULL AND normalized_name<>'' ON CONFLICT (raw_name,entity_type) DO NOTHING",batch);
    jdbc.update("DELETE FROM finance_migration_links WHERE batch_id=?", batch);
    jdbc.update("DELETE FROM finance_migration_identity_links WHERE batch_id=?",batch);
    jdbc.update("DELETE FROM finance_migration_issues WHERE batch_id=?", batch);
    jdbc.update("UPDATE finance_migration_facts SET classification=CASE WHEN account_code IS NOT NULL THEN 'DISTINCT' ELSE 'LEGACY_UNALLOCATED' END,issue_code=NULL,evidence='{}'::jsonb WHERE batch_id=? AND direction<>'NONE' AND canonical_transaction_id IS NULL AND issue_code IS DISTINCT FROM 'NEGATIVE_SOURCE_AMOUNT'", batch);
    jdbc.update("UPDATE finance_migration_facts SET duplicate_of_fact_id=NULL WHERE batch_id=?",batch);
    jdbc.update("UPDATE finance_migration_facts SET resolved_event_type=NULL WHERE batch_id=?",batch);
    jdbc.update("UPDATE finance_migration_facts SET classification='NON_CASH',issue_code=NULL,evidence='{}'::jsonb WHERE batch_id=? AND direction='NONE' AND issue_code='DATE_AMBIGUITY'",batch);
    applyOverrides(batch);
    List<Fact> all = jdbc.query("SELECT f.id,f.row_id,f.slot,f.event_type,f.amount,f.event_date,f.raw_name,f.normalized_name,f.direction,f.source_role,f.account_code,f.classification,r.sheet_name,r.raw_values->'cells'->>'B' AS venue FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id WHERE f.batch_id=? AND f.amount>0 ORDER BY f.id", (rs, index) ->
        new Fact(rs.getObject("id", UUID.class), rs.getObject("row_id", UUID.class), rs.getString("slot"), rs.getString("event_type"), rs.getBigDecimal("amount"),
            rs.getDate("event_date") == null ? null : rs.getDate("event_date").toLocalDate(), rs.getString("raw_name"), rs.getString("normalized_name"),
            rs.getString("direction"), rs.getString("source_role"), rs.getString("account_code"), rs.getString("classification"),rs.getString("sheet_name"),normalize(rs.getString("venue"))), batch);
    Set<UUID> manualFacts = new HashSet<>(jdbc.query("SELECT f.id FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id JOIN finance_migration_batches b ON b.id=f.batch_id JOIN finance_migration_overrides o ON o.workbook_sha256=b.workbook_sha256 AND o.sheet_name=r.sheet_name AND o.source_row=r.source_row AND o.slot=f.slot WHERE f.batch_id=? AND o.action<>'MAP_ENTITY'",(rs,index) -> rs.getObject(1,UUID.class),batch));
    Set<UUID> duplicateFacts = deduplicateDomains(batch,all,manualFacts);
    Set<UUID> transferFacts = resolveOwnerTransfers(batch,all,manualFacts);
    Map<String,String> aliases = new HashMap<>();
    jdbc.queryForList("SELECT raw_name,normalized_identity FROM finance_migration_aliases ORDER BY reviewed").forEach(a -> aliases.put(normalize((String)a.get("raw_name")), (String)a.get("normalized_identity")));
    List<Fact> owners = all.stream().filter(f -> f.role.equals("OWNER") && !f.event.equals("OPENING_BALANCE") && !f.classification.equals("REVIEW_REQUIRED") && !transferFacts.contains(f.id) && !manualFacts.contains(f.id)).toList();
    List<Fact> domains = all.stream().filter(f -> f.role.equals("DOMAIN") && !f.direction.equals("NONE") && !f.classification.equals("REVIEW_REQUIRED") && f.account == null && !duplicateFacts.contains(f.id) && !manualFacts.contains(f.id)).toList();
    Map<UUID,List<Candidate>> byDomain = new HashMap<>();
    Map<UUID,List<Candidate>> byOwner = new HashMap<>();
    for (Fact d : domains) for (Fact o : owners) {
      Candidate c = candidate(d,o,aliases);
      if (c != null) { byDomain.computeIfAbsent(d.id, x -> new ArrayList<>()).add(c); byOwner.computeIfAbsent(o.id, x -> new ArrayList<>()).add(c); }
    }
    Set<UUID> consumed = new HashSet<>();
    for (Fact d : domains) {
      List<Candidate> choices = byDomain.getOrDefault(d.id,List.of());
      if (choices.size() == 1 && byOwner.getOrDefault(choices.getFirst().owner.id,List.of()).size() == 1) {
        Candidate c = choices.getFirst();
        link(batch,c,consumed);
      } else if (!choices.isEmpty()) {
        String issue = choices.size() > 1 ? "MULTIPLE_ACCOUNT_CANDIDATES" : "DUPLICATE_AMBIGUITY";
        set(d.id,"REVIEW_REQUIRED",issue,Map.of("candidateCount",choices.size(),"candidateOwnerFactIds",choices.stream().map(c -> c.owner.id.toString()).toList()));
      } else {
        set(d.id,"LEGACY_UNALLOCATED",d.date == null ? "DATE_AMBIGUITY" : "ACCOUNT_UNKNOWN",Map.of("reason","No unique exact amount, compatible direction, date and identity in owner ledgers"));
      }
    }
    resolveSplits(batch,domains,owners,byDomain,byOwner,consumed,aliases);
    for (Fact o : owners) if (!consumed.contains(o.id) && byOwner.getOrDefault(o.id,List.of()).isEmpty()) {
      set(o.id,"DISTINCT",o.date == null ? "DATE_AMBIGUITY" : "ENTITY_UNKNOWN",Map.of("reason","Owner ledger proves account movement but no domain identity is proven"));
    }
    jdbc.update("UPDATE finance_migration_facts SET classification='REVIEW_REQUIRED',issue_code='DATE_AMBIGUITY',evidence=jsonb_build_object('reason','The source has no unambiguous single event date') WHERE batch_id=? AND source_role='DOMAIN' AND direction='NONE' AND event_date IS NULL AND amount>0 AND event_type NOT IN ('LEGACY_ADJUSTMENT') AND issue_code IS NULL AND id NOT IN (SELECT f.id FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id JOIN finance_migration_batches b ON b.id=f.batch_id JOIN finance_migration_overrides o ON o.workbook_sha256=b.workbook_sha256 AND o.sheet_name=r.sheet_name AND o.source_row=r.source_row AND o.slot=f.slot WHERE f.batch_id=? AND o.action<>'MAP_ENTITY')",batch,batch);
    applyOverrides(batch);
    jdbc.update("UPDATE finance_migration_rows r SET match_confidence=CASE WHEN EXISTS (SELECT 1 FROM finance_migration_facts f WHERE f.row_id=r.id AND f.classification IN ('REVIEW_REQUIRED','LEGACY_UNALLOCATED')) THEN 'REVIEW_REQUIRED' WHEN EXISTS (SELECT 1 FROM finance_migration_facts f WHERE f.row_id=r.id AND f.classification='EXACT') THEN 'EXACT' WHEN EXISTS (SELECT 1 FROM finance_migration_facts f WHERE f.row_id=r.id AND f.classification='HIGH_CONFIDENCE') THEN 'HIGH_CONFIDENCE' ELSE 'DISTINCT' END WHERE r.batch_id=?", batch);
    jdbc.update("INSERT INTO finance_migration_issues(batch_id,row_id,issue_code,detail) SELECT batch_id,row_id,issue_code,'Migration fact '||slot||' needs review' FROM finance_migration_facts WHERE batch_id=? AND issue_code IS NOT NULL AND classification IN ('REVIEW_REQUIRED','LEGACY_UNALLOCATED','DISTINCT')", batch);
    long issues = jdbc.queryForObject("SELECT count(*) FROM finance_migration_issues WHERE batch_id=?", Long.class,batch);
    jdbc.update("UPDATE finance_migration_batches SET status=? WHERE id=? AND status<>'COMMITTED'", issues == 0 ? "VALIDATED" : "REVIEW_REQUIRED", batch);
  }

  private Set<UUID> resolveOwnerTransfers(UUID batch, List<Fact> all, Set<UUID> manualFacts) {
    List<Fact> az = all.stream().filter(f -> f.role.equals("OWNER") && "AZ-2".equals(f.account) && f.date!=null && !f.event.equals("OPENING_BALANCE") && !manualFacts.contains(f.id)).toList();
    List<Fact> ak = all.stream().filter(f -> f.role.equals("OWNER") && "AK-2".equals(f.account) && f.date!=null && !f.event.equals("OPENING_BALANCE") && !manualFacts.contains(f.id)).toList();
    Map<UUID,List<Fact>> candidatesAz = new HashMap<>();
    Map<UUID,List<Fact>> candidatesAk = new HashMap<>();
    for (Fact a : az) for (Fact k : ak) {
      if (a.date.equals(k.date) && a.amount.compareTo(k.amount)==0 && !a.direction.equals(k.direction)
          && Set.of("akash","aakash").contains(a.name) && Set.of("az","azeem").contains(k.name)) {
        candidatesAz.computeIfAbsent(a.id,ignored -> new ArrayList<>()).add(k);
        candidatesAk.computeIfAbsent(k.id,ignored -> new ArrayList<>()).add(a);
      }
    }
    Set<UUID> linked = new HashSet<>();
    for (Fact a : az) {
      List<Fact> options = candidatesAz.getOrDefault(a.id,List.of());
      if (options.size()!=1 || candidatesAk.getOrDefault(options.getFirst().id,List.of()).size()!=1) continue;
      Fact k = options.getFirst();
      Map<String,Object> evidence = Map.of("rule","OWNER_NAMES_OPPOSITE_ACCOUNTS","amount","EXACT","date","EXACT","direction","OPPOSITE");
      jdbc.update("INSERT INTO finance_migration_identity_links(batch_id,primary_fact_id,duplicate_fact_id,confidence,evidence,relation_type) VALUES(?,?,?,'EXACT',?::jsonb,'OWNER_TRANSFER')",batch,a.id,k.id,serialize(evidence));
      jdbc.update("UPDATE finance_migration_facts SET resolved_event_type='OWNER_TRANSFER',classification='EXACT',issue_code=NULL,evidence=?::jsonb WHERE id IN (?,?)",serialize(evidence),a.id,k.id);
      linked.add(a.id);
      linked.add(k.id);
    }
    return linked;
  }

  private void resolveSplits(UUID batch, List<Fact> domains, List<Fact> owners,
                             Map<UUID,List<Candidate>> byDomain, Map<UUID,List<Candidate>> byOwner,
                             Set<UUID> consumed, Map<String,String> aliases) {
    Map<Fact,List<Fact>> domainProposals = new HashMap<>();
    Map<UUID,Integer> ownerUse = new HashMap<>();
    for (Fact d : domains) {
      if (!byDomain.getOrDefault(d.id,List.of()).isEmpty()) continue;
      List<Fact> possible = owners.stream().filter(o -> !consumed.contains(o.id) && byOwner.getOrDefault(o.id,List.of()).isEmpty()
          && o.amount.compareTo(d.amount)<0 && compatibleIdentity(d,o,aliases)).toList();
      List<Fact> subset = uniqueSubset(possible,d.amount);
      if (subset != null) {
        domainProposals.put(d,subset);
        subset.forEach(o -> ownerUse.merge(o.id,1,Integer::sum));
      }
    }
    Set<UUID> matchedDomain = new HashSet<>();
    for (var proposal : domainProposals.entrySet()) {
      if (proposal.getValue().stream().anyMatch(o -> ownerUse.get(o.id)>1)) continue;
      Fact d = proposal.getKey();
      for (Fact o : proposal.getValue()) {
        splitLink(batch,d,o,o.amount,"ONE_DOMAIN_MULTIPLE_OWNER_PAYMENTS");
        consumed.add(o.id);
      }
      matchedDomain.add(d.id);
    }
    Map<Fact,List<Fact>> ownerProposals = new HashMap<>();
    Map<UUID,Integer> domainUse = new HashMap<>();
    for (Fact o : owners) {
      if (consumed.contains(o.id) || !byOwner.getOrDefault(o.id,List.of()).isEmpty()) continue;
      List<Fact> possible = domains.stream().filter(d -> !matchedDomain.contains(d.id) && byDomain.getOrDefault(d.id,List.of()).isEmpty()
          && d.amount.compareTo(o.amount)<0 && compatibleIdentity(d,o,aliases)).toList();
      List<Fact> subset = uniqueSubset(possible,o.amount);
      if (subset != null) {
        ownerProposals.put(o,subset);
        subset.forEach(d -> domainUse.merge(d.id,1,Integer::sum));
      }
    }
    for (var proposal : ownerProposals.entrySet()) {
      if (proposal.getValue().stream().anyMatch(d -> domainUse.get(d.id)>1)) continue;
      Fact o = proposal.getKey();
      for (Fact d : proposal.getValue()) splitLink(batch,d,o,d.amount,"ONE_OWNER_PAYMENT_MULTIPLE_DOMAIN_ITEMS");
      consumed.add(o.id);
    }
  }

  private boolean compatibleIdentity(Fact domain, Fact owner, Map<String,String> aliases) {
    if (!domain.direction.equals(owner.direction) || domain.date==null || owner.date==null
        || Math.abs(ChronoUnit.DAYS.between(domain.date,owner.date))>1 || domain.name.isBlank() || owner.name.isBlank()) return false;
    return aliases.getOrDefault(domain.name,domain.name).equals(aliases.getOrDefault(owner.name,owner.name));
  }

  private List<Fact> uniqueSubset(List<Fact> candidates, BigDecimal target) {
    if (candidates.size()<2 || candidates.size()>20) return null;
    List<List<Fact>> matches = new ArrayList<>();
    subsetSearch(candidates,target,0,new ArrayList<>(),matches);
    return matches.size()==1 ? matches.getFirst() : null;
  }

  private void subsetSearch(List<Fact> candidates, BigDecimal remaining, int start,
                            List<Fact> chosen, List<List<Fact>> matches) {
    if (matches.size()>1) return;
    if (remaining.signum()==0) {
      if (chosen.size()>=2) matches.add(List.copyOf(chosen));
      return;
    }
    if (chosen.size()>=3) return;
    for (int i=start;i<candidates.size();i++) {
      Fact candidate = candidates.get(i);
      if (candidate.amount.compareTo(remaining)>0) continue;
      chosen.add(candidate);
      subsetSearch(candidates,remaining.subtract(candidate.amount),i+1,chosen,matches);
      chosen.removeLast();
      if (matches.size()>1) return;
    }
  }

  private void splitLink(UUID batch, Fact domain, Fact owner, BigDecimal allocated, String rule) {
    Map<String,Object> evidence = Map.of("rule",rule,"amount","EXACT","dateDistanceDays",Math.abs(ChronoUnit.DAYS.between(domain.date,owner.date)),"identity","NORMALIZED_EXACT","direction","COMPATIBLE");
    jdbc.update("INSERT INTO finance_migration_links(batch_id,domain_fact_id,owner_fact_id,allocated_amount,confidence,evidence) VALUES(?,?,?,?,?,?::jsonb)",batch,domain.id,owner.id,allocated,"HIGH_CONFIDENCE",serialize(evidence));
    set(domain.id,"HIGH_CONFIDENCE",null,evidence);
    set(owner.id,"HIGH_CONFIDENCE",null,evidence);
  }

  private Set<UUID> deduplicateDomains(UUID batch, List<Fact> facts, Set<UUID> manualFacts) {
    Map<String,List<Fact>> groups = new HashMap<>();
    for (Fact f : facts) {
      if (!f.role.equals("DOMAIN") || manualFacts.contains(f.id) || f.date == null || f.name == null || f.name.isBlank() || f.venue.isBlank()
          || !PRODUCTION.contains(f.sheet.trim()) || f.event.equals("LEGACY_ADJUSTMENT")) continue;
      String key = f.event+"|"+f.amount.toPlainString()+"|"+f.date+"|"+f.name+"|"+f.venue+"|"+f.direction;
      groups.computeIfAbsent(key,ignored -> new ArrayList<>()).add(f);
    }
    Set<UUID> duplicates = new HashSet<>();
    for (List<Fact> group : groups.values()) {
      if (group.size() < 2 || group.stream().map(f -> f.sheet).distinct().count() != group.size()) continue;
      group.sort((a,b) -> Integer.compare(sheetRank(a.sheet),sheetRank(b.sheet)));
      Fact primary = group.getFirst();
      for (int i=1;i<group.size();i++) {
        Fact duplicate = group.get(i);
        Map<String,Object> evidence = Map.of("amount","EXACT","date","EXACT","normalizedName","EXACT","venue","EXACT","sourceSheets",List.of(primary.sheet,duplicate.sheet));
        jdbc.update("INSERT INTO finance_migration_identity_links(batch_id,primary_fact_id,duplicate_fact_id,confidence,evidence) VALUES(?,?,?,'EXACT',?::jsonb)",batch,primary.id,duplicate.id,serialize(evidence));
        jdbc.update("UPDATE finance_migration_facts SET duplicate_of_fact_id=?,classification='EXACT',issue_code=NULL,evidence=?::jsonb WHERE id=?",primary.id,serialize(evidence),duplicate.id);
        duplicates.add(duplicate.id);
      }
    }
    return duplicates;
  }

  private static int sheetRank(String sheet) {
    return switch (sheet.trim()) {
      case "Jan 26", "JUL+DEC", "Jul-Dec", "jully dec P3 Led", "Sound Jul+dec" -> 0;
      case "All Date 26" -> 1;
      default -> 2;
    };
  }

  private Candidate candidate(Fact domain, Fact owner, Map<String,String> aliases) {
    if (!domain.direction.equals(owner.direction) || domain.amount.compareTo(owner.amount) != 0 || domain.date == null || owner.date == null) return null;
    long days = Math.abs(ChronoUnit.DAYS.between(domain.date,owner.date));
    if (days > 1 || owner.name == null || owner.name.isBlank()) return null;
    String d = aliases.getOrDefault(domain.name == null ? "" : domain.name,domain.name == null ? "" : domain.name);
    String o = aliases.getOrDefault(owner.name,owner.name);
    boolean sameName = !d.isBlank() && d.equals(o);
    boolean sameVenue = PRODUCTION.contains(domain.sheet.trim()) && !domain.venue.isBlank() && domain.venue.equals(o) && days == 0;
    if (!sameName && !sameVenue) return null;
    return new Candidate(domain,owner,days == 0 && sameName && domain.name.equals(owner.name) ? "EXACT" : "HIGH_CONFIDENCE",sameName ? "NORMALIZED_NAME_EXACT" : "VENUE_EXACT",days);
  }

  private void link(UUID batch, Candidate candidate, Set<UUID> consumed) {
    Fact d = candidate.domain;
    Fact o = candidate.owner;
    Map<String,Object> evidence = Map.of("amount","EXACT","dateDistanceDays",candidate.days,"identity",candidate.identity,"direction","COMPATIBLE","ownerAccount",o.account);
    jdbc.update("INSERT INTO finance_migration_links(batch_id,domain_fact_id,owner_fact_id,allocated_amount,confidence,evidence) VALUES(?,?,?,?,?,?::jsonb)",batch,d.id,o.id,d.amount,candidate.confidence,serialize(evidence));
    set(d.id,candidate.confidence,null,evidence);
    set(o.id,candidate.confidence,null,evidence);
    consumed.add(o.id);
  }

  private void set(UUID id, String classification, String issue, Map<String,?> evidence) {
    jdbc.update("UPDATE finance_migration_facts SET classification=?,issue_code=?,evidence=?::jsonb WHERE id=?",classification,issue,serialize(evidence),id);
  }

  private void applyOverrides(UUID batch) {
    var entries = jdbc.queryForList("SELECT f.id,f.direction,o.action,o.target FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id JOIN finance_migration_batches b ON b.id=f.batch_id JOIN finance_migration_overrides o ON o.workbook_sha256=b.workbook_sha256 AND o.sheet_name=r.sheet_name AND o.source_row=r.source_row AND o.slot=f.slot WHERE f.batch_id=?",batch);
    for (var entry : entries) {
      UUID id = (UUID)entry.get("id");
      String action = (String)entry.get("action");
      String target = (String)entry.get("target");
      switch (action) {
        case "ASSIGN_AZ", "ASSIGN_AK" -> jdbc.update("UPDATE finance_migration_facts SET account_code=?,classification='DISTINCT',issue_code=NULL,evidence=jsonb_build_object('manualOverride',?) WHERE id=?",action.equals("ASSIGN_AZ") ? "AZ-2" : "AK-2",action,id);
        case "MARK_NON_CASH" -> jdbc.update("UPDATE finance_migration_facts SET classification='NON_CASH',issue_code=NULL,evidence=jsonb_build_object('manualOverride',?) WHERE id=?",action,id);
        case "MARK_DISTINCT" -> jdbc.update("UPDATE finance_migration_facts SET classification=CASE WHEN source_role='DOMAIN' AND direction<>'NONE' AND account_code IS NULL THEN 'LEGACY_UNALLOCATED' ELSE 'DISTINCT' END,issue_code=CASE WHEN source_role='DOMAIN' AND direction<>'NONE' AND account_code IS NULL THEN 'ACCOUNT_UNKNOWN' ELSE NULL END,evidence=jsonb_build_object('manualOverride',?) WHERE id=?",action,id);
        case "IGNORE_NON_FINANCIAL" -> jdbc.update("UPDATE finance_migration_facts SET classification='IGNORED_NON_FINANCIAL',issue_code=NULL,evidence=jsonb_build_object('manualOverride',?) WHERE id=?",action,id);
        case "MAP_ENTITY" -> jdbc.update("UPDATE finance_migration_facts SET normalized_name=?,evidence=jsonb_build_object('manualOverride',?) WHERE id=?",normalize(target),action,id);
        case "LINK_EXISTING" -> jdbc.update("UPDATE finance_migration_facts SET canonical_transaction_id=?::uuid,classification='EXACT',issue_code=NULL,evidence=jsonb_build_object('manualOverride',?) WHERE id=?",target,action,id);
        case "LEGACY_ADJUSTMENT" -> jdbc.update("UPDATE finance_migration_facts SET event_type='LEGACY_ADJUSTMENT',classification=CASE WHEN account_code IS NULL AND direction<>'NONE' THEN 'LEGACY_UNALLOCATED' ELSE 'NON_CASH' END,issue_code=CASE WHEN account_code IS NULL AND direction<>'NONE' THEN 'ACCOUNT_UNKNOWN' ELSE NULL END,evidence=jsonb_build_object('manualOverride',?) WHERE id=?",action,id);
        default -> throw new IllegalStateException("Unexpected migration action " + action);
      }
    }
  }

  public Map<String,Object> counts(UUID batch) {
    var rows = jdbc.queryForList("SELECT classification,count(*) AS count,coalesce(sum(amount),0) AS amount FROM finance_migration_facts WHERE batch_id=? GROUP BY classification ORDER BY classification",batch);
    var accounts = jdbc.queryForList("SELECT f.account_code,r.sheet_name,count(*) AS count,coalesce(sum(CASE WHEN f.direction='IN' THEN f.amount WHEN f.direction='OUT' THEN -f.amount ELSE 0 END),0) AS net_amount FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id WHERE f.batch_id=? AND f.source_role='OWNER' GROUP BY f.account_code,r.sheet_name ORDER BY f.account_code,r.sheet_name",batch);
    long links = jdbc.queryForObject("SELECT count(*) FROM finance_migration_links WHERE batch_id=?",Long.class,batch);
    long identityLinks = jdbc.queryForObject("SELECT count(*) FROM finance_migration_identity_links WHERE batch_id=? AND relation_type='DUPLICATE_SOURCE'",Long.class,batch);
    long ownerTransfers = jdbc.queryForObject("SELECT count(*) FROM finance_migration_identity_links WHERE batch_id=? AND relation_type='OWNER_TRANSFER'",Long.class,batch);
    long facts = jdbc.queryForObject("SELECT count(*) FROM finance_migration_facts WHERE batch_id=?",Long.class,batch);
    long sourceRows = jdbc.queryForObject("SELECT count(*) FROM finance_migration_rows WHERE batch_id=?",Long.class,batch);
    long financialRows = jdbc.queryForObject("SELECT count(DISTINCT row_id) FROM finance_migration_facts WHERE batch_id=? AND source_role IN ('OWNER','DOMAIN') AND amount>0",Long.class,batch);
    long canonicalPosted = jdbc.queryForObject("SELECT count(DISTINCT canonical_transaction_id) FROM finance_migration_facts WHERE batch_id=? AND canonical_transaction_id IS NOT NULL",Long.class,batch);
    long canonicalFactMemberships = jdbc.queryForObject("SELECT count(*) FROM finance_migration_facts WHERE batch_id=? AND canonical_transaction_id IS NOT NULL",Long.class,batch);
    BigDecimal unallocated = jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM finance_migration_facts WHERE batch_id=? AND source_role='DOMAIN' AND classification='LEGACY_UNALLOCATED' AND duplicate_of_fact_id IS NULL",BigDecimal.class,batch);
    var accountAttribution = jdbc.queryForList("SELECT (SELECT o.account_code FROM finance_migration_facts o WHERE o.id=l.owner_fact_id) AS account_code,count(DISTINCT l.domain_fact_id) AS facts,coalesce(sum(l.allocated_amount),0) AS amount FROM finance_migration_links l WHERE l.batch_id=? GROUP BY 1 ORDER BY 1",batch);
    var issueGroups = jdbc.queryForList("SELECT issue_code AS reason,count(*) AS count,coalesce(sum(amount),0) AS amount FROM finance_migration_facts WHERE batch_id=? AND issue_code IS NOT NULL GROUP BY issue_code ORDER BY issue_code",batch);
    var linkKinds = jdbc.queryForList("SELECT confidence,count(*) AS count FROM finance_migration_links WHERE batch_id=? GROUP BY confidence ORDER BY confidence",batch);
    Map<String,Object> result = new HashMap<>();
    result.put("sourceRows",sourceRows);
    result.put("financialRows",financialRows);
    result.put("nonFinancialRows",sourceRows-financialRows);
    result.put("facts",facts);
    result.put("classifications",rows);
    result.put("ownerEvidence",accounts);
    result.put("accountAttribution",accountAttribution);
    result.put("linkedFacts",links);
    result.put("exactOrHighLinks",linkKinds);
    result.put("duplicateSourceLinks",identityLinks);
    result.put("ownerTransferLinks",ownerTransfers);
    result.put("unallocatedAmount",unallocated);
    result.put("issueGroups",issueGroups);
    result.put("canonicalPosted",canonicalPosted);
    result.put("canonicalFactMemberships",canonicalFactMemberships);
    return result;
  }

  public Map<String,Object> review(UUID batch, int page) {
    if (page < 0 || page > 100000) throw ApiException.badRequest("INVALID_PAGE","Choose a valid page.");
    long total = jdbc.queryForObject("SELECT count(*) FROM finance_migration_facts WHERE batch_id=? AND issue_code IS NOT NULL",Long.class,batch);
    var rows = jdbc.queryForList("SELECT f.id,f.slot,f.source_range AS \"sourceRange\",f.event_type AS \"eventType\",f.amount,f.event_date AS \"date\",f.raw_name AS \"rawName\",f.account_code AS \"accountCode\",f.classification,f.issue_code AS \"reason\",f.evidence,r.sheet_name AS \"sheetName\",r.source_row AS \"sourceRow\",r.raw_values AS \"original\" FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id WHERE f.batch_id=? AND f.issue_code IS NOT NULL ORDER BY r.sheet_name,r.source_row,f.slot LIMIT 50 OFFSET ?",batch,page*50);
    return Map.of("total",total,"page",page,"items",rows);
  }

  @Transactional
  public Map<String,Object> decide(UUID batch, String sheet, int sourceRow, String slot, String action, String target, String reason) {
    if (sheet == null || slot == null || action == null || reason == null || reason.isBlank() || reason.length()>500)
      throw ApiException.badRequest("REVIEW_DECISION_INVALID","Choose a migration action and record its reason.");
    if (!Set.of("ASSIGN_AZ","ASSIGN_AK","MARK_NON_CASH","LINK_EXISTING","MARK_DISTINCT","MAP_ENTITY","IGNORE_NON_FINANCIAL","LEGACY_ADJUSTMENT").contains(action))
      throw ApiException.badRequest("REVIEW_ACTION_INVALID","Choose a supported migration action.");
    var facts = jdbc.queryForList("SELECT f.id,f.amount,f.direction,f.source_role,f.raw_name,b.workbook_sha256 FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id JOIN finance_migration_batches b ON b.id=f.batch_id WHERE f.batch_id=? AND r.sheet_name=? AND r.source_row=? AND f.slot=? FOR UPDATE OF f",batch,sheet,sourceRow,slot);
    if (facts.size()!=1) throw ApiException.notFound("MIGRATION_FACT_NOT_FOUND","The source fact was not found.");
    var fact = facts.getFirst();
    if ((action.equals("ASSIGN_AZ") || action.equals("ASSIGN_AK")) && (fact.get("direction").equals("NONE") || !fact.get("source_role").equals("DOMAIN")))
      throw ApiException.badRequest("ACCOUNT_ASSIGNMENT_INVALID","Only an unresolved domain cash fact can receive a reviewed owner-account assignment.");
    if (Set.of("LINK_EXISTING","MAP_ENTITY").contains(action) && (target == null || target.isBlank()))
      throw ApiException.badRequest("REVIEW_TARGET_REQUIRED","Choose a verified target for this decision.");
    if (action.equals("LINK_EXISTING")) {
      UUID transaction;
      try { transaction = UUID.fromString(target); }
      catch (IllegalArgumentException exception) { throw ApiException.badRequest("REVIEW_TARGET_INVALID","Choose a valid posted transaction."); }
      var matches = jdbc.queryForList("SELECT amount,status,payer_account_id,receiver_account_id FROM finance_transactions WHERE id=?",transaction);
      if (matches.size()!=1 || !"POSTED".equals(matches.getFirst().get("status"))
          || ((BigDecimal)matches.getFirst().get("amount")).compareTo((BigDecimal)fact.get("amount"))!=0
          || (fact.get("direction").equals("IN") && matches.getFirst().get("receiver_account_id")==null)
          || (fact.get("direction").equals("OUT") && matches.getFirst().get("payer_account_id")==null))
        throw ApiException.conflict("REVIEW_LINK_CONFLICT","The posted transaction does not match this source amount and cash direction.");
    }
    String actor = SecurityContextHolder.getContext().getAuthentication()==null ? "system" : SecurityContextHolder.getContext().getAuthentication().getName();
    UUID override = jdbc.queryForObject("INSERT INTO finance_migration_overrides(workbook_sha256,sheet_name,source_row,slot,action,target,reason,actor) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT (workbook_sha256,sheet_name,source_row,slot) DO UPDATE SET action=excluded.action,target=excluded.target,reason=excluded.reason,actor=excluded.actor,updated_at=now() RETURNING id",UUID.class,fact.get("workbook_sha256"),sheet,sourceRow,slot,action,target,reason.trim(),actor);
    jdbc.update("INSERT INTO finance_migration_override_audit(override_id,action,target,reason,actor) VALUES(?,?,?,?,?)",override,action,target,reason.trim(),actor);
    if (action.equals("MAP_ENTITY")) {
      jdbc.update("INSERT INTO finance_migration_aliases(raw_name,normalized_identity,entity_type,confidence,source,reviewed) VALUES(?,?,'UNCLASSIFIED','MANUAL','OWNER_REVIEW',true) ON CONFLICT (raw_name,entity_type) DO UPDATE SET normalized_identity=excluded.normalized_identity,confidence='MANUAL',source='OWNER_REVIEW',reviewed=true",
          fact.get("raw_name"),normalize(target));
    }
    resolve(batch);
    return Map.of("overrideId",override,"batchId",batch,"action",action);
  }

  private static BigDecimal amount(Row row, int column) {
    Cell c = row.getCell(column);
    if (c == null) return null;
    CellType type = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
    return type == CellType.NUMERIC && !DateUtil.isCellDateFormatted(c) ? BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros() : null;
  }

  private static LocalDate date(Row row, int column) {
    Cell c = row.getCell(column);
    if (c == null) return null;
    CellType type = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
    if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) return c.getLocalDateTimeCellValue().toLocalDate();
    if (type == CellType.STRING) {
      try { return LocalDate.parse(c.getStringCellValue().trim()); } catch (RuntimeException ignored) { return null; }
    }
    return null;
  }

  private static String text(Row row, int column) {
    if (row == null) return null;
    Cell c = row.getCell(column);
    if (c == null) return null;
    CellType type = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
    if (type == CellType.STRING) return c.getStringCellValue().trim();
    if (type == CellType.NUMERIC) return DateUtil.isCellDateFormatted(c) ? c.getLocalDateTimeCellValue().toLocalDate().toString() : BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
    return null;
  }

  static String normalize(String input) {
    if (input == null) return "";
    String n = Normalizer.normalize(input,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}]+"," ").replaceAll("\\s+"," ").trim();
    return n.replaceFirst("(?:\\s+ji)$", "");
  }

  private static String column(int index) {
    StringBuilder s = new StringBuilder();
    do { s.insert(0,(char)('A'+index%26)); index=index/26-1; } while(index>=0);
    return s.toString();
  }

  private String serialize(Object value) {
    try { return json.writeValueAsString(value); }
    catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize migration evidence",e); }
  }

  private record Fact(UUID id, UUID rowId, String slot, String event, BigDecimal amount, LocalDate date, String rawName,
                      String name, String direction, String role, String account, String classification, String sheet, String venue) {}
  private record Candidate(Fact domain, Fact owner, String confidence, String identity, long days) {}
}
