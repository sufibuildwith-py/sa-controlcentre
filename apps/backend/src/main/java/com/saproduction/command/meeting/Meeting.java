package com.saproduction.command.meeting;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="meetings")
public class Meeting {
  public enum Status { SCHEDULED,COMPLETED,CANCELLED }
  @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
  @Column(nullable=false) public String title;public String description;public String agenda;
  @Column(name="starts_at",nullable=false) public Instant startsAt;
  @Column(name="ends_at",nullable=false) public Instant endsAt;
  public String location;
  @Enumerated(EnumType.STRING) @Column(nullable=false) public Status status;
  @Column(name="created_at",nullable=false) public Instant createdAt;
  @Column(name="updated_at",nullable=false) public Instant updatedAt;
  @PrePersist void create(){var now=Instant.now();createdAt=now;updatedAt=now;if(status==null)status=Status.SCHEDULED;}
  @PreUpdate void update(){updatedAt=Instant.now();}
}
