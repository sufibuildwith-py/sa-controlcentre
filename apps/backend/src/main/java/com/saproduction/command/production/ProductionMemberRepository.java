package com.saproduction.command.production;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionMemberRepository extends JpaRepository<ProductionMember, UUID> {
  List<ProductionMember> findAllByProductionIdOrderByCreatedAt(UUID productionId);

  boolean existsByProductionIdAndEmployeeId(UUID productionId, UUID employeeId);

  Optional<ProductionMember> findByProductionIdAndEmployeeId(UUID productionId, UUID employeeId);

  long countByEmployeeIdAndAssignmentStatusNot(UUID employeeId, ProductionMember.Status status);
}
