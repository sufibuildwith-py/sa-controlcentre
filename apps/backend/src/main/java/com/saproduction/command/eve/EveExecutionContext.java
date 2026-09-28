package com.saproduction.command.eve;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.finance.FinancePostingService;
import com.saproduction.command.finance.FinanceReadService;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Execution context provided to EveCommandDefinition during canonical execution.
 * Encapsulates the plan identity, security actor, and canonical services.
 */
public record EveExecutionContext(
    UUID sessionId,
    UUID planId,
    int planVersion,
    String planHash,
    String operator,
    FinancePostingService financePostingService,
    FinanceReadService financeReadService,
    JdbcTemplate jdbc,
    ObjectMapper json) {}
