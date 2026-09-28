# SA Command — Commands & Capabilities

Catalog of domain commands. In Phase 1, only READ queries are active. Write commands are documented for schema grounding and will remain disabled until Phase 3.

## Read Commands (Phase 1 Active)
- `READ_EMPLOYEE_FINANCE`:
  - Input: `employeeId` (UUID) or candidate name
  - Service: `FinanceReadService.employee(UUID id)`
  - Data returned: `earned`, `paid`, `outstanding`, `obligations`
- `READ_EMPLOYEE_360`:
  - Input: `employeeId` (UUID)
  - Service: `Employee360Service.get360(UUID id)`
  - Data returned: Identity, today's attendance, money summary, operations, performance
- `READ_PRODUCTION`:
  - Input: `productionId` (UUID) or candidate title
  - Service: `ProductionService.get(UUID id)`
  - Data returned: Title, client, venue, event date, schedule, status
- `READ_HEADQUARTERS_INVENTORY`:
  - Input: `search`, `category`
  - Service: `HeadquartersService.listInventory(...)`

## Write Commands (Phase 3 Governed Execution — Inactive in Phase 1)
- `RECORD_EMPLOYEE_PAYMENT`:
  - Invariants: Explicit payer (`AZ-2` / `AK-2`), non-zero positive amount, fresh outstanding balance check, idempotent submission.
- `RECORD_PRODUCTION_RECEIPT`:
  - Invariants: Explicit receiving owner account, target production, party counterparty.
- `ASSIGN_EQUIPMENT`:
  - Invariants: Availability validation, non-overlapping or safe time window.
