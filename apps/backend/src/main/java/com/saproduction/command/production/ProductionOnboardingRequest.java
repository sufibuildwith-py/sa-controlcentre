package com.saproduction.command.production;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record ProductionOnboardingRequest(
    @NotBlank @Size(max = 180) String title,
    @NotBlank @Size(max = 180) String clientName,
    @Size(max = 4000) String description,
    @NotNull @JsonFormat(pattern = "[yyyy-MM-dd][dd-MM-yyyy]") LocalDate eventDate,
    LocalTime startTime,
    LocalTime endTime,
    @NotBlank @Size(max = 180) String venueName,
    @Size(max = 500) String venueAddress,
    Production.Priority priority,
    @Min(0) @Max(100) Integer progressPercent,
    List<ProductionService.MemberInput> crew,
    List<ProductionService.TaskInput> tasks,
    List<ProductionService.EquipmentInput> equipment,
    @NotNull @Valid ContractInput contract,
    @Valid AdvanceInput advance) {

  public record ContractInput(
      @NotNull UUID idempotencyKey,
      @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
      LocalDate effectiveDate,
      @Size(max = 500) String description) {}

  public record AdvanceInput(
      UUID idempotencyKey,
      @DecimalMin(value = "0") BigDecimal amount,
      LocalDate effectiveDate,
      String receiverAccount,
      @Size(max = 500) String description) {}

  public ProductionService.CreateInput toCreateInput() {
    return new ProductionService.CreateInput(
        title,
        clientName,
        description,
        eventDate,
        startTime,
        endTime,
        venueName,
        venueAddress,
        priority,
        progressPercent,
        crew,
        tasks,
        equipment);
  }
}
