# SA Command — System Domains

This document provides machine-readable and operational definitions of the authoritative domains in SA Command.

## 1. People (Employee Domain)
- **Authority**: `EmployeeService`, `Employee360Service`, `EmployeeRepository`
- **Tables**: `employees`, `attendance_records`, `leave_requests`, `payroll_periods`, `payroll_slips`
- **Core Entity**: `Employee`
  - Identifier: UUID `id`, business code `employee_code` (e.g. `SA-01`)
  - Statuses: `ACTIVE`, `ON_LEAVE`, `INACTIVE`
  - Roles: Sound engineer, technician, operator, director, etc.
- **Invariants**:
  - Employee codes are unique and uppercase.
  - Deactivation preserves audit trail and financial history.
  - Attendance and leave track physical availability.

## 2. Productions (Event Domain)
- **Authority**: `ProductionService`, `ProductionRepository`
- **Tables**: `productions`, `production_members`, `calendar_events`
- **Core Entity**: `Production`
  - Identifier: UUID `id`
  - Statuses: `DRAFT`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`
  - Key attributes: `title`, `client_name`, `venue`, `event_date`, `start_time`, `end_time`
- **Invariants**:
  - A production organizes crew (employees), equipment reservations, tasks, and commercial contracts.
  - Production completion does NOT imply payment, receipt, expense, or invoice settlement.

## 3. Headquarters (Equipment & Inventory Domain)
- **Authority**: `HeadquartersService`
- **Tables**: `equipment_inventory`, `equipment_reservations`, `equipment_assignments`
- **Core Entity**: Equipment inventory items and gear packages.
- **Invariants**:
  - Equipment assignment/reservation reserves physical stock from Headquarters for a specific production window.
  - Assignment is NOT purchase or financial payment.
  - Stock count cannot be negative; reservations require available inventory.

## 4. Work (Task Domain)
- **Authority**: `WorkTaskService`, `WorkTaskRepository`
- **Tables**: `tasks`, `task_updates`
- **Core Entity**: `WorkTask`
  - Identifier: UUID `id`
  - Statuses: `TODO`, `IN_PROGRESS`, `DONE`, `CANCELLED`
  - Assignee: Optional foreign key to `employees(id)`
  - Production: Optional foreign key to `productions(id)`

## 5. Finance (Financial Ledger Domain)
- **Authority**: `FinancePostingService`, `FinanceReadService`
- **Tables**: `finance_transactions`, `finance_employee_obligations`, `finance_employee_payment_allocations`, `finance_counterparties`, `finance_counterparty_charges`, `finance_counterparty_payment_allocations`, `finance_invoices`, `finance_invoice_payment_allocations`, `finance_reconciliation_snapshots`
- **Core Entities**:
  - `FinanceTransaction`: The append-only source of financial truth.
  - `EmployeeObligation`: Money owed to an employee (e.g. shift earning, base salary).
  - `EmployeePaymentAllocation`: Links an actual posted payment transaction to obligations.
- **Invariants**:
  - Money is stored in integer minor units (paisa) or exact `BigDecimal`.
  - Balances are derived from posted transactions; balances are never manually edited.
  - Owner attribution is explicit (`AZ-2` for Azeem Khan, `AK-2` for Akash).

## 6. Billing (Invoicing Domain)
- **Authority**: `BillingService`
- **Tables**: `bills`, `bill_items`, `tax_rates`
- **Core Entities**: Direct party charges and formal tax invoices.
- **Invariants**:
  - Billing draft export or issuance does not post money.
  - Settlements must target specific invoices or direct charges.
