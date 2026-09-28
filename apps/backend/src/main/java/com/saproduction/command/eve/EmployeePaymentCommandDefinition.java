package com.saproduction.command.eve;

import com.saproduction.command.finance.FinanceCommands;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Authoritative command definition for RECORD_EMPLOYEE_PAYMENT.
 * Dispatches to canonical FinancePostingService.employeePayment.
 * Enforces:
 * - Positive amount, scale <= 2
 * - Explicit owner account (AZ-2 or AK-2)
 * - Active employee existence
 * - Fresh outstanding payable balance check (earned - paid >= amount)
 * - Deterministic idempotency key derived from plan identity
 * - Post-execution authoritative verification against PostgreSQL
 */
@Component
public class EmployeePaymentCommandDefinition implements EveCommandDefinition {

  public static final String COMMAND_TYPE = "RECORD_EMPLOYEE_PAYMENT";

  @Override
  public String commandType() {
    return COMMAND_TYPE;
  }

  @Override
  public String domain() {
    return "FINANCE";
  }

  @Override
  public String description() {
    return "Post an employee payment from an owner cash/bank position to settle obligations";
  }

  @Override
  public EveDtos.RiskTier riskTier() {
    return EveDtos.RiskTier.FINANCIAL_WRITE;
  }

  @Override
  public EveDtos.ConfirmationPolicy confirmationPolicy() {
    return EveDtos.ConfirmationPolicy.CONFIRMATION_REQUIRED;
  }

  @Override
  public void validateSchema(Map<String, Object> parameters) {
    if (parameters == null) {
      throw ApiException.badRequest("INVALID_PARAMETERS", "Command parameters cannot be null.");
    }

    // 1. Employee ID
    Object empIdObj = parameters.get("employeeId");
    if (empIdObj == null || empIdObj.toString().isBlank()) {
      throw ApiException.badRequest("EMPLOYEE_REQUIRED", "Employee ID is required.");
    }
    try {
      UUID.fromString(empIdObj.toString());
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("INVALID_EMPLOYEE_ID", "Employee ID must be a valid UUID.");
    }

    // 2. Amount
    Object amtObj = parameters.get("amount");
    if (amtObj == null || amtObj.toString().isBlank()) {
      throw ApiException.badRequest("AMOUNT_REQUIRED", "Payment amount is required.");
    }
    BigDecimal amount;
    try {
      amount = new BigDecimal(amtObj.toString());
    } catch (NumberFormatException e) {
      throw ApiException.badRequest("INVALID_AMOUNT", "Amount must be a valid decimal number.");
    }
    if (amount.scale() > 2) {
      throw ApiException.badRequest("INVALID_AMOUNT_PRECISION", "Money amounts need at most two decimal places.");
    }
    if (amount.signum() <= 0) {
      throw ApiException.badRequest("AMOUNT_NOT_POSITIVE", "Payment amount must be greater than zero.");
    }

    // 3. Payer Account
    Object payerObj = parameters.get("payerAccount");
    if (payerObj == null || payerObj.toString().isBlank()) {
      throw ApiException.badRequest("OWNER_ACCOUNT_REQUIRED", "Payer owner account is required (AZ-2 or AK-2).");
    }
    String payerCode = payerObj.toString().trim();
    if (!Set.of("AZ-2", "AK-2").contains(payerCode)) {
      throw ApiException.badRequest("OWNER_ACCOUNT_REQUIRED", "Choose Azeem (AZ-2) or Akash (AK-2).");
    }

    // 4. Date (optional, defaults to today)
    if (parameters.containsKey("date") && parameters.get("date") != null && !parameters.get("date").toString().isBlank()) {
      try {
        LocalDate.parse(parameters.get("date").toString().trim());
      } catch (DateTimeParseException e) {
        throw ApiException.badRequest("INVALID_DATE", "Date must be formatted as YYYY-MM-DD.");
      }
    }

    // 5. Description (optional, max 500)
    if (parameters.containsKey("description") && parameters.get("description") != null) {
      if (parameters.get("description").toString().length() > 500) {
        throw ApiException.badRequest("DESCRIPTION_TOO_LONG", "Description cannot exceed 500 characters.");
      }
    }
  }

  @Override
  public void validateDomain(EveDtos.EvePlanAction action, EveExecutionContext context) {
    Map<String, Object> params = action.parameters();
    UUID employeeId = UUID.fromString(params.get("employeeId").toString());
    String payerAccount = params.get("payerAccount").toString().trim();
    BigDecimal amount = new BigDecimal(params.get("amount").toString()).setScale(2, RoundingMode.UNNECESSARY);

    // 1. Employee existence in canonical DB
    Integer empCount = context.jdbc().queryForObject(
        "SELECT count(*) FROM employees WHERE id = ? AND status = 'ACTIVE'",
        Integer.class,
        employeeId);
    if (empCount == null || empCount == 0) {
      throw ApiException.notFound("EMPLOYEE_NOT_FOUND", "Active employee record was not found.");
    }

    // 2. Owner account existence in canonical DB
    Integer accCount = context.jdbc().queryForObject(
        "SELECT count(*) FROM finance_accounts WHERE code = ? AND active",
        Integer.class,
        payerAccount);
    if (accCount == null || accCount == 0) {
      throw ApiException.badRequest("OWNER_ACCOUNT_REQUIRED", "Owner account " + payerAccount + " was not found or is inactive.");
    }

    // 3. Authoritative fresh balance check
    Map<String, Object> empFinance = context.financeReadService().employee(employeeId);
    BigDecimal outstanding = (BigDecimal) empFinance.get("outstanding");
    if (outstanding == null || outstanding.signum() <= 0) {
      throw ApiException.badRequest("NO_OUTSTANDING_BALANCE", "Employee has no pending payable balance.");
    }
    if (amount.compareTo(outstanding) > 0) {
      throw ApiException.conflict(
          "PAYMENT_EXCEEDS_OUTSTANDING",
          "Payment of ₹" + amount.toPlainString() + " exceeds employee outstanding balance of ₹" + outstanding.toPlainString() + ".");
    }
  }

  @Override
  public EveDtos.EveCommandResult execute(EveDtos.EvePlanAction action, EveExecutionContext context) {
    Map<String, Object> params = action.parameters();
    UUID employeeId = UUID.fromString(params.get("employeeId").toString());
    String payerAccount = params.get("payerAccount").toString().trim();
    BigDecimal amount = new BigDecimal(params.get("amount").toString()).setScale(2, RoundingMode.UNNECESSARY);

    LocalDate date = LocalDate.now();
    if (params.containsKey("date") && params.get("date") != null && !params.get("date").toString().isBlank()) {
      date = LocalDate.parse(params.get("date").toString().trim());
    }

    String description = "Payment via EVE";
    if (params.containsKey("description") && params.get("description") != null && !params.get("description").toString().isBlank()) {
      description = params.get("description").toString().trim();
    }

    // Deterministic idempotency key derived from plan + action seq + hash
    UUID idempotencyKey = UUID.nameUUIDFromBytes(
        (context.planId() + ":" + action.seq() + ":" + context.planHash()).getBytes(StandardCharsets.UTF_8));

    FinanceCommands.EmployeePayment cmd = new FinanceCommands.EmployeePayment(
        idempotencyKey,
        employeeId,
        amount,
        date,
        description,
        payerAccount);

    Map<String, Object> postResult = context.financePostingService().employeePayment(cmd);
    UUID transactionId = (UUID) postResult.get("id");
    String txnNo = postResult.get("transactionNo") != null ? postResult.get("transactionNo").toString() : "N/A";

    return new EveDtos.EveCommandResult(
        COMMAND_TYPE,
        "EXECUTED",
        java.time.Instant.now(),
        transactionId,
        "Payment of ₹" + amount.toPlainString() + " posted as transaction " + txnNo,
        idempotencyKey.toString());
  }

  @Override
  public EveDtos.EveVerificationResult verify(
      EveDtos.EvePlanAction action,
      EveDtos.EveCommandResult executionResult,
      EveVerificationContext context) {
    UUID transactionId = executionResult.canonicalRecordId();
    if (transactionId == null) {
      return new EveDtos.EveVerificationResult(
          "FAILED",
          "EMPLOYEE_PAYMENT_VERIFICATION",
          "Transaction ID present",
          "Null transaction ID",
          java.time.Instant.now(),
          "No transaction ID returned from execution.");
    }

    // 1. Transaction verification in finance_transactions
    List<Map<String, Object>> txns = context.jdbc().queryForList(
        "SELECT id, transaction_no, transaction_type, status, amount, employee_id, payer_account_id, idempotency_key FROM finance_transactions WHERE id = ?",
        transactionId);
    if (txns.isEmpty()) {
      return new EveDtos.EveVerificationResult(
          "FAILED",
          "EMPLOYEE_PAYMENT_VERIFICATION",
          "Transaction record in PostgreSQL",
          "Record not found",
          java.time.Instant.now(),
          "Transaction " + transactionId + " was not found in finance_transactions table.");
    }

    Map<String, Object> txn = txns.getFirst();

    // Verify transaction type
    String txType = (String) txn.get("transaction_type");
    if (!"EMPLOYEE_PAYMENT".equals(txType)) {
      return new EveDtos.EveVerificationResult(
          "FAILED",
          "EMPLOYEE_PAYMENT_VERIFICATION",
          "Transaction type EMPLOYEE_PAYMENT",
          "Transaction type " + txType,
          java.time.Instant.now(),
          "Transaction type mismatch.");
    }

    // Verify status
    String status = (String) txn.get("status");
    if (!"POSTED".equals(status)) {
      return new EveDtos.EveVerificationResult(
          "FAILED",
          "EMPLOYEE_PAYMENT_VERIFICATION",
          "Status POSTED",
          "Status " + status,
          java.time.Instant.now(),
          "Transaction status is not POSTED.");
    }

    // Verify exact amount
    BigDecimal expectedAmount = new BigDecimal(action.parameters().get("amount").toString()).setScale(2, RoundingMode.UNNECESSARY);
    BigDecimal actualAmount = (BigDecimal) txn.get("amount");
    if (actualAmount == null || actualAmount.compareTo(expectedAmount) != 0) {
      return new EveDtos.EveVerificationResult(
          "FAILED",
          "EMPLOYEE_PAYMENT_VERIFICATION",
          "Amount ₹" + expectedAmount.toPlainString(),
          "Amount ₹" + (actualAmount != null ? actualAmount.toPlainString() : "null"),
          java.time.Instant.now(),
          "Transaction amount does not match expected payment amount.");
    }

    // Verify exact employee binding
    UUID expectedEmployeeId = UUID.fromString(action.parameters().get("employeeId").toString());
    UUID actualEmployeeId = (UUID) txn.get("employee_id");
    if (!expectedEmployeeId.equals(actualEmployeeId)) {
      return new EveDtos.EveVerificationResult(
          "FAILED",
          "EMPLOYEE_PAYMENT_VERIFICATION",
          "Employee " + expectedEmployeeId,
          "Employee " + actualEmployeeId,
          java.time.Instant.now(),
          "Transaction is associated with wrong employee.");
    }

    // Verify exact payer account binding
    String payerCode = action.parameters().get("payerAccount").toString().trim();
    List<UUID> payerAccIds = context.jdbc().query(
        "SELECT id FROM finance_accounts WHERE code = ?",
        (rs, rowNum) -> rs.getObject("id", UUID.class),
        payerCode);
    if (payerAccIds.isEmpty()) {
      return new EveDtos.EveVerificationResult(
          "FAILED",
          "EMPLOYEE_PAYMENT_VERIFICATION",
          "Payer account " + payerCode,
          "Payer account not found",
          java.time.Instant.now(),
          "Payer account record not found in PostgreSQL.");
    }
    UUID expectedPayerAccId = payerAccIds.getFirst();
    UUID actualPayerAccId = (UUID) txn.get("payer_account_id");
    if (!expectedPayerAccId.equals(actualPayerAccId)) {
      return new EveDtos.EveVerificationResult(
          "FAILED",
          "EMPLOYEE_PAYMENT_VERIFICATION",
          "Payer account " + expectedPayerAccId,
          "Payer account " + actualPayerAccId,
          java.time.Instant.now(),
          "Transaction debited wrong owner account.");
    }

    // 2. Allocation verification (double-entry integrity)
    BigDecimal allocated = context.jdbc().queryForObject(
        "SELECT coalesce(sum(amount),0) FROM finance_employee_payment_allocations WHERE transaction_id = ?",
        BigDecimal.class,
        transactionId);
    if (allocated == null || allocated.compareTo(expectedAmount) != 0) {
      return new EveDtos.EveVerificationResult(
          "FAILED",
          "EMPLOYEE_PAYMENT_VERIFICATION",
          "Allocated ₹" + expectedAmount.toPlainString(),
          "Allocated ₹" + (allocated != null ? allocated.toPlainString() : "0"),
          java.time.Instant.now(),
          "Allocation amount does not equal payment amount.");
    }

    // Verify allocations belong exclusively to target employee
    Integer foreignObligations = context.jdbc().queryForObject(
        """
        SELECT count(*)
        FROM finance_employee_payment_allocations a
        JOIN finance_employee_obligations o ON o.id = a.obligation_id
        WHERE a.transaction_id = ? AND o.employee_id != ?
        """,
        Integer.class,
        transactionId,
        expectedEmployeeId);
    if (foreignObligations != null && foreignObligations > 0) {
      return new EveDtos.EveVerificationResult(
          "FAILED",
          "EMPLOYEE_PAYMENT_VERIFICATION",
          "0 foreign allocations",
          foreignObligations + " foreign allocations",
          java.time.Instant.now(),
          "Payment allocated to obligations belonging to a different employee.");
    }

    // 3. Audit trail verification
    Integer auditCount = context.jdbc().queryForObject(
        "SELECT count(*) FROM finance_audit_events WHERE transaction_id = ? AND action = 'POSTED'",
        Integer.class,
        transactionId);
    if (auditCount == null || auditCount == 0) {
      return new EveDtos.EveVerificationResult(
          "FAILED",
          "EMPLOYEE_PAYMENT_VERIFICATION",
          "Finance audit event recorded",
          "No audit event",
          java.time.Instant.now(),
          "No finance audit event recorded for transaction.");
    }

    String txnNo = txn.get("transaction_no") != null ? txn.get("transaction_no").toString() : transactionId.toString();
    return new EveDtos.EveVerificationResult(
        "VERIFIED",
        "EMPLOYEE_PAYMENT_VERIFICATION",
        "Transaction " + txnNo + " POSTED for ₹" + expectedAmount.toPlainString() + " with allocations and audit",
        "Authoritative PostgreSQL state verified: POSTED, allocated ₹" + allocated.toPlainString(),
        java.time.Instant.now(),
        "Double-entry integrity, employee attribution, payer attribution and audit record verified in PostgreSQL system of record.");
  }
}
