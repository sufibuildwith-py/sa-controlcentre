package com.saproduction.command.audit;

import java.time.Instant;

/**
 * Emitted after domain mutations for audit and continuous intelligence observation.
 */
public record DomainMutationEvent(
    String entityType,
    String action,
    String entityId,
    String actorId,
    Object before,
    Object after,
    Instant occurredAt
) {}
