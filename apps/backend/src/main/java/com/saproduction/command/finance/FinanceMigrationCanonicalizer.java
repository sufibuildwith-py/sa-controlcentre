package com.saproduction.command.finance;

import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Posts only historical events whose complete accounting effect is proven by source evidence. */
@Service
public class FinanceMigrationCanonicalizer {
  private final JdbcTemplate jdbc;
  private final FinancePostingService posting;

  public FinanceMigrationCanonicalizer(JdbcTemplate jdbc, FinancePostingService posting) {
    this.jdbc = jdbc;
    this.posting = posting;
  }

  @Transactional
  public Map<String,Object> postProven(UUID batch) {
    var batches = jdbc.queryForList("SELECT workbook_sha256 FROM finance_migration_batches WHERE id=? FOR UPDATE",batch);
    if (batches.isEmpty()) throw ApiException.notFound("MIGRATION_NOT_FOUND","Migration batch not found.");
    String hash = (String)batches.getFirst().get("workbook_sha256");
    int purchases = 0;
    int transfers = 0;

    var purchaseFacts = jdbc.queryForList("""
        SELECT f.id,r.sheet_name,r.source_row,f.amount,f.event_date,f.raw_name,
               r.raw_values->'cells'->>'C' AS rate,r.raw_values->'cells'->>'D' AS tax_per_unit,
               r.raw_values->'cells'->>'E' AS quantity
        FROM finance_migration_facts f JOIN finance_migration_rows r ON r.id=f.row_id
        WHERE f.batch_id=? AND f.event_type='EQUIPMENT_PURCHASE' AND f.classification='NON_CASH'
          AND f.canonical_transaction_id IS NULL AND f.duplicate_of_fact_id IS NULL
        ORDER BY r.sheet_name,r.source_row,f.slot
        """,batch);
    for (var row : purchaseFacts) {
      BigDecimal rate = decimal(row.get("rate"));
      BigDecimal taxPerUnit = decimal(row.get("tax_per_unit"));
      BigDecimal quantity = decimal(row.get("quantity"));
      BigDecimal amount = (BigDecimal)row.get("amount");
      Date date = (Date)row.get("event_date");
      String name = (String)row.get("raw_name");
      UUID factId = (UUID)row.get("id");
      if (name == null || name.isBlank()) { review(factId,"ENTITY_UNKNOWN","Purchase item name is missing"); continue; }
      if (rate == null || quantity == null || quantity.signum() <= 0 || date == null
          || row.get("tax_per_unit") != null && taxPerUnit == null) {
        review(factId,"MALFORMED_SOURCE","Purchase rate, positive quantity or date is missing");
        continue;
      }
      BigDecimal subtotal = rate.multiply(quantity);
      BigDecimal tax = (taxPerUnit == null ? BigDecimal.ZERO : taxPerUnit).multiply(quantity);
      if (subtotal.signum() < 0 || tax.signum() < 0 || subtotal.scale() > 2 || tax.scale() > 2
          || subtotal.add(tax).compareTo(amount) != 0) {
        review(factId,"FORMULA_STATIC_CONFLICT","Purchase amount does not equal (rate + tax per unit) × quantity");
        continue;
      }
      String reference = row.get("sheet_name") + "!" + row.get("source_row");
      UUID key = key(hash,reference,"EQUIPMENT_PURCHASE");
      posting.purchase(new FinanceCommands.Purchase(key,null,date.toLocalDate(),reference,
          description(name,reference),subtotal,tax,null,null));
      UUID transaction = transaction(key);
      jdbc.update("UPDATE finance_migration_facts SET canonical_transaction_id=?,evidence=evidence||jsonb_build_object('canonicalRule','PROVEN_PURCHASE_ARITHMETIC') WHERE id=?",transaction,row.get("id"));
      purchases++;
    }

    var ownerTransfers = jdbc.queryForList("""
        SELECT l.primary_fact_id,l.duplicate_fact_id,p.amount,p.event_date,p.direction,p.account_code AS primary_account,
               d.account_code AS other_account,r.sheet_name,r.source_row,p.slot
        FROM finance_migration_identity_links l
        JOIN finance_migration_facts p ON p.id=l.primary_fact_id
        JOIN finance_migration_facts d ON d.id=l.duplicate_fact_id
        JOIN finance_migration_rows r ON r.id=p.row_id
        WHERE l.batch_id=? AND l.relation_type='OWNER_TRANSFER'
          AND p.canonical_transaction_id IS NULL AND d.canonical_transaction_id IS NULL
        ORDER BY r.sheet_name,r.source_row,p.slot
        """,batch);
    for (var row : ownerTransfers) {
      Date date = (Date)row.get("event_date");
      if (date == null) continue;
      String primary = (String)row.get("primary_account");
      String other = (String)row.get("other_account");
      String payer = "OUT".equals(row.get("direction")) ? primary : other;
      String receiver = "OUT".equals(row.get("direction")) ? other : primary;
      String reference = row.get("sheet_name") + "!" + row.get("source_row") + ":" + row.get("slot");
      UUID key = key(hash,reference,"OWNER_TRANSFER");
      posting.ownerTransfer(new FinanceCommands.OwnerMovement(key,(BigDecimal)row.get("amount"),date.toLocalDate(),
          description("Historical owner transfer",reference),payer,receiver));
      UUID transaction = transaction(key);
      jdbc.update("UPDATE finance_migration_facts SET canonical_transaction_id=?,evidence=evidence||jsonb_build_object('canonicalRule','MATCHED_BOTH_OWNER_LEDGERS') WHERE id IN (?,?)",
          transaction,row.get("primary_fact_id"),row.get("duplicate_fact_id"));
      transfers++;
    }
    return Map.of("equipmentPurchases",purchases,"ownerTransfers",transfers);
  }

  private UUID transaction(UUID key) {
    return jdbc.queryForObject("SELECT id FROM finance_transactions WHERE idempotency_key=?",UUID.class,key);
  }

  private void review(UUID fact, String code, String detail) {
    jdbc.update("UPDATE finance_migration_facts SET classification='REVIEW_REQUIRED',issue_code=?,evidence=evidence||jsonb_build_object('canonicalization',?) WHERE id=?",code,detail,fact);
    jdbc.update("INSERT INTO finance_migration_issues(batch_id,row_id,issue_code,detail) SELECT batch_id,row_id,?,? FROM finance_migration_facts WHERE id=?",code,detail,fact);
  }

  private static UUID key(String hash, String reference, String type) {
    return UUID.nameUUIDFromBytes(("finance-migration:"+hash+":"+reference+":"+type).getBytes(StandardCharsets.UTF_8));
  }

  private static BigDecimal decimal(Object value) {
    if (value == null) return null;
    try { return new BigDecimal(value.toString()); }
    catch (NumberFormatException ignored) { return null; }
  }

  private static String description(String name, String reference) {
    String value = name.trim() + " (" + reference + ")";
    return value.length() <= 500 ? value : value.substring(0,500);
  }
}
