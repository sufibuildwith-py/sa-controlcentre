package com.saproduction.command.eve;

import com.saproduction.command.finance.FinanceReadService;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verification context provided to EveCommandDefinition for authoritative post-execution verification.
 */
public record EveVerificationContext(
    UUID sessionId,
    UUID planId,
    UUID canonicalRecordId,
    FinanceReadService financeReadService,
    JdbcTemplate jdbc) {}
