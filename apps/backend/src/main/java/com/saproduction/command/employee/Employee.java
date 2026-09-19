package com.saproduction.command.employee;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="employees")
public class Employee {
  public enum Status { ACTIVE, ON_LEAVE, INACTIVE }
  @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @Column(name="employee_code", nullable=false, unique=true) public String employeeCode;
  @Column(name="first_name", nullable=false) public String firstName;
  @Column(name="last_name") public String lastName;
  @Column(name="display_name", nullable=false) public String displayName;
  @Column(nullable=false) public String phone;
  @Column(name="whatsapp_phone") public String whatsappPhone;
  public String email;
  @Column(name="role_title", nullable=false) public String roleTitle;
  @Column(nullable=false) public String department;
  @Column(name="employment_type", nullable=false) public String employmentType;
  @Column(name="joining_date", nullable=false) public LocalDate joiningDate;
  @Column(name="base_salary_minor", nullable=false) public long baseSalaryMinor;
  @JdbcTypeCode(SqlTypes.CHAR) @Column(name="salary_currency", nullable=false, length=3) public String salaryCurrency;
  @Enumerated(EnumType.STRING) @Column(nullable=false) public Status status;
  @Column(name="profile_photo_url") public String profilePhotoUrl;
  public String notes;
  @Column(name="created_at", nullable=false) public Instant createdAt;
  @Column(name="updated_at", nullable=false) public Instant updatedAt;
  // Access relationships through methods so Hibernate can initialize lazy proxies.
  public UUID getId(){ return id; }
  public String getDisplayName(){ return displayName; }
  public void setStatus(Status value){ status=value; }
  @PrePersist void create(){ var now=Instant.now(); createdAt=now; updatedAt=now; if(status==null) status=Status.ACTIVE; if(salaryCurrency==null) salaryCurrency="INR"; }
  @PreUpdate void update(){ updatedAt=Instant.now(); }
}
