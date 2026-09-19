package com.saproduction.command.leave;

import com.saproduction.command.employee.Employee;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="leave_requests")
public class LeaveRequest {
  public enum Status { PENDING, APPROVED, REJECTED, CANCELLED }
  @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="employee_id") public Employee employee;
  @Column(name="start_date", nullable=false) public LocalDate startDate;
  @Column(name="end_date", nullable=false) public LocalDate endDate;
  @Column(name="leave_type", nullable=false) public String leaveType;
  @Column(nullable=false) public String reason;
  @Enumerated(EnumType.STRING) @Column(nullable=false) public Status status;
  @Column(name="owner_note") public String ownerNote;
  @Column(name="created_at", nullable=false) public Instant createdAt;
  @Column(name="resolved_at") public Instant resolvedAt;
  @PrePersist void create(){ createdAt=Instant.now(); if(status==null) status=Status.PENDING; }
}

