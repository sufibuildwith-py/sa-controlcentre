package com.saproduction.command.production;

import com.saproduction.command.finance.FinanceCommands;
import com.saproduction.command.finance.FinancePostingService;
import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application-level orchestrator for atomic production onboarding.
 * Coordinates operational production creation with canonical finance contracts and advances.
 */
@Service
public class ProductionOnboardingService {
  private final ProductionService productionService;
  private final FinancePostingService postingService;
  private final JdbcTemplate jdbc;

  public ProductionOnboardingService(
      ProductionService productionService,
      FinancePostingService postingService,
      JdbcTemplate jdbc) {
    this.productionService = productionService;
    this.postingService = postingService;
    this.jdbc = jdbc;
  }

  @Transactional
  public ProductionService.View onboard(ProductionOnboardingRequest input) {
    if (input.contract() == null
        || input.contract().amount() == null
        || input.contract().amount().compareTo(BigDecimal.ZERO) <= 0) {
      throw ApiException.badRequest(
          "CONTRACT_REQUIRED", "A positive contract amount is required to create a production.");
    }

    BigDecimal contractAmount = input.contract().amount();
    BigDecimal advanceAmount =
        (input.advance() != null && input.advance().amount() != null)
            ? input.advance().amount()
            : BigDecimal.ZERO;

    if (advanceAmount.compareTo(BigDecimal.ZERO) < 0) {
      throw ApiException.badRequest("INVALID_ADVANCE_AMOUNT", "Advance amount cannot be negative.");
    }

    if (advanceAmount.compareTo(contractAmount) > 0) {
      throw ApiException.badRequest(
          "ADVANCE_EXCEEDS_CONTRACT", "Advance amount cannot exceed the contract amount.");
    }

    if (advanceAmount.compareTo(BigDecimal.ZERO) > 0) {
      if (input.advance().receiverAccount() == null || input.advance().receiverAccount().isBlank()) {
        throw ApiException.badRequest(
            "RECEIVER_ACCOUNT_REQUIRED", "Choose an owner receiver account for the advance.");
      }
    }

    UUID contractKey = input.contract().idempotencyKey();
    if (contractKey == null) {
      throw ApiException.badRequest("FINANCE_REQUIRED_FIELD", "Contract request ID is required.");
    }

    // Acquire lock on contract idempotency key for race prevention
    jdbc.queryForList(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))", contractKey.toString());

    // Check if contract with this idempotency key already exists
    var existingContract =
        jdbc.queryForList(
            "SELECT id, production_id FROM finance_transactions WHERE idempotency_key = ?",
            contractKey);
    if (!existingContract.isEmpty()) {
      UUID existingProdId = (UUID) existingContract.getFirst().get("production_id");
      if (existingProdId != null) {
        // Replay advance if requested and not yet posted
        if (advanceAmount.compareTo(BigDecimal.ZERO) > 0) {
          UUID advanceKey =
              (input.advance().idempotencyKey() != null)
                  ? input.advance().idempotencyKey()
                  : UUID.nameUUIDFromBytes((contractKey.toString() + ":advance").getBytes());
          LocalDate advanceDate =
              input.advance().effectiveDate() != null
                  ? input.advance().effectiveDate()
                  : input.eventDate();
          String advanceDesc =
              (input.advance().description() != null && !input.advance().description().isBlank())
                  ? input.advance().description().trim()
                  : "Client advance for " + input.title().trim();
          postingService.receipt(
              new FinanceCommands.Receipt(
                  advanceKey,
                  existingProdId,
                  advanceAmount,
                  advanceDate,
                  advanceDesc,
                  input.advance().receiverAccount(),
                  null,
                  "ADD"));
        }
        return productionService.get(existingProdId);
      }
    }

    // 1. Create operational production
    ProductionService.View prod = productionService.create(input.toCreateInput());

    // 2. Post canonical contract
    LocalDate contractDate =
        input.contract().effectiveDate() != null
            ? input.contract().effectiveDate()
            : input.eventDate();
    String contractDesc =
        (input.contract().description() != null && !input.contract().description().isBlank())
            ? input.contract().description().trim()
            : "Contract for " + input.title().trim();

    postingService.contract(
        new FinanceCommands.Contract(
            contractKey, prod.id(), contractAmount, contractDate, contractDesc));

    // 3. Post canonical receipt (advance) if advance > 0
    if (advanceAmount.compareTo(BigDecimal.ZERO) > 0) {
      UUID advanceKey =
          (input.advance().idempotencyKey() != null)
              ? input.advance().idempotencyKey()
              : UUID.nameUUIDFromBytes((contractKey.toString() + ":advance").getBytes());
      LocalDate advanceDate =
          input.advance().effectiveDate() != null
              ? input.advance().effectiveDate()
              : input.eventDate();
      String advanceDesc =
          (input.advance().description() != null && !input.advance().description().isBlank())
              ? input.advance().description().trim()
              : "Client advance for " + input.title().trim();

      postingService.receipt(
          new FinanceCommands.Receipt(
              advanceKey,
              prod.id(),
              advanceAmount,
              advanceDate,
              advanceDesc,
              input.advance().receiverAccount(),
              null,
              "ADD"));
    }

    return productionService.get(prod.id());
  }
}
