package com.saproduction.command.eve;

import java.util.Set;

/**
 * Finite allowlist of typed domain signals observable by EVE.
 */
public final class EveSignalTypes {
  private EveSignalTypes() {}

  // Finance Domain
  public static final String EMPLOYEE_PAYMENT_POSTED = "EMPLOYEE_PAYMENT_POSTED";
  public static final String EMPLOYEE_EARNING_CREATED = "EMPLOYEE_EARNING_CREATED";
  public static final String EMPLOYEE_BALANCE_CHANGED = "EMPLOYEE_BALANCE_CHANGED";
  public static final String INVOICE_CREATED = "INVOICE_CREATED";
  public static final String INVOICE_SETTLED = "INVOICE_SETTLED";
  public static final String PARTY_CHARGE_CREATED = "PARTY_CHARGE_CREATED";
  public static final String PARTY_RECEIPT_POSTED = "PARTY_RECEIPT_POSTED";

  // Production Domain
  public static final String PRODUCTION_CREATED = "PRODUCTION_CREATED";
  public static final String PRODUCTION_UPDATED = "PRODUCTION_UPDATED";
  public static final String PRODUCTION_STATUS_CHANGED = "PRODUCTION_STATUS_CHANGED";
  public static final String PRODUCTION_DATE_CHANGED = "PRODUCTION_DATE_CHANGED";
  public static final String CREW_ASSIGNMENT_CHANGED = "CREW_ASSIGNMENT_CHANGED";

  // Work Domain
  public static final String TASK_CREATED = "TASK_CREATED";
  public static final String TASK_COMPLETED = "TASK_COMPLETED";
  public static final String TASK_DUE_DATE_CHANGED = "TASK_DUE_DATE_CHANGED";
  public static final String TASK_PROGRESS_UPDATED = "TASK_PROGRESS_UPDATED";

  // Headquarters Domain
  public static final String EQUIPMENT_RESERVATION_CREATED = "EQUIPMENT_RESERVATION_CREATED";

  public static final Set<String> ALL = Set.of(
      EMPLOYEE_PAYMENT_POSTED,
      EMPLOYEE_EARNING_CREATED,
      EMPLOYEE_BALANCE_CHANGED,
      INVOICE_CREATED,
      INVOICE_SETTLED,
      PARTY_CHARGE_CREATED,
      PARTY_RECEIPT_POSTED,
      PRODUCTION_CREATED,
      PRODUCTION_UPDATED,
      PRODUCTION_STATUS_CHANGED,
      PRODUCTION_DATE_CHANGED,
      CREW_ASSIGNMENT_CHANGED,
      TASK_CREATED,
      TASK_COMPLETED,
      TASK_DUE_DATE_CHANGED,
      TASK_PROGRESS_UPDATED,
      EQUIPMENT_RESERVATION_CREATED
  );

  public static boolean isValid(String signalType) {
    return signalType != null && ALL.contains(signalType);
  }
}
