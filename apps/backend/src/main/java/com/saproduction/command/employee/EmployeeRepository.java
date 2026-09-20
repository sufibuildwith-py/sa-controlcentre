package com.saproduction.command.employee;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface EmployeeRepository
    extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {
  boolean existsByEmployeeCodeIgnoreCase(String code);

  Optional<Employee> findByEmployeeCodeIgnoreCase(String code);

  List<Employee> findAllByStatusNotOrderByDisplayName(Employee.Status status);
}
