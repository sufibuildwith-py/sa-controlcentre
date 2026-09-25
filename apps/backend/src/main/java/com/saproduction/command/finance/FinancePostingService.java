package com.saproduction.command.finance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.audit.AuditService;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The sole normal posting path: command identity, domain effect, balanced journal, projection and audit. */
@Service
public class FinancePostingService {
  private static final BigDecimal ZERO = new BigDecimal("0.00");
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final AuditService audit;

  public FinancePostingService(JdbcTemplate jdbc, ObjectMapper json, AuditService audit) {
    this.jdbc = jdbc;
    this.json = json;
    this.audit = audit;
  }

  record Line(String code, BigDecimal debit, BigDecimal credit, UUID ownerAccount) {}

  static final class Spec {
    final String type;
    final BigDecimal amount;
    final LocalDate date;
    final String description;
    final UUID key;
    final Object payload;
    UUID production;
    UUID employee;
    UUID party;
    UUID invoice;
    UUID purchase;
    UUID payer;
    UUID receiver;
    UUID category;
    UUID reversalOf;
    String legacyType;
    final List<Line> lines = new ArrayList<>();

    Spec(String type, BigDecimal amount, LocalDate date, String description, UUID key, Object payload) {
      this.type = type;
      this.amount = money(amount);
      this.date = date;
      this.description = description;
      this.key = key;
      this.payload = payload;
    }

    Spec debit(String code, BigDecimal value, UUID owner) {
      lines.add(new Line(code, money(value), ZERO, owner));
      return this;
    }

    Spec credit(String code, BigDecimal value, UUID owner) {
      lines.add(new Line(code, ZERO, money(value), owner));
      return this;
    }
  }

  @Transactional
  public Map<String, Object> contract(FinanceCommands.Contract input) {
    Spec s = new Spec("PRODUCTION_CONTRACT", input.amount(), input.date(), input.description(), input.idempotencyKey(), input);
    s.production = input.productionId();
    s.debit("PRODUCTION_RECEIVABLE", s.amount, null).credit("CONTRACTED_REVENUE", s.amount, null);
    return post(s, id -> {
      require("productions", input.productionId(), "PRODUCTION_NOT_FOUND");
      int inserted = jdbc.update("INSERT INTO finance_production_profiles(production_id,contracted_amount,source_transaction_id) VALUES(?,?,?) ON CONFLICT DO NOTHING", input.productionId(), s.amount, id);
      if (inserted == 0) throw ApiException.conflict("CONTRACT_ALREADY_SET", "This production already has a financial contract. Reverse and replace the original transaction to correct it.");
    });
  }

  @Transactional
  public Map<String, Object> receipt(FinanceCommands.Receipt input) {
    UUID receiver = accountId(input.receiverAccount());
    Spec s = new Spec("PRODUCTION_RECEIPT", input.amount(), input.date(), input.description(), input.idempotencyKey(), input);
    s.production = input.productionId();
    s.party = input.counterpartyId();
    s.receiver = receiver;
    s.legacyType = input.legacyType();
    s.debit("OWNER_POSITION", s.amount, receiver).credit("PRODUCTION_RECEIVABLE", s.amount, null);
    return post(s, id -> {
      var profile = one("SELECT contracted_amount FROM finance_production_profiles WHERE production_id=? FOR UPDATE", input.productionId(), "CONTRACT_NOT_FOUND");
      BigDecimal received = scalar("SELECT coalesce(sum(a.amount),0) FROM finance_production_receipt_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.production_id=? AND t.status='POSTED'", input.productionId());
      if (received.add(s.amount).compareTo((BigDecimal) profile.get("contracted_amount")) > 0)
        throw ApiException.conflict("RECEIPT_EXCEEDS_OUTSTANDING", "Receipt exceeds the production amount outstanding.");
      if (input.counterpartyId() != null) require("finance_counterparties", input.counterpartyId(), "COUNTERPARTY_NOT_FOUND");
      jdbc.update("INSERT INTO finance_production_receipt_allocations(transaction_id,production_id,amount,legacy_type) VALUES(?,?,?,?)", id, input.productionId(), s.amount, input.legacyType());
    });
  }

  @Transactional
  public Map<String, Object> expense(FinanceCommands.Expense input) {
    UUID payer = accountId(input.payerAccount());
    Spec s = new Spec(input.productionId() == null ? "GENERAL_EXPENSE" : "PRODUCTION_EXPENSE", input.amount(), input.date(), input.description(), input.idempotencyKey(), input);
    s.production = input.productionId();
    s.party = input.counterpartyId();
    s.employee = input.employeeId();
    s.payer = payer;
    if (input.categoryCode() != null && !input.categoryCode().isBlank()) {
      s.category = jdbc.query("SELECT id FROM finance_expense_categories WHERE code=? AND active", (rs, row) -> rs.getObject(1, UUID.class), input.categoryCode()).stream().findFirst().orElseThrow(() -> ApiException.badRequest("CATEGORY_NOT_FOUND", "Select an active expense category."));
    }
    s.debit(s.production == null ? "GENERAL_EXPENSE" : "PRODUCTION_EXPENSE", s.amount, null).credit("OWNER_POSITION", s.amount, payer);
    return post(s, id -> {
      if (s.production != null) require("productions", s.production, "PRODUCTION_NOT_FOUND");
      if (s.party != null) require("finance_counterparties", s.party, "COUNTERPARTY_NOT_FOUND");
      if (s.employee != null) require("employees", s.employee, "EMPLOYEE_NOT_FOUND");
      if (input.equipmentId() != null) require("hq_equipment", input.equipmentId(), "EQUIPMENT_NOT_FOUND");
    });
  }

  @Transactional
  public Map<String, Object> earning(FinanceCommands.Earning input) {
    Spec s = new Spec("EMPLOYEE_EARNING", input.amount(), input.date(), input.description(), input.idempotencyKey(), input);
    s.employee = input.employeeId();
    s.production = input.productionId();
    s.debit("LABOR_EXPENSE", s.amount, null).credit("EMPLOYEE_PAYABLE", s.amount, null);
    return post(s, id -> {
      require("employees", s.employee, "EMPLOYEE_NOT_FOUND");
      if (s.production != null) require("productions", s.production, "PRODUCTION_NOT_FOUND");
      jdbc.update("INSERT INTO finance_employee_obligations(employee_id,production_id,obligation_type,effective_date,gross_amount,description,source_transaction_id) VALUES(?,?,'WORK_EARNING',?,?,?,?)", s.employee, s.production, Date.valueOf(s.date), s.amount, s.description, id);
    });
  }

  @Transactional
  public Map<String, Object> salary(FinanceCommands.Salary input) {
    BigDecimal gross = nonnegative(input.gross());
    BigDecimal deductions = nonnegative(input.approvedDeductions());
    BigDecimal adjustment = scaled(input.adjustment());
    BigDecimal net = gross.subtract(deductions).add(adjustment);
    if (net.signum() <= 0) throw ApiException.badRequest("SALARY_NET_NOT_POSITIVE", "The net salary obligation must be positive.");
    Spec s = new Spec("MONTHLY_SALARY_ACCRUAL", net, input.date(), input.description(), input.idempotencyKey(), input);
    s.employee = input.employeeId();
    s.debit("SALARY_EXPENSE", net, null).credit("EMPLOYEE_PAYABLE", net, null);
    return post(s, id -> {
      require("employees", s.employee, "EMPLOYEE_NOT_FOUND");
      if (input.payrollItemId() != null) {
        var item = one("SELECT employee_id,net_salary_minor FROM payroll_items WHERE id=?", input.payrollItemId(), "PAYROLL_ITEM_NOT_FOUND");
        if (!s.employee.equals(item.get("employee_id")) || net.movePointRight(2).longValueExact() != ((Number)item.get("net_salary_minor")).longValue())
          throw ApiException.conflict("PAYROLL_SNAPSHOT_MISMATCH", "The salary amount differs from the locked payroll snapshot.");
      }
      jdbc.update("INSERT INTO finance_employee_obligations(employee_id,payroll_item_id,obligation_type,effective_date,gross_amount,approved_deductions,adjustment,description,source_transaction_id) VALUES(?,?,'FIXED_SALARY',?,?,?,?,?,?)", s.employee, input.payrollItemId(), Date.valueOf(s.date), gross, deductions, adjustment, s.description, id);
    });
  }

  @Transactional
  public Map<String, Object> employeePayment(FinanceCommands.EmployeePayment input) {
    UUID payer = accountId(input.payerAccount());
    Spec s = new Spec("EMPLOYEE_PAYMENT", input.amount(), input.date(), input.description(), input.idempotencyKey(), input);
    s.employee = input.employeeId();
    s.payer = payer;
    s.debit("EMPLOYEE_PAYABLE", s.amount, null).credit("OWNER_POSITION", s.amount, payer);
    return post(s, id -> {
      require("employees", s.employee, "EMPLOYEE_NOT_FOUND");
      BigDecimal remaining = s.amount;
      var obligations = jdbc.queryForList("SELECT o.id,o.net_amount FROM finance_employee_obligations o LEFT JOIN finance_transactions t ON t.id=o.source_transaction_id WHERE o.employee_id=? AND (t.id IS NULL OR t.status='POSTED') ORDER BY o.effective_date,o.id FOR UPDATE OF o", s.employee);
      for (var obligation : obligations) {
        if (remaining.signum() == 0) break;
        UUID obligationId = (UUID) obligation.get("id");
        BigDecimal paid = scalar("SELECT coalesce(sum(a.amount),0) FROM finance_employee_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.obligation_id=? AND t.status='POSTED'", obligationId);
        BigDecimal available = ((BigDecimal) obligation.get("net_amount")).subtract(paid);
        if (available.signum() <= 0) continue;
        BigDecimal allocated = remaining.min(available);
        jdbc.update("INSERT INTO finance_employee_payment_allocations(transaction_id,obligation_id,amount) VALUES(?,?,?)", id, obligationId, allocated);
        remaining = remaining.subtract(allocated);
      }
      if (remaining.signum() > 0) throw ApiException.conflict("EMPLOYEE_PAYMENT_EXCEEDS_PAYABLE", "Payment exceeds the employee amount remaining.");
    });
  }

  @Transactional
  public Map<String, Object> createCounterparty(FinanceCommands.Counterparty input) {
    if (!Set.of("CUSTOMER","VENDOR","RENTAL_PROVIDER","SUBCONTRACTOR","OTHER","UNKNOWN","MIXED").contains(input.role()))
      throw ApiException.badRequest("INVALID_ROLE", "Choose a supported party role.");
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO finance_counterparties(id,display_name,role,gstin) VALUES(?,?,?,?)", id, input.displayName().trim(), input.role(), input.gstin());
    audit.record("FINANCE_COUNTERPARTY", "FINANCE_COUNTERPARTY_CREATED", id.toString(), null, Map.of("displayName", input.displayName()));
    return Map.of("id", id, "displayName", input.displayName(), "role", input.role());
  }

  @Transactional
  public Map<String, Object> charge(FinanceCommands.Charge input) {
    Spec s = new Spec("COUNTERPARTY_CHARGE", input.amount(), input.date(), input.description(), input.idempotencyKey(), input);
    s.party = input.counterpartyId();
    s.production = input.productionId();
    s.debit("COUNTERPARTY_RECEIVABLE", s.amount, null).credit("CONTRACTED_REVENUE", s.amount, null);
    return post(s, id -> {
      require("finance_counterparties", s.party, "COUNTERPARTY_NOT_FOUND");
      if (s.production != null) require("productions", s.production, "PRODUCTION_NOT_FOUND");
      jdbc.update("INSERT INTO finance_counterparty_charges(counterparty_id,production_id,effective_date,amount,description,source_transaction_id) VALUES(?,?,?,?,?,?)", s.party, s.production, Date.valueOf(s.date), s.amount, s.description, id);
    });
  }

  @Transactional
  public Map<String, Object> partyReceipt(FinanceCommands.PartyReceipt input) {
    UUID receiver = accountId(input.receiverAccount());
    Spec s = new Spec("COUNTERPARTY_RECEIPT", input.amount(), input.date(), input.description(), input.idempotencyKey(), input);
    s.party = input.counterpartyId();
    s.receiver = receiver;
    s.debit("OWNER_POSITION", s.amount, receiver).credit("COUNTERPARTY_RECEIVABLE", s.amount, null);
    return post(s, id -> {
      require("finance_counterparties", s.party, "COUNTERPARTY_NOT_FOUND");
      BigDecimal remaining = s.amount;
      var charges = jdbc.queryForList("SELECT c.id,c.amount FROM finance_counterparty_charges c JOIN finance_transactions t ON t.id=c.source_transaction_id WHERE c.counterparty_id=? AND t.status='POSTED' ORDER BY c.effective_date,c.id FOR UPDATE OF c", s.party);
      for (var charge : charges) {
        if (remaining.signum() == 0) break;
        UUID chargeId = (UUID) charge.get("id");
        BigDecimal paid = scalar("SELECT coalesce(sum(a.amount),0) FROM finance_counterparty_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.charge_id=? AND t.status='POSTED'", chargeId);
        BigDecimal available = ((BigDecimal) charge.get("amount")).subtract(paid);
        if (available.signum() <= 0) continue;
        BigDecimal allocated = remaining.min(available);
        jdbc.update("INSERT INTO finance_counterparty_payment_allocations(transaction_id,charge_id,amount) VALUES(?,?,?)", id, chargeId, allocated);
        remaining = remaining.subtract(allocated);
      }
      if (remaining.signum() > 0) throw ApiException.conflict("PARTY_RECEIPT_EXCEEDS_OUTSTANDING", "Receipt exceeds this party's amount outstanding.");
    });
  }

  @Transactional
  public Map<String, Object> invoice(FinanceCommands.Invoice input) {
    if (!Set.of("CGST_SGST", "IGST", "NONE", "CUSTOM").contains(input.taxMode()))
      throw ApiException.badRequest("INVALID_TAX_MODE", "Choose a supported tax mode.");
    BigDecimal base = nonnegative(input.baseAmount());
    BigDecimal cgstRate = nonnegative(input.cgstRate());
    BigDecimal sgstRate = nonnegative(input.sgstRate());
    BigDecimal igstRate = nonnegative(input.igstRate());
    if (("NONE".equals(input.taxMode()) && (cgstRate.signum() != 0 || sgstRate.signum() != 0 || igstRate.signum() != 0))
        || ("IGST".equals(input.taxMode()) && (cgstRate.signum() != 0 || sgstRate.signum() != 0))
        || ("CGST_SGST".equals(input.taxMode()) && igstRate.signum() != 0))
      throw ApiException.badRequest("TAX_MODE_RATE_MISMATCH", "The selected tax mode conflicts with its rates.");
    BigDecimal cgst = tax(base, cgstRate);
    BigDecimal sgst = tax(base, sgstRate);
    BigDecimal igst = tax(base, igstRate);
    BigDecimal total = base.add(cgst).add(sgst).add(igst);
    BigDecimal tds = nonnegative(input.tdsAmount());
    if (total.signum() <= 0 || tds.compareTo(total) > 0)
      throw ApiException.badRequest("INVALID_INVOICE_TOTAL", "Invoice total must be positive and TDS cannot exceed it.");
    Spec s = new Spec("INVOICE_ISSUED", total, input.invoiceDate(), "Invoice " + input.invoiceNumber(), input.idempotencyKey(), input);
    s.party = input.counterpartyId();
    s.production = input.productionId();
    s.debit("INVOICE_RECEIVABLE", total, null).credit("INVOICE_REVENUE", base, null);
    if (cgst.signum() > 0) s.credit("CGST_PAYABLE", cgst, null);
    if (sgst.signum() > 0) s.credit("SGST_PAYABLE", sgst, null);
    if (igst.signum() > 0) s.credit("IGST_PAYABLE", igst, null);
    return post(s, id -> {
      require("finance_counterparties", s.party, "COUNTERPARTY_NOT_FOUND");
      if (s.production != null) require("productions", s.production, "PRODUCTION_NOT_FOUND");
      jdbc.update("INSERT INTO finance_invoices(invoice_number,financial_year,invoice_date,counterparty_id,production_id,gstin,tax_mode,base_amount,cgst_rate,sgst_rate,igst_rate,cgst_amount,sgst_amount,igst_amount,invoice_total,tds_amount,source_transaction_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", input.invoiceNumber(), input.financialYear(), Date.valueOf(s.date), s.party, s.production, input.gstin(), input.taxMode(), base, cgstRate, sgstRate, igstRate, cgst, sgst, igst, total, tds, id);
    });
  }

  @Transactional
  public Map<String, Object> invoicePayment(FinanceCommands.InvoicePayment input) {
    UUID receiver = accountId(input.receiverAccount());
    Spec s = new Spec("INVOICE_PAYMENT", input.amount(), input.date(), input.description(), input.idempotencyKey(), input);
    s.invoice = input.invoiceId();
    var reference = one("SELECT counterparty_id,production_id FROM finance_invoices WHERE id=?", s.invoice, "INVOICE_NOT_FOUND");
    s.party = (UUID) reference.get("counterparty_id");
    s.production = (UUID) reference.get("production_id");
    s.receiver = receiver;
    s.debit("OWNER_POSITION", s.amount, receiver).credit("INVOICE_RECEIVABLE", s.amount, null);
    return post(s, id -> {
      var invoice = one("SELECT i.invoice_total,i.tds_amount,i.counterparty_id,i.production_id FROM finance_invoices i JOIN finance_transactions t ON t.id=i.source_transaction_id WHERE i.id=? AND t.status='POSTED' FOR UPDATE OF i", s.invoice, "INVOICE_NOT_FOUND");
      BigDecimal paid = scalar("SELECT coalesce(sum(a.amount),0) FROM finance_invoice_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.invoice_id=? AND t.status='POSTED'", s.invoice);
      BigDecimal available = ((BigDecimal) invoice.get("invoice_total")).subtract((BigDecimal) invoice.get("tds_amount")).subtract(paid);
      if (s.amount.compareTo(available) > 0) throw ApiException.conflict("INVOICE_PAYMENT_EXCEEDS_REMAINING", "Payment exceeds the invoice balance remaining.");
      jdbc.update("INSERT INTO finance_invoice_payment_allocations(transaction_id,invoice_id,amount) VALUES(?,?,?)", id, s.invoice, s.amount);
    });
  }

  @Transactional
  public Map<String, Object> purchase(FinanceCommands.Purchase input) {
    BigDecimal subtotal = nonnegative(input.subtotal());
    BigDecimal tax = nonnegative(input.tax());
    BigDecimal total = subtotal.add(tax);
    if (total.signum() <= 0) throw ApiException.badRequest("INVALID_PURCHASE_TOTAL", "Equipment purchase total must be positive.");
    Spec s = new Spec("EQUIPMENT_PURCHASE", total, input.date(), input.description(), input.idempotencyKey(), input);
    s.party = input.counterpartyId();
    s.debit("EQUIPMENT_ASSET", total, null).credit("EQUIPMENT_PAYABLE", total, null);
    return post(s, id -> {
      if (s.party != null) require("finance_counterparties", s.party, "COUNTERPARTY_NOT_FOUND");
      UUID purchaseId = UUID.randomUUID();
      jdbc.update("INSERT INTO finance_equipment_purchases(id,counterparty_id,purchase_date,reference,description,subtotal,tax,total,source_transaction_id) VALUES(?,?,?,?,?,?,?,?,?)", purchaseId, s.party, Date.valueOf(s.date), input.reference(), s.description, subtotal, tax, total, id);
      if (input.headquartersEquipmentId() != null) {
        require("hq_equipment", input.headquartersEquipmentId(), "EQUIPMENT_NOT_FOUND");
        if (input.quantity() == null || input.quantity().signum() <= 0)
          throw ApiException.badRequest("PURCHASE_QUANTITY_REQUIRED", "Enter the purchase item quantity for the Headquarters link.");
        jdbc.update("INSERT INTO finance_equipment_purchase_items(purchase_id,headquarters_equipment_id,description,quantity,unit_rate,tax,amount) VALUES(?,?,?,?,?,?,?)", purchaseId, input.headquartersEquipmentId(), s.description, input.quantity(), subtotal.divide(input.quantity(), 2, RoundingMode.HALF_UP), tax, total);
      }
    });
  }

  @Transactional
  public Map<String, Object> purchasePayment(FinanceCommands.PurchasePayment input) {
    UUID payer = accountId(input.payerAccount());
    Spec s = new Spec("EQUIPMENT_PAYMENT", input.amount(), input.date(), input.description(), input.idempotencyKey(), input);
    s.purchase = input.purchaseId();
    s.party = (UUID) one("SELECT counterparty_id FROM finance_equipment_purchases WHERE id=?", s.purchase, "PURCHASE_NOT_FOUND").get("counterparty_id");
    s.payer = payer;
    s.debit("EQUIPMENT_PAYABLE", s.amount, null).credit("OWNER_POSITION", s.amount, payer);
    return post(s, id -> {
      var purchase = one("SELECT p.total FROM finance_equipment_purchases p JOIN finance_transactions t ON t.id=p.source_transaction_id WHERE p.id=? AND t.status='POSTED' FOR UPDATE OF p", s.purchase, "PURCHASE_NOT_FOUND");
      BigDecimal paid = scalar("SELECT coalesce(sum(a.amount),0) FROM finance_equipment_payment_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.purchase_id=? AND t.status='POSTED'", s.purchase);
      if (paid.add(s.amount).compareTo((BigDecimal)purchase.get("total")) > 0)
        throw ApiException.conflict("EQUIPMENT_PAYMENT_EXCEEDS_REMAINING", "Payment exceeds the equipment purchase balance remaining.");
      jdbc.update("INSERT INTO finance_equipment_payment_allocations(transaction_id,purchase_id,amount) VALUES(?,?,?)", id, s.purchase, s.amount);
    });
  }

  @Transactional
  public Map<String, Object> ownerCredit(FinanceCommands.OwnerMovement input) {
    UUID receiver = accountId(input.receiverAccount());
    Spec s = new Spec("OWNER_CREDIT", input.amount(), input.date(), input.description(), input.idempotencyKey(), input);
    s.receiver = receiver;
    s.debit("OWNER_POSITION", s.amount, receiver).credit("OWNER_FUNDING", s.amount, null);
    return post(s, id -> {});
  }

  @Transactional
  public Map<String, Object> ownerTransfer(FinanceCommands.OwnerMovement input) {
    UUID payer = accountId(input.payerAccount());
    UUID receiver = accountId(input.receiverAccount());
    if (payer.equals(receiver)) throw ApiException.badRequest("SAME_ACCOUNT_TRANSFER", "Choose two different owner accounts.");
    Spec s = new Spec("OWNER_TRANSFER", input.amount(), input.date(), input.description(), input.idempotencyKey(), input);
    s.payer = payer;
    s.receiver = receiver;
    s.debit("OWNER_POSITION", s.amount, receiver).credit("OWNER_POSITION", s.amount, payer);
    return post(s, id -> {});
  }

  @Transactional
  public Map<String, Object> ownerDebit(FinanceCommands.OwnerMovement input) {
    UUID payer = accountId(input.payerAccount());
    Spec s = new Spec("OWNER_DEBIT", input.amount(), input.date(), input.description(), input.idempotencyKey(), input);
    s.payer = payer;
    s.debit("OWNER_DRAW", s.amount, null).credit("OWNER_POSITION", s.amount, payer);
    return post(s, id -> {});
  }

  @Transactional
  public Map<String, Object> reverse(UUID originalId, FinanceCommands.Reversal input) {
    var original = one("SELECT * FROM finance_transactions WHERE id=? FOR UPDATE", originalId, "TRANSACTION_NOT_FOUND");
    if ("REVERSED".equals(original.get("status"))) {
      var existing = jdbc.queryForList("SELECT id,idempotency_key FROM finance_transactions WHERE reversal_of=?", originalId);
      if (!existing.isEmpty() && input.idempotencyKey().equals(existing.getFirst().get("idempotency_key"))) return view((UUID) existing.getFirst().get("id"));
      throw ApiException.conflict("ALREADY_REVERSED", "This transaction was already reversed.");
    }
    if (!"POSTED".equals(original.get("status"))) throw ApiException.conflict("TRANSACTION_NOT_POSTED", "Only posted transactions can be reversed.");
    if ("PRODUCTION_CONTRACT".equals(original.get("transaction_type"))) {
      BigDecimal active = scalar("SELECT coalesce(sum(a.amount),0) FROM finance_production_receipt_allocations a JOIN finance_transactions t ON t.id=a.transaction_id WHERE a.production_id=? AND t.status='POSTED'", original.get("production_id"));
      if (active.signum() > 0) throw ApiException.conflict("CONTRACT_HAS_RECEIPTS", "Reverse the production receipts before reversing its contract.");
    }
    String type = (String) original.get("transaction_type");
    String allocationSql = switch (type) {
      case "EMPLOYEE_EARNING", "MONTHLY_SALARY_ACCRUAL" -> "SELECT count(*) FROM finance_employee_obligations o JOIN finance_employee_payment_allocations a ON a.obligation_id=o.id JOIN finance_transactions t ON t.id=a.transaction_id WHERE o.source_transaction_id=? AND t.status='POSTED'";
      case "COUNTERPARTY_CHARGE" -> "SELECT count(*) FROM finance_counterparty_charges c JOIN finance_counterparty_payment_allocations a ON a.charge_id=c.id JOIN finance_transactions t ON t.id=a.transaction_id WHERE c.source_transaction_id=? AND t.status='POSTED'";
      case "INVOICE_ISSUED" -> "SELECT count(*) FROM finance_invoices i JOIN finance_invoice_payment_allocations a ON a.invoice_id=i.id JOIN finance_transactions t ON t.id=a.transaction_id WHERE i.source_transaction_id=? AND t.status='POSTED'";
      case "EQUIPMENT_PURCHASE" -> "SELECT count(*) FROM finance_equipment_purchases p JOIN finance_equipment_payment_allocations a ON a.purchase_id=p.id JOIN finance_transactions t ON t.id=a.transaction_id WHERE p.source_transaction_id=? AND t.status='POSTED'";
      default -> null;
    };
    if (allocationSql != null && jdbc.queryForObject(allocationSql, Long.class, originalId) > 0)
      throw ApiException.conflict("REVERSAL_HAS_DEPENDENTS", "Reverse linked payments before reversing this obligation.");
    Spec s = new Spec("REVERSAL", (BigDecimal) original.get("amount"), LocalDate.now(), input.reason(), input.idempotencyKey(), Map.of("originalId", originalId, "reason", input.reason(), "idempotencyKey", input.idempotencyKey()));
    s.production = (UUID) original.get("production_id");
    s.employee = (UUID) original.get("employee_id");
    s.party = (UUID) original.get("counterparty_id");
    s.invoice = (UUID) original.get("invoice_id");
    s.purchase = (UUID) original.get("equipment_purchase_id");
    s.reversalOf = originalId;
    var lines = jdbc.queryForList("SELECT l.ledger_code,l.debit,l.credit,l.owner_account_id FROM finance_journal_lines l JOIN finance_journal_entries e ON e.id=l.entry_id WHERE e.transaction_id=? ORDER BY l.id", originalId);
    for (var line : lines) s.lines.add(new Line((String)line.get("ledger_code"), (BigDecimal)line.get("credit"), (BigDecimal)line.get("debit"), (UUID)line.get("owner_account_id")));
    return post(s, id -> {
      jdbc.update("UPDATE finance_transactions SET status='REVERSED' WHERE id=? AND status='POSTED'", originalId);
      if ("PRODUCTION_CONTRACT".equals(original.get("transaction_type"))) jdbc.update("DELETE FROM finance_production_profiles WHERE source_transaction_id=?", originalId);
    });
  }

  private Map<String, Object> post(Spec s, Consumer<UUID> domainEffect) {
    if (s.date == null || s.description == null || s.description.isBlank() || s.key == null)
      throw ApiException.badRequest("FINANCE_REQUIRED_FIELD", "Date, description and request ID are required.");
    if (s.lines.size() < 2) throw ApiException.badRequest("JOURNAL_LINES_REQUIRED", "The financial entry is incomplete.");
    BigDecimal debits = s.lines.stream().map(Line::debit).reduce(ZERO, BigDecimal::add);
    BigDecimal credits = s.lines.stream().map(Line::credit).reduce(ZERO, BigDecimal::add);
    if (debits.compareTo(credits) != 0) throw ApiException.badRequest("UNBALANCED_JOURNAL", "The financial entry is not balanced.");
    String payloadHash = hash(s.payload);
    jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", s.key.toString());
    var existing = jdbc.queryForList("SELECT id,payload_hash FROM finance_transactions WHERE idempotency_key=?", s.key);
    if (!existing.isEmpty()) {
      if (!payloadHash.equals(existing.getFirst().get("payload_hash")))
        throw ApiException.conflict("IDEMPOTENCY_CONFLICT", "This request ID was already used for a different financial action.");
      return view((UUID)existing.getFirst().get("id"));
    }
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO finance_transactions(id,transaction_type,status,effective_date,description,amount,
          production_id,employee_id,counterparty_id,invoice_id,equipment_purchase_id,payer_account_id,
          receiver_account_id,expense_category_id,legacy_type,idempotency_key,payload_hash,reversal_of,
          created_by,posted_by,posted_at)
        VALUES(?,?,'POSTED',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,now())
        """, id, s.type, Date.valueOf(s.date), s.description.trim(), s.amount, s.production, s.employee,
        s.party, s.invoice, s.purchase, s.payer, s.receiver, s.category, s.legacyType, s.key,
        payloadHash, s.reversalOf, actor(), actor());
    domainEffect.accept(id);
    UUID entry = UUID.randomUUID();
    jdbc.update("INSERT INTO finance_journal_entries(id,transaction_id) VALUES(?,?)", entry, id);
    for (Line line : s.lines) {
      jdbc.update("INSERT INTO finance_journal_lines(entry_id,ledger_code,debit,credit,owner_account_id,production_id,employee_id,counterparty_id,invoice_id,equipment_purchase_id) VALUES(?,?,?,?,?,?,?,?,?,?)", entry, line.code(), line.debit(), line.credit(), line.ownerAccount(), s.production, s.employee, s.party, s.invoice, s.purchase);
      if (line.ownerAccount() != null) jdbc.update("UPDATE finance_account_positions SET position=position+?,version=version+1,updated_at=now() WHERE account_id=?", line.debit().subtract(line.credit()), line.ownerAccount());
    }
    jdbc.update("INSERT INTO finance_audit_events(transaction_id,action,actor,reason,evidence) VALUES(?,'POSTED',?,?,?::jsonb)", id, actor(), s.description, auditEvidence(s));
    audit.record("FINANCE_TRANSACTION", "FINANCE_TRANSACTION_POSTED", id.toString(), null, Map.of("type", s.type, "amount", s.amount, "idempotencyKey", s.key));
    return view(id);
  }

  private String auditEvidence(Spec s) {
    try {
      return json.writeValueAsString(Map.of("type", s.type, "amount", s.amount.toPlainString(), "currency", "INR"));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Finance audit serialization failed", e);
    }
  }

  private String hash(Object payload) {
    try {
      byte[] encoded = json.writeValueAsBytes(payload);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
    } catch (JsonProcessingException | NoSuchAlgorithmException e) {
      throw new IllegalStateException("Finance command identity could not be calculated", e);
    }
  }

  private UUID accountId(String code) {
    if (code == null || !Set.of("AZ-2", "AK-2").contains(code))
      throw ApiException.badRequest("OWNER_ACCOUNT_REQUIRED", "Choose Azeem (AZ-2) or Akash (AK-2).");
    return jdbc.query("SELECT id FROM finance_accounts WHERE code=? AND active", (rs, row) -> rs.getObject(1, UUID.class), code)
        .stream().findFirst().orElseThrow(() -> ApiException.badRequest("OWNER_ACCOUNT_REQUIRED", "Choose an active owner account."));
  }

  private void require(String table, UUID id, String code) {
    if (id == null || jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id=?", Long.class, id) == 0)
      throw ApiException.notFound(code, "The related record was not found.");
  }

  private Map<String, Object> one(String sql, UUID id, String code) {
    return jdbc.queryForList(sql, id).stream().findFirst().orElseThrow(() -> ApiException.notFound(code, "The financial record was not found."));
  }

  private BigDecimal scalar(String sql, Object... args) {
    BigDecimal result = jdbc.queryForObject(sql, BigDecimal.class, args);
    return result == null ? ZERO : result;
  }

  private Map<String, Object> view(UUID id) {
    return jdbc.queryForMap("SELECT id,transaction_no \"transactionNo\",transaction_type \"type\",status,effective_date \"date\",description,amount,production_id \"productionId\",employee_id \"employeeId\",counterparty_id \"counterpartyId\",invoice_id \"invoiceId\",equipment_purchase_id \"equipmentPurchaseId\",payer_account_id \"payerAccountId\",receiver_account_id \"receiverAccountId\",reversal_of \"reversalOf\",posted_at \"postedAt\" FROM finance_transactions WHERE id=?", id);
  }

  private static BigDecimal scaled(BigDecimal value) {
    if (value == null || value.scale() > 2) throw ApiException.badRequest("INVALID_AMOUNT_PRECISION", "Money amounts need at most two decimal places.");
    return value.setScale(2, RoundingMode.UNNECESSARY);
  }

  private static BigDecimal money(BigDecimal value) {
    BigDecimal amount = scaled(value);
    if (amount.signum() <= 0) throw ApiException.badRequest("AMOUNT_NOT_POSITIVE", "Amount must be greater than zero.");
    return amount;
  }

  private static BigDecimal nonnegative(BigDecimal value) {
    BigDecimal amount = scaled(value);
    if (amount.signum() < 0) throw ApiException.badRequest("AMOUNT_NEGATIVE", "Amount cannot be negative.");
    return amount;
  }

  private static BigDecimal tax(BigDecimal base, BigDecimal rate) {
    if (rate.compareTo(new BigDecimal("100")) > 0) throw ApiException.badRequest("TAX_RATE_INVALID", "Tax rate cannot exceed 100%. ");
    return base.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
  }

  private static String actor() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth == null || auth.getName() == null ? "system" : auth.getName();
  }
}
