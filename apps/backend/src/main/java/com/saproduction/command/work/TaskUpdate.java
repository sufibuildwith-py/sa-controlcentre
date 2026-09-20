package com.saproduction.command.work;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_updates")
public class TaskUpdate {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  public UUID id;

  @Column(name = "task_id", nullable = false)
  public UUID taskId;

  @Column(name = "author_id")
  public UUID authorId;

  @Column(name = "progress_percent", nullable = false)
  public int progressPercent;

  public String note;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @PrePersist
  void create() {
    createdAt = Instant.now();
  }
}
