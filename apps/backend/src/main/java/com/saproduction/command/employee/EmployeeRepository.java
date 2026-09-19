package com.saproduction.command.employee;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
  boolean existsByEmployeeCodeIgnoreCase(String code);
  @Query("select e from Employee e where (:status is null or e.status=:status) and (:search is null or lower(e.displayName) like lower(concat('%',:search,'%')) or lower(e.employeeCode) like lower(concat('%',:search,'%')) or lower(e.roleTitle) like lower(concat('%',:search,'%'))) order by e.displayName")
  List<Employee> search(@Param("search") String search, @Param("status") Employee.Status status);
  List<Employee> findAllByStatusNotOrderByDisplayName(Employee.Status status);
}

