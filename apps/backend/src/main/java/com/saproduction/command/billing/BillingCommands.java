package com.saproduction.command.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BillingCommands {
  private BillingCommands() {}

  public record Line(
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal days,
      @NotBlank @Size(max = 500) String description,
      @NotNull @DecimalMin("0") BigDecimal rate,
      @Size(max = 180) String reference) {}

  public record Create(
      @NotBlank @Size(max = 100) String billNumber,
      @NotNull LocalDate billDate,
      @NotBlank @Size(max = 20) String financialYear,
      @NotNull UUID counterpartyId,
      UUID productionId,
      @Size(max = 240) String eventName,
      @Size(max = 240) String venue,
      @NotBlank String taxMode,
      @NotNull @DecimalMin("0") BigDecimal cgstRate,
      @NotNull @DecimalMin("0") BigDecimal sgstRate,
      @NotNull @DecimalMin("0") BigDecimal igstRate,
      @Size(max = 40) String gstin,
      @NotNull @DecimalMin("0") BigDecimal discount,
      @NotNull @DecimalMin("0") BigDecimal freight,
      @NotNull @DecimalMin("0") BigDecimal advancePaid,
      @Size(max = 5000) String notes,
      @Size(max = 500) String paymentTerms,
      @NotNull @Size(min = 1, max = 100) List<@Valid Line> lines) {}

  public record Issue(@NotNull UUID idempotencyKey) {}
}
