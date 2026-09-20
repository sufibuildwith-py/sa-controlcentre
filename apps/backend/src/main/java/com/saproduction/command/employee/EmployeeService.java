package com.saproduction.command.employee;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.communication.DomainEventService;
import com.saproduction.command.shared.ApiException;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {
  private final EmployeeRepository employees; private final AuditService audit;private final DomainEventService events;
  public EmployeeService(EmployeeRepository employees,AuditService audit,DomainEventService events){this.employees=employees;this.audit=audit;this.events=events;}
  @Transactional(readOnly=true) public List<EmployeeDtos.View> list(String search,Employee.Status status){
    return employees.findAll((root,query,cb)->{
      List<Predicate> filters=new ArrayList<>();
      if(status!=null) filters.add(cb.equal(root.get("status"),status));
      if(!blank(search)){
        String pattern="%"+search.trim().toLowerCase(Locale.ROOT)+"%";
        filters.add(cb.or(
          cb.like(cb.lower(root.get("displayName")),pattern),
          cb.like(cb.lower(root.get("employeeCode")),pattern),
          cb.like(cb.lower(root.get("roleTitle")),pattern)
        ));
      }
      return cb.and(filters.toArray(Predicate[]::new));
    },Sort.by(Sort.Direction.ASC,"displayName")).stream().map(EmployeeDtos::view).toList();
  }
  @Transactional(readOnly=true) public Employee getEntity(UUID id){ return org.hibernate.Hibernate.unproxy(employees.findById(id).orElseThrow(()->ApiException.notFound("EMPLOYEE_NOT_FOUND","Employee was not found.")),Employee.class); }
  @Transactional(readOnly=true) public EmployeeDtos.View get(UUID id){return EmployeeDtos.view(getEntity(id));}
  @Transactional public EmployeeDtos.View create(EmployeeDtos.Input in){
    if(employees.existsByEmployeeCodeIgnoreCase(in.employeeCode())) throw ApiException.conflict("EMPLOYEE_CODE_EXISTS","Employee code is already in use.");
    Employee e=new Employee(); apply(e,in); try{employees.saveAndFlush(e);}catch(DataIntegrityViolationException ex){throw ApiException.conflict("EMPLOYEE_CONFLICT","Employee details conflict with an existing record.");} audit.record("EMPLOYEE","EMPLOYEE_CREATED",e.id.toString(),null,EmployeeDtos.view(e));events.emit("EMPLOYEE_CREATED","EMPLOYEE",e.id,Map.of("employeeId",e.id,"displayName",e.displayName)); return EmployeeDtos.view(e);
  }
  @Transactional public EmployeeDtos.View update(UUID id,EmployeeDtos.Input in){ Employee e=getEntity(id); EmployeeDtos.View before=EmployeeDtos.view(e); if(!e.employeeCode.equalsIgnoreCase(in.employeeCode())&&employees.existsByEmployeeCodeIgnoreCase(in.employeeCode())) throw ApiException.conflict("EMPLOYEE_CODE_EXISTS","Employee code is already in use."); long salary=e.baseSalaryMinor; apply(e,in); employees.saveAndFlush(e); var after=EmployeeDtos.view(e); audit.record("EMPLOYEE","EMPLOYEE_EDITED",id.toString(),before,after); if(salary!=e.baseSalaryMinor) audit.record("EMPLOYEE","SALARY_BASIS_CHANGED",id.toString(),salary,e.baseSalaryMinor); return after; }
  @Transactional public EmployeeDtos.View deactivate(UUID id){Employee e=getEntity(id);var before=EmployeeDtos.view(e);e.status=Employee.Status.INACTIVE;employees.save(e);var after=EmployeeDtos.view(e);audit.record("EMPLOYEE","EMPLOYEE_DEACTIVATED",id.toString(),before,after);return after;}
  private void apply(Employee e,EmployeeDtos.Input in){e.employeeCode=in.employeeCode().trim();e.firstName=in.firstName().trim();e.lastName=clean(in.lastName());e.displayName=in.displayName().trim();e.phone=in.phone().trim();e.whatsappPhone=clean(in.whatsappPhone());e.email=clean(in.email());e.roleTitle=in.roleTitle().trim();e.department=in.department().trim();e.employmentType=in.employmentType().trim();e.joiningDate=in.joiningDate();e.baseSalaryMinor=in.baseSalaryMinor();e.salaryCurrency=in.salaryCurrency();e.status=in.status()==null?Employee.Status.ACTIVE:in.status();e.profilePhotoUrl=clean(in.profilePhotoUrl());e.notes=clean(in.notes());}
  private static String clean(String s){return blank(s)?null:s.trim();} private static boolean blank(String s){return s==null||s.isBlank();}
}
