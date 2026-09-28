# SA Command — Entity Relationships

Defines relational topology across domains for grounded cross-system intelligence.

```
                      ┌───────────────┐
                      │   EMPLOYEE    │
                      └───────┬───────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
          ▼                   ▼                   ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│Attendance / Leave│ │ Tasks / Work     │ │FinanceObligation │
│attendance_records│ │ assigned_emp_id  │ │  & Allocations   │
└──────────────────┘ └──────────────────┘ └─────────┬────────┘
          ▲                   ▲                     │
          │                   │                     ▼
          │          ┌────────┴───────┐   ┌──────────────────┐
          └──────────┤   PRODUCTION   ├───┤FinanceTransaction│
                     └────────┬───────┘   └──────────────────┘
                              │
                              ▼
                     ┌──────────────────┐
                     │   HEADQUARTERS   │
                     │ equipment_reserv │
                     └──────────────────┘
```

## Key Invariants
1. **Employee ↔ Production**: An employee is assigned to a production via `production_members`.
2. **Production ↔ Equipment**: Equipment is reserved for a production duration via `equipment_reservations`.
3. **Production ↔ Task**: Tasks can be scoped to a production or independent.
4. **Employee ↔ Finance**: An employee accumulates earnings via obligations (per shift or salary), and payments settle those obligations through transactions.
5. **Production ↔ Finance**: Productions have revenues (receipts from client counterparties) and expenses (crew earnings, vendor direct charges).
