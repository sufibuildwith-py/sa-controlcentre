package com.saproduction.command.attendance;

import com.saproduction.command.employee.Employee;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="attendance_records", uniqueConstraints=@UniqueConstraint(name="attendance_employee_date_uk", columnNames={"employee_id","attendance_date"}))
public class AttendanceRecord {
  public enum Status { PRESENT, ABSENT, LATE, HALF_DAY, LEAVE, HOLIDAY }
  @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="employee_id") public Employee employee;
  @Column(name="attendance_date", nullable=false) public LocalDate date;
  @Enumerated(EnumType.STRING) @Column(nullable=false) public Status status;
  @Column(name="check_in_time") public LocalTime checkInTime;
  @Column(name="check_out_time") public LocalTime checkOutTime;
  @Column(name="minutes_late", nullable=false) public int minutesLate;
  public String notes;
  @Column(name="recorded_by") public UUID recordedBy;
  @Column(name="created_at", nullable=false) public Instant createdAt;
  @Column(name="updated_at", nullable=false) public Instant updatedAt;
  @PrePersist void create(){ var now=Instant.now(); createdAt=now; updatedAt=now; }
  @PreUpdate void update(){ updatedAt=Instant.now(); }
}

