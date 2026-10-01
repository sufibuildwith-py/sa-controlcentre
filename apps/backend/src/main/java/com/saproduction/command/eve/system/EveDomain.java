package com.saproduction.command.eve.system;

/**
 * Authoritative system domains in SA Command.
 * Captures real system boundaries derived directly from the application's domain services.
 */
public enum EveDomain {
  EMPLOYEE("People / Team domain managed by EmployeeService and Employee360Service"),
  PRODUCTION("Events / Productions domain managed by ProductionService and ProductionRepository"),
  HEADQUARTERS_EQUIPMENT("Physical equipment, inventory stock, and warehouse positions managed by HeadquartersService"),
  WORK_TASK("Operational tasks and assignments managed by WorkTaskService"),
  FINANCE("Financial transactions, obligations, and balances managed by FinanceReadService and FinancePostingService"),
  CALENDAR("Schedules and temporal date views managed by ProductionService calendar queries"),
  SYSTEM("System-level conversational greetings, settings, and memory"),
  GENERAL("General cognitive capabilities including arithmetic, recommendations, and conversation"),
  EXTERNAL("External capabilities such as weather and third-party lookups");

  private final String description;

  EveDomain(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
