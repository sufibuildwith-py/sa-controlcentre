package com.saproduction.command.audit;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditLog, UUID> {List<AuditLog> findAllByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType,String entityId);}
