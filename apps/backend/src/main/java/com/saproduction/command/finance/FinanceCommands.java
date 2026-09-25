package com.saproduction.command.finance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Owner-facing financial commands. Every posting request carries one stable retry identity. */
public final class FinanceCommands {
  private FinanceCommands() {}

  public record Contract(
      @NotNull UUID idempotencyKey,
      @NotNull UUID productionId,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
      @NotNull LocalDate date,
      @NotBlank @Size(max = 500) String description) {}

  public record Receipt(
      @NotNull UUID idempotencyKey,
      @NotNull UUID productionId,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
      @NotNull LocalDate date,
      @NotBlank @Size(max = 500) String description,
      @NotBlank String receiverAccount,
      UUID counterpartyId,
      String legacyType) {}

  public record Expense(
      @NotNull UUID idempotencyKey,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
      @NotNull LocalDate date,
      @NotBlank @Size(max = 500) String description,
      @NotBlank String payerAccount,
      UUID productionId,
      UUID counterpartyId,
      UUID employeeId,
      UUID equipmentId,
      String categoryCode) {}

  public record Earning(
      @NotNull UUID idempotencyKey,
      @NotNull UUID employeeId,
      UUID productionId,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
      @NotNull LocalDate date,
      @NotBlank @Size(max = 500) String description) {}

  public record Salary(
      @NotNull UUID idempotencyKey,
      @NotNull UUID employeeId,
      UUID payrollItemId,
      @NotNull @DecimalMin("0") BigDecimal gross,
      @NotNull @DecimalMin("0") BigDecimal approvedDeductions,
      @NotNull BigDecimal adjustment,
      @NotNull LocalDate date,
      @NotBlank @Size(max = 500) String description) {}

  public record EmployeePayment(
      @NotNull UUID idempotencyKey,
      @NotNull UUID employeeId,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
      @NotNull LocalDate date,
      @NotBlank @Size(max = 500) String description,
      @NotBlank String payerAccount) {}

  public record Counterparty(
      @NotBlank @Size(max = 180) String displayName,
      @NotBlank String role,
      String gstin) {}

  public record Charge(
      @NotNull UUID idempotencyKey,
      @NotNull UUID counterpartyId,
      UUID productionId,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
      @NotNull LocalDate date,
      @NotBlank @Size(max = 500) String description) {}

  public record PartyReceipt(
      @NotNull UUID idempotencyKey,
      @NotNull UUID counterpartyId,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
      @NotNull LocalDate date,
      @NotBlank @Size(max = 500) String description,
      @NotBlank String receiverAccount) {}

  public record Invoice(
      @NotNull UUID idempotencyKey,
      @NotBlank @Size(max = 100) String invoiceNumber,
      @NotBlank String financialYear,
      @NotNull LocalDate invoiceDate,
      @NotNull UUID counterpartyId,
      UUID productionId,
      String gstin,
      @NotBlank String taxMode,
      @NotNull @DecimalMin("0") BigDecimal baseAmount,
      @NotNull @DecimalMin("0") BigDecimal cgstRate,
      @NotNull @DecimalMin("0") BigDecimal sgstRate,
      @NotNull @DecimalMin("0") BigDecimal igstRate,
      @NotNull @DecimalMin("0") BigDecimal tdsAmount) {}

  public record InvoicePayment(
      @NotNull UUID idempotencyKey,
      @NotNull UUID invoiceId,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
      @NotNull LocalDate date,
      @NotBlank @Size(max = 500) String description,
      @NotBlank String receiverAccount) {}

  public record Purchase(
      @NotNull UUID idempotencyKey,
      UUID counterpartyId,
      @NotNull LocalDate date,
      String reference,
      @NotBlank @Size(max = 500) String description,
      @NotNull @DecimalMin("0") BigDecimal subtotal,
      @NotNull @DecimalMin("0") BigDecimal tax,
      UUID headquartersEquipmentId,
      @DecimalMin(value = "0", inclusive = false) BigDecimal quantity) {}

  public record PurchasePayment(
      @NotNull UUID idempotencyKey,
      @NotNull UUID purchaseId,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
      @NotNull LocalDate date,
      @NotBlank @Size(max = 500) String description,
      @NotBlank String payerAccount) {}

  public record OwnerMovement(
      @NotNull UUID idempotencyKey,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
      @NotNull LocalDate date,
      @NotBlank @Size(max = 500) String description,
      String payerAccount,
      String receiverAccount) {}

  public record Reversal(
      @NotNull UUID idempotencyKey, @NotBlank @Size(max = 500) String reason) {}
}
