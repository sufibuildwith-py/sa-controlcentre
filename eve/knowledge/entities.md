# SA Command — Core Entities

Machine-readable catalog of core entities, identifiers, authoritative services, and fields.

## Employee
- **Table**: `employees`
- **Identifier**: `id` (UUID), `employee_code` (String, unique)
- **Names**: `display_name`, `first_name`, `last_name`
- **Status**: `ACTIVE`, `ON_LEAVE`, `INACTIVE`
- **Financial Link**: Queried via `FinanceReadService.employee(id)`
  - `earned`: Total sum of valid `finance_employee_obligations`
  - `paid`: Total sum of posted payment allocations
  - `outstanding`: `earned - paid`
- **Operational Link**: Assigned tasks in `tasks`, assigned crew in `production_members`

## Production
- **Table**: `productions`
- **Identifier**: `id` (UUID)
- **Attributes**: `title`, `client_name`, `venue`, `event_date`, `start_time`, `end_time`, `status`
- **Financial Profile**: `finance_production_profiles`, `finance_transactions` (allocations to production)
- **Crew**: Linked via `production_members` (`production_id`, `employee_id`, `role`)
- **Equipment**: Linked via `equipment_reservations` (`production_id`, `equipment_id`, `quantity`)

## Counterparty (Party / Client)
- **Table**: `finance_counterparties`
- **Identifier**: `id` (UUID), `display_name`
- **Attributes**: `role` (`CUSTOMER`, `VENDOR`), `gstin`
- **Financial Link**: Queried via `FinanceReadService.counterparty(id)`

## Equipment Inventory Item
- **Table**: `equipment_inventory`
- **Identifier**: `id` (UUID), `code` (e.g. `DEMO-HQ-007`)
- **Attributes**: `name`, `category`, `total_quantity`, `available_quantity`

## Work Task
- **Table**: `tasks`
- **Identifier**: `id` (UUID)
- **Attributes**: `title`, `description`, `status`, `due_at`, `assigned_employee_id`, `production_id`
