package com.saproduction.command.finance;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded SQL-backed projections; no browser-side financial calculation. */
@Service
public class FinanceReadService {
  private static final BigDecimal ZERO = new BigDecimal("0.00");
  private final JdbcTemplate jdbc;
  private final AuditService audit;

  public FinanceReadService(JdbcTemplate jdbc, AuditService audit) {
    this.jdbc = jdbc;
    this.audit = audit;
  }

  public Map<String, Object> overview() {
    var totals = jdbc.queryForMap("""
        SELECT
          coalesce(sum(amount) FILTER (WHERE transaction_type IN ('PRODUCTION_RECEIPT','COUNTERPARTY_RECEIPT','INVOICE_PAYMENT')),0) AS received,
          coalesce(sum(amount) FILTER (WHERE transaction_type IN ('PRODUCTION_EXPENSE','GENERAL_EXPENSE','EMPLOYEE_EARNING','MONTHLY_SALARY_ACCRUAL')),0) AS expenses,
          coalesce(sum(amount) FILTER (WHERE transaction_type='PRODUCTION_CONTRACT'),0) AS contracted,
          count(*) FILTER (WHERE status='POSTED') AS posted_count
        FROM finance_transactions WHERE status='POSTED'
        """);
    BigDecimal received = (BigDecimal) totals.get("received");
    BigDecimal expenses = (BigDecimal) totals.get("expenses");
    return Map.of(
        "received", received,
        "incurredExpense", expenses,
        "overallResult", received.subtract(expenses),
        "contracted", totals.get("contracted"),
        "postedCount", totals.get("posted_count"),
        "accounts", accounts(),
        "receivables", productionReceivables(),
        "profitAllocation", allocation(received.subtract(expenses), LocalDate.now()));
  }

  public List<Map<String, Object>> accounts() {
    return jdbc.queryForList("""
        SELECT a.id,a.code,a.display_name AS "displayName",a.currency,p.position,p.updated_at AS "updatedAt"
        FROM finance_accounts a JOIN finance_account_positions p ON p.account_id=a.id ORDER BY a.code DESC
        """);
  }

  public Map<String, Object> account(UUID id) {
    return one("SELECT a.id,a.code,a.display_name AS \"displayName\",p.position FROM finance_accounts a JOIN finance_account_positions p ON p.account_id=a.id WHERE a.id=?", id);
  }

  public Map<String, Object> accountLedger(UUID id, int page, int size) {
    account(id);
    return page("""
        SELECT t.id,t.transaction_no AS "transactionNo",t.effective_date AS date,t.transaction_type AS type,
          t.description,t.status,t.amount,l.debit,l.credit,t.reversal_of AS "reversalOf"
        FROM finance_journal_lines l JOIN finance_journal_entries j ON j.id=l.entry_id
        JOIN finance_transactions t ON t.id=j.transaction_id
        WHERE l.owner_account_id=? ORDER BY t.effective_date DESC,t.transaction_no DESC
        """, "SELECT count(*) FROM finance_journal_lines WHERE owner_account_id=?", page, size, id);
  }

  public Map<String, Object> transactions(int page, int size, String type, UUID productionId, String search) {
    String filter = " WHERE (CAST(? AS text) IS NULL OR t.transaction_type=?) AND (CAST(? AS uuid) IS NULL OR t.production_id=?) AND (CAST(? AS text) IS NULL OR lower(t.description) LIKE lower('%'||?||'%'))";
    Object[] args = {type,type,productionId,productionId,search,search};
    int limit = limit(size), offset = offset(page, limit);
    var items = jdbc.queryForList("""
        SELECT t.id,t.transaction_no AS "transactionNo",t.effective_date AS date,t.transaction_type AS type,
          t.status,t.description,t.amount,t.production_id AS "productionId",p.title AS "productionTitle",
          t.employee_id AS "employeeId",e.display_name AS "employeeName",t.counterparty_id AS "counterpartyId",
          c.display_name AS "counterpartyName",t.reversal_of AS "reversalOf",t.posted_at AS "postedAt"
        FROM finance_transactions t LEFT JOIN productions p ON p.id=t.production_id
        LEFT JOIN employees e ON e.id=t.employee_id LEFT JOIN finance_counterparties c ON c.id=t.counterparty_id
        """ + filter + " ORDER BY t.effective_date DESC,t.transaction_no DESC LIMIT ? OFFSET ?",
        type,type,productionId,productionId,search,search,limit,offset);
    Long count = jdbc.queryForObject("SELECT count(*) FROM finance_transactions t" + filter, Long.class, args);
    return Map.of("items", items, "page", page, "size", limit, "total", count == null ? 0 : count);
  }

  public Map<String, Object> transaction(UUID id) {
    var row = one("SELECT * FROM finance_transactions WHERE id=?", id);
    return Map.of("transaction", row, "journal", jdbc.queryForList("""
        SELECT l.ledger_code AS "ledgerCode",l.debit,l.credit,a.code AS "ownerAccount"
        FROM finance_journal_lines l JOIN finance_journal_entries e ON e.id=l.entry_id
        LEFT JOIN finance_accounts a ON a.id=l.owner_account_id
        WHERE e.transaction_id=? ORDER BY l.id
        """, id));
  }

  public Map<String, Object> production(UUID id) {
    var base = one("SELECT p.id,p.title,p.client_name AS \"clientName\",p.event_date AS \"eventDate\",p.venue_name AS \"venueName\",coalesce(f.contracted_amount,0) AS contracted FROM productions p LEFT JOIN finance_production_profiles f ON f.production_id=p.id WHERE p.id=?", id);
    BigDecimal received = amount("SELECT coalesce(sum(a.amount),0) FROM finance_production_receipt_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.production_id=? AND t.status='POSTED'", id);
    BigDecimal expense = amount("SELECT coalesce(sum(amount),0) FROM finance_transactions WHERE production_id=? AND status='POSTED' AND transaction_type IN ('PRODUCTION_EXPENSE','EMPLOYEE_EARNING')", id);
    BigDecimal contracted = (BigDecimal) base.get("contracted");
    LocalDate eventDate = ((java.sql.Date) base.get("eventDate")).toLocalDate();
    return Map.of("production", base, "received", received, "outstanding", contracted.subtract(received),
        "incurredExpense", expense, "contractedMargin", contracted.subtract(expense), "realizedMargin", received.subtract(expense),
        "profitAllocation", allocation(received.subtract(expense), eventDate),
        "transactions", jdbc.queryForList("SELECT id,transaction_no AS \"transactionNo\",transaction_type AS type,effective_date AS date,description,amount,status FROM finance_transactions WHERE production_id=? ORDER BY effective_date DESC,transaction_no DESC LIMIT 100", id));
  }

  public Map<String, Object> productions(int page, int size, String search) {
    return page("""
        SELECT p.id,p.title,p.client_name AS "clientName",p.event_date AS "eventDate",
          coalesce(f.contracted_amount,0) AS contracted,
          coalesce((SELECT sum(a.amount) FROM finance_production_receipt_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.production_id=p.id AND t.status='POSTED'),0) AS received,
          coalesce((SELECT sum(t.amount) FROM finance_transactions t WHERE t.production_id=p.id AND t.status='POSTED' AND t.transaction_type IN ('PRODUCTION_EXPENSE','EMPLOYEE_EARNING')),0) AS expense
        FROM productions p LEFT JOIN finance_production_profiles f ON f.production_id=p.id
        WHERE (CAST(? AS text) IS NULL OR lower(p.title) LIKE lower('%'||?||'%')) ORDER BY p.event_date DESC,p.id
        """, "SELECT count(*) FROM productions WHERE (CAST(? AS text) IS NULL OR lower(title) LIKE lower('%'||?||'%'))", page, size, search, search);
  }

  public Map<String, Object> employee(UUID id) {
    var person = one("SELECT id,display_name AS \"displayName\",employee_code AS \"employeeCode\" FROM employees WHERE id=?", id);
    BigDecimal earned = amount("SELECT coalesce(sum(o.net_amount),0) FROM finance_employee_obligations o LEFT JOIN finance_transactions t ON t.id=o.source_transaction_id WHERE o.employee_id=? AND (t.id IS NULL OR t.status='POSTED')", id);
    BigDecimal paid = amount("SELECT coalesce(sum(a.amount),0) FROM finance_employee_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id JOIN finance_employee_obligations o ON o.id=a.obligation_id WHERE o.employee_id=? AND t.status='POSTED'", id);
    return Map.of("employee", person, "earned", earned, "paid", paid, "outstanding", earned.subtract(paid),
        "obligations", jdbc.queryForList("SELECT id,obligation_type AS type,effective_date AS date,net_amount AS amount,description FROM finance_employee_obligations WHERE employee_id=? ORDER BY effective_date DESC,id DESC LIMIT 100", id));
  }

  public Map<String, Object> employeePayables(int page, int size) {
    return page("""
        SELECT e.id,e.display_name AS "displayName",coalesce(sum(o.net_amount) FILTER (WHERE t.id IS NULL OR t.status='POSTED'),0) AS earned,
          coalesce((SELECT sum(a.amount) FROM finance_employee_payment_allocations a JOIN finance_transactions pt ON pt.id=a.transaction_id JOIN finance_employee_obligations eo ON eo.id=a.obligation_id WHERE eo.employee_id=e.id AND pt.status='POSTED'),0) AS paid
        FROM employees e LEFT JOIN finance_employee_obligations o ON o.employee_id=e.id
        LEFT JOIN finance_transactions t ON t.id=o.source_transaction_id
        GROUP BY e.id ORDER BY e.display_name,e.id
        """, "SELECT count(*) FROM employees", page, size);
  }

  public Map<String, Object> counterparties(int page, int size, String search) {
    return page("""
        SELECT c.id,c.display_name AS "displayName",c.role,c.gstin,
          coalesce((SELECT sum(ch.amount) FROM finance_counterparty_charges ch JOIN finance_transactions t ON t.id=ch.source_transaction_id WHERE ch.counterparty_id=c.id AND t.status='POSTED'),0) AS charged,
          coalesce((SELECT sum(a.amount) FROM finance_counterparty_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id JOIN finance_counterparty_charges ch ON ch.id=a.charge_id WHERE ch.counterparty_id=c.id AND t.status='POSTED'),0) AS received
        FROM finance_counterparties c WHERE (CAST(? AS text) IS NULL OR lower(c.display_name) LIKE lower('%'||?||'%'))
        ORDER BY c.display_name,c.id
        """, "SELECT count(*) FROM finance_counterparties WHERE (CAST(? AS text) IS NULL OR lower(display_name) LIKE lower('%'||?||'%'))", page, size, search, search);
  }

  public Map<String, Object> counterparty(UUID id) {
    var party = one("SELECT id,display_name AS \"displayName\",role,gstin FROM finance_counterparties WHERE id=?", id);
    var entries = jdbc.queryForList("SELECT id,transaction_no AS \"transactionNo\",transaction_type AS type,effective_date AS date,description,amount,status FROM finance_transactions WHERE counterparty_id=? ORDER BY effective_date DESC,transaction_no DESC LIMIT 100", id);
    return Map.of("counterparty", party, "transactions", entries);
  }

  public Map<String, Object> invoices(int page, int size) {
    return page("""
        SELECT i.id,i.invoice_number AS "invoiceNumber",i.invoice_date AS date,c.display_name AS "counterpartyName",i.invoice_total AS total,i.tds_amount AS tds,
          coalesce((SELECT sum(a.amount) FROM finance_invoice_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.invoice_id=i.id AND t.status='POSTED'),0) AS paid
        FROM finance_invoices i JOIN finance_counterparties c ON c.id=i.counterparty_id
        JOIN finance_transactions t ON t.id=i.source_transaction_id WHERE t.status='POSTED' ORDER BY i.invoice_date DESC,i.id
        """, "SELECT count(*) FROM finance_invoices i JOIN finance_transactions t ON t.id=i.source_transaction_id WHERE t.status='POSTED'", page, size);
  }

  public Map<String, Object> invoice(UUID id) {
    var item = one("SELECT i.* FROM finance_invoices i JOIN finance_transactions t ON t.id=i.source_transaction_id WHERE i.id=? AND t.status='POSTED'", id);
    BigDecimal paid = amount("SELECT coalesce(sum(a.amount),0) FROM finance_invoice_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.invoice_id=? AND t.status='POSTED'", id);
    return Map.of("invoice", item, "paid", paid, "remaining", ((BigDecimal)item.get("invoice_total")).subtract((BigDecimal)item.get("tds_amount")).subtract(paid));
  }

  public Map<String, Object> purchases(int page, int size) {
    return page("""
        SELECT p.id,p.purchase_date AS date,p.description,p.total,c.display_name AS "counterpartyName",
          coalesce((SELECT sum(a.amount) FROM finance_equipment_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.purchase_id=p.id AND t.status='POSTED'),0) AS paid
        FROM finance_equipment_purchases p LEFT JOIN finance_counterparties c ON c.id=p.counterparty_id
        JOIN finance_transactions t ON t.id=p.source_transaction_id WHERE t.status='POSTED' ORDER BY p.purchase_date DESC,p.id
        """, "SELECT count(*) FROM finance_equipment_purchases p JOIN finance_transactions t ON t.id=p.source_transaction_id WHERE t.status='POSTED'", page, size);
  }

  public Map<String, Object> purchase(UUID id) {
    var item = one("SELECT p.* FROM finance_equipment_purchases p JOIN finance_transactions t ON t.id=p.source_transaction_id WHERE p.id=? AND t.status='POSTED'", id);
    BigDecimal paid = amount("SELECT coalesce(sum(a.amount),0) FROM finance_equipment_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.purchase_id=? AND t.status='POSTED'", id);
    return Map.of("purchase", item, "paid", paid, "remaining", ((BigDecimal)item.get("total")).subtract(paid),
        "items", jdbc.queryForList("SELECT id,headquarters_equipment_id AS \"headquartersEquipmentId\",description,quantity,unit_rate AS \"unitRate\",tax,amount FROM finance_equipment_purchase_items WHERE purchase_id=?", id));
  }

  public Map<String, Object> config() {
    return Map.of("accounts", accounts(), "expenseCategories", jdbc.queryForList("SELECT id,code,display_name AS \"displayName\" FROM finance_expense_categories WHERE active ORDER BY display_name"),
        "profitSplit", jdbc.queryForList("SELECT effective_from AS \"effectiveFrom\",azeem_percent AS azeem,akash_percent AS akash FROM finance_profit_split_rules ORDER BY effective_from DESC LIMIT 1"));
  }

  @Transactional
  public Map<String, Object> reconciliation() {
    BigDecimal expected = amount("SELECT coalesce(sum(l.debit-l.credit),0) FROM finance_journal_lines l WHERE l.owner_account_id IS NOT NULL");
    BigDecimal actual = amount("SELECT coalesce(sum(position),0) FROM finance_account_positions");
    BigDecimal journalDifference = amount("SELECT coalesce(sum(abs(q.debits-q.credits)),0) FROM (SELECT sum(l.debit) AS debits,sum(l.credit) AS credits FROM finance_journal_lines l GROUP BY l.entry_id) q");
    BigDecimal difference = actual.subtract(expected).add(journalDifference);
    BigDecimal receivables = productionReceivables();
    BigDecimal employeePayables = amount("SELECT coalesce(sum(net_amount),0) FROM finance_employee_obligations o LEFT JOIN finance_transactions t ON t.id=o.source_transaction_id WHERE t.id IS NULL OR t.status='POSTED'")
        .subtract(amount("SELECT coalesce(sum(a.amount),0) FROM finance_employee_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE t.status='POSTED'"));
    BigDecimal invoiceReceivables = amount("SELECT coalesce(sum(i.invoice_total-i.tds_amount),0) FROM finance_invoices i JOIN finance_transactions t ON t.id=i.source_transaction_id WHERE t.status='POSTED'")
        .subtract(amount("SELECT coalesce(sum(a.amount),0) FROM finance_invoice_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE t.status='POSTED'"));
    BigDecimal equipmentPayables = amount("SELECT coalesce(sum(p.total),0) FROM finance_equipment_purchases p JOIN finance_transactions t ON t.id=p.source_transaction_id WHERE t.status='POSTED'")
        .subtract(amount("SELECT coalesce(sum(a.amount),0) FROM finance_equipment_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE t.status='POSTED'"));
    long unresolvedMigration = jdbc.queryForObject("SELECT count(*) FROM finance_migration_facts WHERE batch_id=(SELECT id FROM finance_migration_batches ORDER BY created_at DESC LIMIT 1) AND issue_code IS NOT NULL",Long.class);
    String status = difference.signum() == 0 ? "RECONCILED" : "BROKEN";
    var accounts = accounts();
    BigDecimal az = position(accounts, "AZ-2"), ak = position(accounts, "AK-2");
    BigDecimal overall = (BigDecimal) overview().get("overallResult");
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO finance_reconciliation_snapshots(id,calculation_version,overall_result,azeem_position,akash_position,receivables,employee_payables,invoice_receivables,equipment_payables,control_difference,status) VALUES(?,'FIN-1',?,?,?,?,?,?,?,?,?)", id, overall, az, ak, receivables, employeePayables, invoiceReceivables, equipmentPayables, difference, status);
    Map<String,Object> result = new LinkedHashMap<>(Map.of("id", id, "status", status, "controlDifference", difference, "overallResult", overall, "azeemPosition", az, "akashPosition", ak,
        "receivables", receivables, "employeePayables", employeePayables, "invoiceReceivables", invoiceReceivables, "equipmentPayables", equipmentPayables));
    result.put("migrationOpenCount",unresolvedMigration);
    return result;
  }

  @Transactional
  public Map<String, Object> rebuildPositions() {
    BigDecimal before = amount("SELECT coalesce(sum(position),0) FROM finance_account_positions");
    jdbc.update("""
        UPDATE finance_account_positions p SET position=coalesce((SELECT sum(l.debit-l.credit) FROM finance_journal_lines l WHERE l.owner_account_id=p.account_id),0),
          version=version+1,updated_at=now()
        """);
    BigDecimal after = amount("SELECT coalesce(sum(position),0) FROM finance_account_positions");
    var result = reconciliation();
    jdbc.update("INSERT INTO finance_projection_checkpoints(before_hash,after_hash,consistent) VALUES(md5(?),md5(?),?)", before.toPlainString(), after.toPlainString(), "RECONCILED".equals(result.get("status")));
    audit.record("FINANCE_PROJECTION", "FINANCE_POSITION_REBUILT", "owner-accounts", Map.of("before", before), Map.of("after", after));
    return result;
  }

  private BigDecimal productionReceivables() {
    return amount("SELECT coalesce(sum(f.contracted_amount),0) FROM finance_production_profiles f")
        .subtract(amount("SELECT coalesce(sum(a.amount),0) FROM finance_production_receipt_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE t.status='POSTED'"));
  }

  private Map<String,BigDecimal> allocation(BigDecimal result, LocalDate effectiveDate) {
    var rule = jdbc.queryForList("SELECT azeem_percent FROM finance_profit_split_rules WHERE effective_from<=? AND (effective_to IS NULL OR effective_to>=?) ORDER BY effective_from DESC LIMIT 1", effectiveDate, effectiveDate);
    if (rule.isEmpty()) return Map.of("azeem", ZERO, "akash", ZERO);
    BigDecimal azeem = result.multiply((BigDecimal)rule.getFirst().get("azeem_percent"))
        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    return Map.of("azeem", azeem, "akash", result.subtract(azeem));
  }

  private BigDecimal position(List<Map<String,Object>> rows, String code) {
    return rows.stream().filter(row -> code.equals(row.get("code"))).map(row -> (BigDecimal)row.get("position")).findFirst().orElse(ZERO);
  }

  private BigDecimal amount(String sql, Object... args) {
    BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
    return value == null ? ZERO : value;
  }

  private Map<String,Object> one(String sql, Object... args) {
    return jdbc.queryForList(sql, args).stream().findFirst().orElseThrow(() -> ApiException.notFound("FINANCE_RECORD_NOT_FOUND", "Financial record not found."));
  }

  private Map<String,Object> page(String dataSql, String countSql, int page, int size, Object... args) {
    int limit = limit(size), offset = offset(page, limit);
    Object[] dataArgs = java.util.Arrays.copyOf(args, args.length + 2);
    dataArgs[args.length] = limit;
    dataArgs[args.length + 1] = offset;
    List<Map<String,Object>> items = jdbc.queryForList(dataSql + " LIMIT ? OFFSET ?", dataArgs);
    Long total = jdbc.queryForObject(countSql, Long.class, args);
    return Map.of("items", items, "page", page, "size", limit, "total", total == null ? 0 : total);
  }

  private int limit(int requested) { return Math.min(100, Math.max(1, requested)); }
  private int offset(int page, int limit) {
    if (page < 0 || page > 100000) throw ApiException.badRequest("INVALID_PAGE", "Choose a valid page.");
    return page * limit;
  }
}
