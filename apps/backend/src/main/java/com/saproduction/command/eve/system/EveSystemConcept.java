package com.saproduction.command.eve.system;

/**
 * Core entity concepts that authoritative services in SA Command manage.
 * Only concepts supported by actual repositories and domain services are included.
 */
public enum EveSystemConcept {
  EMPLOYEE("Individual staff member, technician, or contractor with unique employee code and role"),
  PRODUCTION("Event or production with client, venue, date, assigned crew, tasks, and gear"),
  EQUIPMENT("Physical equipment or consumable resource tracked in headquarters positions and reservations"),
  WORK_TASK("Action item or operational task optionally assigned to an employee or production"),
  CALENDAR_DATE("Temporal calendar reference to inspect scheduled productions on a given date"),
  FINANCE_RECORD("Financial ledger entry, obligation, or payment allocation"),
  SYSTEM("System-level operational entity or assistant capability");

  private final String description;

  EveSystemConcept(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
