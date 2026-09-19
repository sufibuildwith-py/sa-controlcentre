package com.saproduction.command.employee;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;

public final class EmployeeDtos {
  private EmployeeDtos(){}
  public record Input(
    @NotBlank @Size(max=32) String employeeCode,
    @NotBlank @Size(max=80) String firstName,
    @Size(max=80) String lastName,
    @NotBlank @Size(max=160) String displayName,
    @NotBlank @Pattern(regexp="^[+0-9 ()-]{7,24}$") String phone,
    @Pattern(regexp="^$|^[+0-9 ()-]{7,24}$") String whatsappPhone,
    @Email @Size(max=254) String email,
    @NotBlank @Size(max=120) String roleTitle,
    @NotBlank @Size(max=100) String department,
    @NotBlank @Size(max=32) String employmentType,
    @NotNull @PastOrPresent LocalDate joiningDate,
    @PositiveOrZero long baseSalaryMinor,
    @NotBlank @Pattern(regexp="^[A-Z]{3}$") String salaryCurrency,
    Employee.Status status,
    @Size(max=1000) String profilePhotoUrl,
    @Size(max=4000) String notes) {}
  public record View(UUID id,String employeeCode,String firstName,String lastName,String displayName,String phone,String whatsappPhone,String email,String roleTitle,String department,String employmentType,LocalDate joiningDate,long baseSalaryMinor,String salaryCurrency,Employee.Status status,String profilePhotoUrl,String notes,Instant createdAt,Instant updatedAt) {}
  public static View view(Employee e){e=org.hibernate.Hibernate.unproxy(e,Employee.class);return new View(e.id,e.employeeCode,e.firstName,e.lastName,e.displayName,e.phone,e.whatsappPhone,e.email,e.roleTitle,e.department,e.employmentType,e.joiningDate,e.baseSalaryMinor,e.salaryCurrency,e.status,e.profilePhotoUrl,e.notes,e.createdAt,e.updatedAt);}
}
