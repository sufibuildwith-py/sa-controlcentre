package com.saproduction.command.eve.system;

/**
 * Information topics describing WHAT factual information the user is seeking.
 * Used by language understanding to map natural language to the system's capabilities.
 */
public enum EveInformationTopic {
  // Equipment / Inventory
  EQUIPMENT_STOCK("Physical inventory, usable count, reserved count, and available stock in headquarters"),
  EQUIPMENT_OVERVIEW("Fleet-wide summary of all controlled, usable, reserved, and deployed equipment"),

  // Production reads
  CLIENT_INFO("Client, customer, or commissioning party for a production"),
  CREW_MEMBERS("Assigned crew, technicians, sound/lighting engineers on a production"),
  EQUIPMENT_ASSIGNED("Specific equipment, gear packages, and quantities assigned to a production"),
  PRODUCTION_TASKS("Work tasks linked to a specific production"),
  PRODUCTION_OVERVIEW("General status, venue, date, and timing of a production"),
  PRODUCTION_FINANCE("Financial position of a production: contract value, advance received, outstanding balance, and transactions"),
  MEMBER_PRESENCE("Checking whether a specific employee is assigned to a production"),

  // Employee reads
  EMPLOYEE_FINANCE("Financial position, earned obligations, payments made, and outstanding balance of an employee"),
  EMPLOYEE_PROFILE("360 degree profile, role, department, contact info, and status of an employee"),
  EMPLOYEE_ASSIGNMENTS("Productions and events currently or historically assigned to an employee"),

  // Work / Tasks
  TASK_SUMMARY("Overview of open or pending tasks across the system"),

  // Calendar
  SCHEDULE_DATE("Productions and events scheduled for a specific date (today, tomorrow, etc.)"),

  // Governed Execution (Phase 3)
  PAYMENT_PROPOSAL("Proposing a formal payment plan to pay an employee from an owner account"),

  // Conversational / System
  GREETING("Conversational greeting, pleasantry, or gratitude"),
  DISAMBIGUATION_CHOICE("Selection from multiple candidate entities presented during disambiguation"),
  VOCABULARY_DEFINITION("Learning an operator-specific alias or vocabulary term"),
  SYSTEM_OVERVIEW("General health and activity summary of SA Command"),

  // Bounded Out-of-Scope / Unsupported
  UNSUPPORTED("Information need outside SA Command's authoritative domain capabilities"),
  UNKNOWN("Unable to determine information need");

  private final String description;

  EveInformationTopic(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
