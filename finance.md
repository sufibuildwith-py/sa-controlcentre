SA Command --- Finance Control Plane (finance.md)
Status: implementation specification / source-of-truth update
Business: SA Production
Scope: broad Finance update for SA Command
Source basis: 2026 Led Wall Booking (1).xlsx,
sheet analysis.txt, and the confirmed business answers collected
from Azeem/Akash workflow discussion
Research date: 2026-09-24
Primary rule: Do not simplify the business logic just because
the UI is simpler. Preserve the Excel system's financial behavior, but
replace duplicate manual entry with one transaction that propagates
everywhere.

0. What this update is
Finance is the most important control surface in SA Command. It is not
an "income/expense page" and it must not be implemented as a few cards
plus a transaction table.
The existing workbook is already a financial operating system spread
across 18 worksheets, approximately 41,352 populated cells, and
1,366 formula cells. It combines:
- event/production revenue,
- advances and later collections,
- event expenses,
- work-based employee earnings,
- employee settlements,
- fixed salary behavior,
- Azeem/Akash money positions,
- customer/party receivables,
- GST invoices and TDS,
- equipment purchases and payment settlement,
- quotation/costing logic,
- owner profit allocation,
- and a final cross-sheet reconciliation/control layer.
The new Finance module must reproduce those relationships in a
normalized, auditable system without forcing the user to understand
database/accounting internals.
The user experience should feel much easier than Excel:
Enter the real-world transaction once. SA Command performs every
linked update automatically.

Examples:
- Record ₹6,500 event expense → ask who paid → update event cost +
  AZ/AK position + overall result + reconciliation + audit.
- Pay Roshan ₹17,000 → ask who paid → update employee payment
  history + employee outstanding + AZ/AK position + Finance
  dashboard + reconciliation.
- Receive ₹10,000 from a client → ask who received it → update
  production collection + party ledger + receivable + AZ/AK position +
  realized result + overview.
- Buy equipment → create financial purchase/liability and link it to
  Headquarters inventory without merging physical custody and payment
  state.
No screen is allowed to require the same real transaction to be
entered twice.
1. Non-negotiable business rules confirmed from the workbook + meeting
These rules are the initial Finance contract. They are not UI
suggestions.
1.1 Profit split
Current confirmed profit allocation:
Azeem / Mamu = 65%
Akash        = 35%
The workbook repeatedly implements the same split. Examples include:
Akash = Profit × 35%
Azeem = Profit - Akash
      = Profit × 65%
Jan 26 baseline:
Profit / Budget = ₹1,603,683.00
Akash 35%       = ₹561,289.05
Azeem 65%       = ₹1,042,393.95
Important:
- Treat 65/35 as a profit-allocation rule.
- Do not automatically apply 65/35 to every expense, purchase,
  salary, rental, or cash movement.
- If future business rules introduce a different split for a venture,
  it must be explicit and versioned.
- Never retroactively recalculate old periods merely because the
  default split changes later.
Recommended rule model:
profit_split_rule
- id
- effective_from
- effective_to nullable
- azeem_percent = 65.00
- akash_percent = 35.00
- scope = DEFAULT_PROFIT
- status
Invariant:
azeem_percent + akash_percent = 100.00
1.2 AZ-2 and AK-2 are operational owner/current positions
Do not model AZ-2 and AK-2 as ordinary bank accounts only.
They are business operating positions that answer:
- how much money came through Azeem/Akash,
- how much was paid by Azeem/Akash,
- and what the resulting signed position is.
A negative position is valid.
Confirmed interpretation example:
Available / recorded position:  ₹10,00,000
Equipment/payment made:         ₹20,00,000
Resulting position:             -₹10,00,000
This does not automatically mean "SA Production owes Azeem ₹10
lakh". It means the relevant running position has gone below zero
because outflow exceeded the recorded balance/inflow.
Workbook baselines:
az-2
Credit  = ₹2,457,885
Expense = ₹4,155,407
Balance = -₹1,697,522

Ak-2 September block
From    = ₹529,760
To      = ₹425,665
Balance = ₹104,095
Earlier az 26 separates cash and bank:
Cash balance = ₹178,250
Bank balance = ₹80,599
Combined     = ₹258,849
That ₹258,849 is carried forward as Last balance in az-2.
Required implementation
Create two canonical owner-position accounts:
AZ-2 — Azeem
AK-2 — Akash
Every money movement affecting one of them must have an explicit
owner-account attribution.
For an outflow:
Who paid?
[ Azeem / AZ-2 ] [ Akash / AK-2 ]
For an inflow:
Who received it?
[ Azeem / AZ-2 ] [ Akash / AK-2 ]
For a transfer:
From: AZ-2
To:   AK-2
Amount: ₹...
Never infer payer/receiver from the logged-in user.
1.3 Payer attribution is mandatory
Confirmed workflow:
If ₹6,500 is added as an expense anywhere, SA Command must ask whether
Azeem or Akash paid it. The selected account must receive the
corresponding financial effect automatically.

Therefore:
OUTFLOW → payer_account_id REQUIRED
INFLOW  → receiver_account_id REQUIRED
TRANSFER → source_account_id + destination_account_id REQUIRED
This applies across:
- production/event expense,
- employee payment,
- rental/vendor payment,
- general expense,
- equipment purchase payment,
- EMI,
- transport,
- food,
- repair,
- other business outflows,
- client receipt,
- party receipt,
- invoice receipt,
- advances.
No financial write endpoint should accept an outflow without payer
attribution unless the transaction is explicitly marked as an
opening/migration adjustment.
1.4 Add is money received
The workbook's add field means an advance/partial amount received
against a production.
Existing event logic is effectively:
Outstanding = Total - Add - Payment
In the new system:
- preserve legacy_add as a migration/source label,
- but do not create a confusing permanent business concept called
  Add.
Normalize both Add and Payment into receipts allocated to a
production receivable, while retaining their legacy source type for
parity.
Recommended:
receipt.allocation_type:
- LEGACY_ADD
- LEGACY_PAYMENT
- ADVANCE
- COLLECTION
- INVOICE_PAYMENT
- PARTY_PAYMENT
- OTHER
1.5 Employee earning and employee payment are different events
Confirmed example:
Roshan = amount Roshan earned for that work/date
Roshan Pay = amount actually paid to Roshan
An employee may earn today and receive the money later.
Therefore:
WORK/EARNING EVENT
      ↓
employee payable increases
      ↓
payment can occur later
      ↓
employee payable decreases
Do not store this as a boolean paid.
1.6 There are two employee compensation modes
Mode A --- work/event based
Example: Roshan-style running balance.
Work performed
→ earning generated
→ running employee payable
→ arbitrary partial payments
→ eventual settlement
Payment timing is not fixed.
Mode B --- fixed monthly salary
monthly gross salary
- leave / approved deductions
± manual adjustments
= net salary obligation

then:
payment 1
payment 2
payment 3...
= amount paid

net salary obligation - amount paid
= remaining payable
A fixed-salary employee can still be paid in multiple slots.
Finance must support both modes per employee, not globally.
1.7 Party ledgers are mostly receivable/customer ledgers
The large शीट4 structure contains many party-specific mini-ledgers
such as:
- Sanjeev Dada
- Rinku Bhaiya
- Shailendra Ji
- Anjali Enterprises
- Kanu Babu
- and others.
The common shape is:
Date
Venue
LED Wall / service
Rate
Amount
Payment
Balance
with:
Balance = Amount - Payment
The new system must replace "one spreadsheet region per party" with one
generic Counterparty Ledger.
A counterparty can later support multiple roles:
CUSTOMER
VENDOR
RENTAL_PROVIDER
SUBCONTRACTOR
OTHER
but migration must not guess a role when the workbook does not prove it.
1.8 Equipment purchase sheets are SA Production-owned equipment finance
LED and Sound represent equipment purchases/payment settlement for
equipment owned by SA Production.
Examples:
LED baseline:
Total purchase amount = ₹13,076,798
Payments              = ₹11,306,448
Outstanding           = ₹1,770,350
Sound example block:
LFX total = ₹3,174,223
Paid      = ₹3,174,223
Balance   = ₹0
Finance and Headquarters must connect, but must remain separate truths:
Headquarters = physical asset/custody/availability truth
Finance      = purchase/cost/payment/liability truth
Never make "asset exists" imply "asset is paid". Never make "invoice
paid" imply "asset quantity changed".
1.9 Final is both settlement and overall business position
Confirmed owner behavior:
When opening Final, Azeem first wants to see:
1. overall profit or loss, and
2. how much balance/position is with Azeem and Akash.
That exact priority must drive:
- Finance landing page,
- and the small Finance section on the main SA Command Overview.
Do not put GST, invoice counts, employee details, equipment, or every
finance KPI on the main Overview.
2. Reverse-engineered workbook map
The workbook currently contains these major areas:
  Sheet                               Interpreted role
  All Date 26                       broad production/date/payment
                                      overview
  jully dec P3 Led                  P3 LED production/event finance +
                                      employee earning/payment
  JUL+DEC                           production/event finance + employee
                                      earning/payment
  Jan 26                            production/event finance + employee
                                      earning/payment
  Sound Jul+dec                     sound production finance structure
  Ak-2                              Akash current/operating account
  az-2                              Azeem current/operating account
                                      continuation
  Final                             cross-sheet
                                      settlement/reconciliation/control
  Jul-Dec                           production/event finance + employee
                                      earning/payment
  az 26                             earlier Azeem cash + bank ledgers
  GST                               invoices, GST, TDS, payments,
                                      balance
  G Pay Aakash 26                   earlier Akash account ledger
  Expence                           general/owner expenses and
                                      adjustments
  शीट4                              party/customer mini-ledgers
  Varma ji                          quotation / event costing / rate
                                      calculations
  LED                               LED equipment purchase finance
  Sound                             sound equipment purchase finance
  शीट4 (2)                          accessories/equipment data and
                                  small calculations
The workbook is not merely formula-connected. It also relies on manual
duplicate entry of the same real transaction in different sheets.
Observed examples include matching account/event entries such as:
JUL+DEC:
Govind payment ₹10,000

Ak-2:
Govind FROM ₹10,000
and:
Jan 26:
Perfect Cue payment ₹2,000

G Pay Aakash 26:
Perfect Cue FROM ₹2,000
This is the behavior SA Command must eliminate.
3. Legacy formula contract
These formulas matter because the new system must be able to reproduce
legacy outputs during migration/parity testing.
3.1 Production outstanding
Observed row-level formula:
Outstanding = Total - Add - Payment
Example:
Total    ₹6,000
Add      ₹2,000
Payment       ₹0
Balance  ₹4,000
New normalized equivalent:
contracted_amount = ₹6,000
receipts_total    = ₹2,000
outstanding       = ₹4,000
Invariant:
outstanding = contracted_amount - allocated_receipts
Over-collection must not silently produce a negative receivable. It
requires either:
- explicit overpayment/credit handling,
- or validation preventing the allocation.
3.2 Legacy Budget is realized/cash-position margin, not simple contracted profit
Observed:
Budget = Total - Balance - Expense
Since:
Total - Balance = Add + Payment = money received
the effective rule is:
Legacy Realized Result = Money Received - Expense
Example from JUL+DEC:
Total       ₹6,000
Received    ₹2,000
Outstanding ₹4,000
Expense     ₹2,600
Budget       -₹600
This is important.
The new Finance UI should expose two separate metrics:
Contracted Margin
= contracted revenue - incurred expenses

Realized Margin
= received revenue - incurred expenses
For Excel parity:
Legacy Budget ≈ Realized Margin
Do not replace the existing meaning with conventional accounting
terminology without preserving the legacy projection.
3.3 Employee payable
Semantic rule:
Employee Outstanding = Employee Earned - Employee Paid
The workbook has inconsistent column ordering/naming in different
sheets, but the business meaning is confirmed.
The application must normalize this into:
employee_earning
employee_payment
employee_balance_projection
Negative balances must be visible and classified rather than silently
clipped to zero; they may represent advance/overpayment or legacy data
inconsistency.
3.4 Profit allocation
Observed:
Akash share = Profit × 35%
Azeem share = Profit × 65%
P3 sometimes expresses Akash as:
30% + 5% = 35%
The application must store one semantic 35% rule rather than reproducing
arbitrary formula syntax.
3.5 Owner/current account
Akash monthly blocks:
Balance = From - To
Azeem ledgers:
Balance = Credit - Expense
Earlier az 26:
Cash balance = Cash credit - Cash expense
Bank balance = Bank credit - Bank expense
Combined = Cash balance + Bank balance + carry/adjustment
The new account model must support:
- opening balance,
- credit/inflow,
- debit/outflow,
- transfer,
- adjustment,
- closing signed position.
3.6 GST
Observed invoice columns include:
Invoice No
Date
Party
GST No
Amount
IGST
CGST
SGST
Total
TDS
Payment 1...
Payment 6...
Balance
Observed formulas include:
CGST = Amount × 9%
SGST = Amount × 9%
Total = Amount + CGST + SGST
and for IGST rows:
IGST = Amount × 18%
Total = Amount + IGST
Example invoice:
Base amount = ₹388,800
CGST        = ₹34,992
SGST        = ₹34,992
Invoice     = ₹458,784
TDS         = ₹7,776
Payments    = multiple slots
Balance     = Invoice - TDS - payments
Tax warning: these workbook formulas are source behavior, not a
declaration that every future invoice must use these tax rates. GST/TDS
configuration must be explicit and editable by authorized users. SA
Command is not to invent tax treatment.
3.7 Equipment purchase balance
Observed:
Outstanding = Total Purchase Amount - Payments
The LED sheet also contains 65/35 values against the outstanding
amount. Because the meeting only confirmed 65/35 as the profit split, do
not make this a universal automatic rule in the new system. Preserve
imported legacy values and require explicit configuration if this
allocation is still operationally required for equipment.
4. Target architecture
4.1 Architecture style
Keep SA Command a modular monolith.
Do not create Finance microservices.
Recommended logical modules:
finance
├── ledger
├── accounts
├── productionfinance
├── employees
├── receivables
├── invoicing
├── assets
├── reconciliation
├── reporting
├── migration
└── audit
Integrations:
Productions ───────┐
People/Payroll ────┤
Headquarters ──────┤
Calendar/Work ─────┤
                   ▼
              FINANCE CORE
                   │
                   ├── immutable financial events
                   ├── journal/postings
                   ├── projections
                   ├── reconciliation
                   └── reporting
                   │
                   ▼
              Command Overview
Finance owns financial truth. Other modules own their domain truth.
Examples:
- People owns employee identity/employment.
- Attendance owns attendance/leave evidence.
- Productions owns production identity/schedule.
- Headquarters owns physical assets and custody.
- Finance owns money obligations, receipts, payments, cost and
  financial position.
5. Core design principle: one business event, multiple projections
Never reproduce Excel by creating 18 independent CRUD screens.
Use:
Command
  ↓
Financial Event
  ↓
Validated Journal/Postings
  ↓
Projections
  ├── AZ-2 / AK-2
  ├── production finance
  ├── employee payable
  ├── party ledger
  ├── invoice balance
  ├── equipment liability
  ├── profit allocation
  ├── Final/reconciliation
  └── Overview
Example:
RecordEmployeePayment(
    employee = Roshan,
    amount = 17000,
    payer = AZ_2
)
must atomically result in:
AZ-2 position               -₹17,000
Roshan payments             +₹17,000
Roshan outstanding          -₹17,000
payroll/employee projection updated
reconciliation updated
audit trail written
overview updated
The user performs one action.
6. Finance transaction lifecycle
Use explicit states:
DRAFT
POSTED
REVERSED
Optional later:
PENDING_APPROVAL
REJECTED
Rules:
- Drafts can be edited.
- Posted financial transactions are immutable.
- Posted transactions are never hard-deleted.
- Corrections happen by reversal + replacement.
- Every reversal references the original transaction.
- Projections use POSTED transactions only.
- Imported Excel history can be tagged MIGRATED.
- A migration adjustment is explicit and cannot masquerade as a normal
  transaction.
7. Money representation
Never use double or float.
Backend:
BigDecimal
Database:
NUMERIC(19,2)
Default currency:
INR
Recommended value object:
record Money(BigDecimal amount, Currency currency) {}
or use JSR 354 / Moneta if it integrates cleanly.
Rules:
- explicit scale,
- explicit rounding mode,
- no binary floating-point money,
- API sends amounts as decimal strings where necessary,
- UI formats with Indian grouping (₹1,23,456.00) but does not
  perform authoritative calculations.
All authoritative calculations happen server-side.
8. Domain model
8.1 finance_account
Represents an operational money position.
id
code                    // AZ-2, AK-2
name
owner_person_id nullable
account_type            // OWNER_CURRENT
currency
active
created_at
Initial accounts:
AZ-2
AK-2
Do not create one database table per owner.
8.2 financial_transaction
One user-recognizable business event.
id UUID
transaction_no
type
status
transaction_date
effective_date
description
currency
total_amount
production_id nullable
counterparty_id nullable
employee_id nullable
invoice_id nullable
equipment_purchase_id nullable
payer_account_id nullable
receiver_account_id nullable
created_by
posted_by
posted_at
source
idempotency_key
reversal_of_transaction_id nullable
migration_batch_id nullable
legacy_source_json nullable
created_at
Suggested transaction types:
PRODUCTION_RECEIPT
PRODUCTION_EXPENSE
EMPLOYEE_EARNING
EMPLOYEE_PAYMENT
MONTHLY_SALARY_ACCRUAL
SALARY_DEDUCTION
PARTY_CHARGE
PARTY_RECEIPT
INVOICE_ISSUED
INVOICE_PAYMENT
TDS_WITHHELD
EQUIPMENT_PURCHASE
EQUIPMENT_PAYMENT
GENERAL_EXPENSE
OWNER_ACCOUNT_CREDIT
OWNER_ACCOUNT_DEBIT
OWNER_TRANSFER
OPENING_BALANCE
ADJUSTMENT
REVERSAL
8.3 journal_entry
Optional but strongly recommended as the accounting/integrity layer.
id
financial_transaction_id
entry_no
posted_at
description
8.4 journal_line
id
journal_entry_id
ledger_account_code
debit
credit
owner_account_id nullable
production_id nullable
employee_id nullable
counterparty_id nullable
invoice_id nullable
asset_purchase_id nullable
Invariant:
SUM(debit) == SUM(credit)
Use this as an internal integrity model even though the old workbook is
not presented as formal double-entry accounting.
Do not expose debit/credit jargon to Mamu unless needed.
9. Recommended journal behavior
The UI stays business-language-first; the backend can maintain balanced
entries.
9.1 Production receipt
Receive ₹10,000 from Govind into AK-2
Business effects:
AK-2 position             +₹10,000
Govind receivable         -₹10,000
Production collected      +₹10,000
Production outstanding    -₹10,000
Realized margin           recalculated
Journal concept:
Dr AK-2 / Cash Position
Cr Customer Receivable
9.2 Production expense
Food ₹1,400 paid by AZ-2
Effects:
Production expense        +₹1,400
AZ-2 position             -₹1,400
Realized margin           -₹1,400
Contracted margin         -₹1,400
Journal:
Dr Production Expense
Cr AZ-2 Position
9.3 Employee earning
Roshan earned ₹2,500 on Production X
Effects:
Production labor expense  +₹2,500
Roshan payable            +₹2,500
Journal:
Dr Production Labor Expense
Cr Employee Payable — Roshan
No AZ/AK balance changes because nobody has paid yet.
9.4 Employee payment
Roshan paid ₹17,000 by AZ-2
Effects:
Roshan payable            -₹17,000
AZ-2 position             -₹17,000
Journal:
Dr Employee Payable — Roshan
Cr AZ-2 Position
Do not add another production expense here if the earning already
created the cost. That would double-count labor.
9.5 Equipment purchase
At purchase recognition:
Dr Equipment / Capital Purchase
Cr Equipment Payable
When payment occurs:
Dr Equipment Payable
Cr AZ-2 or AK-2
This allows:
physical equipment exists
AND
purchase still partly unpaid
without contradiction.
10. Production Finance
Each Production must have a Finance tab.
10.1 Header
Production
Client
Venue
Dates
Status
10.2 Financial summary
Contracted Revenue
Received
Outstanding
Incurred Expense
Contracted Margin
Realized Margin
Definitions:
received = sum(posted receipts allocated to production)

outstanding =
max(0, contracted_revenue - received)
unless explicit credit/overpayment exists

contracted_margin =
contracted_revenue - incurred_expense

realized_margin =
received - incurred_expense
Legacy parity:
Legacy Balance = Total - Add - Payment
Legacy Budget  = Total - Legacy Balance - Expense
               = received - expense
10.3 Transaction timeline
Show:
Date | Type | Description | Amount | AZ/AK | Party | Status
Examples:
24 Sep | Receipt | Govind | +₹10,000 | AK-2
24 Sep | Expense | Food | -₹1,400 | AZ-2
24 Sep | Earning | Roshan | ₹2,500 payable | —
10.4 Quick actions
+ Receive Payment
+ Add Expense
+ Add Employee Earning
+ Add Vendor/Rental Cost
+ Add Adjustment
Every relevant action must ask payer/receiver.
11. Employee Finance
Create a dedicated employee financial ledger, integrated with People and
Payroll.
11.1 Employee finance profile
Compensation type:
- WORK_BASED
- FIXED_MONTHLY
Configurable fields:
monthly_salary nullable
effective_from
deduction_policy
active
11.2 Work-based employee
Ledger:
Date | Production | Earned | Paid | Running Outstanding | Payer
Projection:
total_earned
total_paid
outstanding = total_earned - total_paid
Payments can be:
- partial,
- multiple,
- on unrelated later dates,
- from AZ-2 or AK-2.
11.3 Fixed monthly employee
Monthly obligation:
gross_salary
leave_deduction
other_deduction
adjustment
net_payable
paid
remaining
Required formula:
net_payable =
gross_salary
- approved_deductions
+ positive_adjustments
- negative_adjustments

remaining =
net_payable - payments_allocated
Attendance/leave may provide evidence, but Finance owns the monetary
result.
No automatic deduction should occur from raw attendance without an
explicit payroll rule and auditable calculation.
11.4 Allocation
A payment may settle:
- one obligation,
- several old obligations,
- current month partially.
Use payment allocations:
employee_payment
employee_payment_allocation
Never lose the relationship between a payment and what it settled.
12. AZ-2 / AK-2 account experience
Finance → Accounts.
Two large account cards:
AZ-2
Current signed position
Inflows this period
Outflows this period

AK-2
Current signed position
Inflows this period
Outflows this period
Clicking opens full ledger:
Date
Description
Type
From/To
Production
Counterparty
Employee
Inflow
Outflow
Running Position
Reference
Negative positions are valid and visually distinct but not labeled
"debt" unless the business explicitly classifies them as debt.
Filters:
- date,
- transaction type,
- production,
- counterparty,
- employee,
- amount,
- source,
- migrated/manual/system.
13. Party / customer receivables
Replace शीट4 mini-ledgers with a generic counterparty system.
13.1 Counterparty
id
display_name
legal_name nullable
role_flags
gstin nullable
phone nullable
notes
active
13.2 Ledger
Date
Production / Invoice
Description
Charge
Receipt
Balance
Receiver (AZ/AK)
13.3 Party summary
Total charged
Total received
Outstanding
Oldest outstanding date
Last payment
Do not infer that every workbook party is a legal customer/vendor
classification during migration. Preserve UNKNOWN/MIXED until
confirmed.
14. Invoices / GST / TDS
Finance → Invoices.
This must reproduce the workbook's usable behavior while improving
structure.
14.1 Invoice entity
id
invoice_number
financial_year
invoice_date
counterparty_id
production_id nullable
gstin nullable
tax_mode             // CGST_SGST / IGST / NONE / CUSTOM
base_amount
cgst_rate
sgst_rate
igst_rate
cgst_amount
sgst_amount
igst_amount
invoice_total
tds_amount
status
notes
14.2 Invoice payments
Never create payment1 ... payment6 columns.
Use unlimited child rows:
invoice_payment
- id
- invoice_id
- receipt_transaction_id
- amount
- date
- receiver_account_id
14.3 Invoice balance
Workbook-compatible default:
balance =
invoice_total
- tds_amount
- allocated_payments
Do not allow the frontend to calculate the authoritative balance.
14.4 Tax calculation
Support workbook-observed patterns:
CGST 9% + SGST 9%
IGST 18%
but make rates explicit configuration.
The application must not silently assume the tax treatment of a future
invoice.
Provide:
Use saved tax profile
[ CGST 9 + SGST 9 ]
[ IGST 18 ]
[ No GST ]
[ Custom ]
Authorized user sees the exact rates before posting.
15. Equipment Finance ↔ Headquarters
15.1 Purchase record
equipment_purchase
- id
- vendor
- purchase_date
- description
- invoice/reference
- subtotal
- tax
- total
- amount_paid
- outstanding
- status
15.2 Purchase items
equipment_purchase_item
- purchase_id
- headquarters_item/category reference nullable
- description
- quantity
- unit_rate
- tax
- amount
15.3 Payments
Unlimited:
equipment_purchase_payment
- purchase_id
- financial_transaction_id
- payer AZ/AK
- amount
- date
15.4 Headquarters link
When an owned asset is received:
Finance purchase item
       │
       └── optional link
             ↓
Headquarters inventory item / serialized asset
No automatic stock mutation solely because a finance record exists
unless the user explicitly performs/approves a "Receive into
Headquarters" workflow.
16. General expenses
Replace Expence with a generic expense workflow.
Expense fields:
date
amount
category
description
payer AZ/AK
production nullable
counterparty nullable
employee nullable
asset/equipment nullable
attachment nullable
notes
Categories should be configurable.
Seed from observed workbook vocabulary, not hardcoded forever:
FOOD
TRANSPORT
PETROL
VEHICLE
REPAIR
EMI
EQUIPMENT
STAFF
RENTAL
INTERNET
OFFICE
OTHER
17. Quotation / costing (Varma ji)
The Varma ji sheet is primarily a quotation/costing/rate-calculation
subsystem.
Observed concepts:
Event date
Setup date
Visuals
Audio
Product
Size
Quantity
Square feet
Rate
Days
Amount
Section total
Grand total
GST
Do not mix quotation drafts into the immutable ledger.
Recommended future flow:
Quotation Draft
      ↓ approved
Production Commercials
      ↓
Contracted Revenue
      ↓
Invoice / Receivable
Finance V1 broad update should support the data boundary and link, but
quotation authoring can remain a separate follow-up unless already
present elsewhere.
18. Reconciliation Engine --- replacement for Final
This is the heart of the update.
Final currently pulls values from multiple production sheets, AZ/AK
account sheets, expenses, and other financial blocks.
Current workbook control:
Final!L8 = H9 + K9 - F9
Current result:
L8 = 0
That zero acts as a control/reconciliation signal.
SA Command must preserve the concept, not the cell coordinates.
18.1 Reconciliation result
finance_reconciliation_snapshot
- id
- as_of
- overall_result
- azeem_position
- akash_position
- receivables
- employee_payables
- invoice_receivables
- equipment_payables
- adjustments
- control_difference
- status
- calculated_at
- calculation_version
Status:
RECONCILED
WARNING
BROKEN
Default:
RECONCILED only when control_difference == 0.00
If rounding/configuration creates tolerated differences later, tolerance
must be explicit, tiny, and documented. Do not silently hide mismatch.
18.2 Integrity panel
Finance page:
Financial Integrity
✓ Reconciled

Control difference       ₹0.00
Last recalculated        20:12
Calculation version      FIN-1
If broken:
Financial Integrity
⚠ Difference detected

Difference               ₹14,500

Potential sources:
Unallocated receipt      ₹10,000
Unlinked adjustment       ₹4,500
19. Main Command Overview --- SMALL Finance section
This is intentionally small.
Azeem's confirmed first-look priorities are:
1. overall profit/loss,
2. his current position,
3. Akash's current position.
Therefore add one compact Finance strip/card to the existing Command
Overview.
19.1 Exact information hierarchy
FINANCE
──────────────────────────────────────────────────
Overall Position
+ ₹X,XX,XXX Profit
or
- ₹X,XX,XXX Loss

AZ-2                         AK-2
₹X,XX,XXX                    ₹X,XX,XXX
Azeem position               Akash position

Reconciled ✓                 Updated 20:12
                                         View Finance →
Do not show on the main Overview:
- every invoice,
- GST breakdown,
- employee balances,
- equipment purchases,
- all receivables,
- transaction charts,
- expense category tables,
- detailed owner split.
Those belong inside Finance.
19.2 Data source
Overview must consume the same server-side Finance projection used by
Finance itself.
Do not calculate Overview numbers separately.
Suggested endpoint:
GET /api/v1/finance/overview
Response:
{
  "asOf": "2026-09-24T20:12:00+05:30",
  "overallResult": "873261.35",
  "overallResultType": "PROFIT",
  "azeemPosition": "-1697522.00",
  "akashPosition": "104095.00",
  "reconciliationStatus": "RECONCILED",
  "controlDifference": "0.00"
}
The exact production meaning of overallResult must come from the
finalized reconciliation projection, not a hardcoded legacy cell.
20. Finance landing page
Main Finance page should answer the owner's questions before showing
operational detail.
Top row
Overall Profit / Loss
AZ-2 Position
AK-2 Position
Financial Integrity
Second row
Receivables
Employee Payables
Invoice Outstanding
Equipment Payables
Below
Recent Money Movement
Production Finance
Needs Attention
Needs Attention examples:
- overdue party balance,
- employee overpayment/negative payable,
- unallocated receipt,
- invoice balance mismatch,
- unreconciled transaction,
- equipment purchase unpaid,
- transaction missing production/counterparty where expected.
Use restrained SA Command visual language. Finance must feel serious,
not like a trading dashboard.
No neon. No casino-style green/red. No giant decorative charts.
21. Database schema --- recommended tables
This is a target model; align naming with existing SA Command
conventions.
finance_account
financial_transaction
journal_entry
journal_line
transaction_allocation
profit_split_rule

production_finance_profile
production_receivable
production_receipt_allocation
production_expense_link

employee_compensation_profile
employee_earning
employee_pay_obligation
employee_payment
employee_payment_allocation

counterparty
counterparty_charge
counterparty_payment_allocation

finance_invoice
finance_invoice_payment_allocation

equipment_purchase
equipment_purchase_item
equipment_purchase_payment_allocation

finance_reconciliation_snapshot
finance_projection_checkpoint

finance_migration_batch
finance_migration_row
finance_migration_issue

finance_audit_event
Avoid duplicate amount fields that can drift unless they are deliberate
immutable snapshots.
Derived balances should be projections/calculations, not editable
fields.
22. Constraints and invariants
Put important rules in both application validation and PostgreSQL
constraints where possible.
Transaction
amount > 0
currency = INR for current deployment
POSTED transaction cannot be edited
REVERSED transaction references original
idempotency_key unique for command scope
Payer/receiver
outflow requires payer
inflow requires receiver
transfer requires both and source != destination
Journal
sum(debit) = sum(credit)
no negative debit/credit line
exactly one of debit/credit > 0 per line
Allocation
allocated amount > 0
sum allocations <= payment amount
sum receipt allocations <= receipt amount
Invoice
base_amount >= 0
tax amounts >= 0
invoice_total >= 0
tds_amount >= 0
Profit split
azeem + akash = 100%
Posted data
No cascade delete of posted financial history.
23. Concurrency
Finance must be safe when two actions occur at the same time.
Examples:
- two users allocate the same client receipt,
- two employee payments settle the same outstanding,
- two equipment payments try to close the same liability,
- repeated click/API retry posts the same expense twice.
Required protections:
1. database transaction around posting,
2. idempotency key,
3. unique constraints,
4. row locking or serializable transaction for contested allocations,
5. retry on serialization failure where used,
6. optimistic versioning for editable drafts,
7. immutable posted transaction.
For high-contention allocation:
SELECT obligation FOR UPDATE
validate remaining
insert allocation
post journal
commit
or use a SERIALIZABLE transaction with bounded retry.
Do not rely on frontend button disabling.
24. Idempotency
Every write endpoint must accept an idempotency key.
Example:
POST /api/v1/finance/employee-payments
Idempotency-Key: ...
Same key + same payload:
return original successful result
Same key + different payload:
409 Conflict
This is mandatory for Tauri retries, network retry, double-clicks, and
future mobile integrations.
25. Auditability
Every posted transaction must answer:
Who?
What?
When?
Why?
How much?
Which production?
Which employee/party?
Which AZ/AK account?
What did it replace/reverse?
Where did it originate?
Store:
created_by
posted_by
timestamp
device/session context where appropriate
source
before/after for mutable drafts
reversal reference
migration provenance
Financial audit trail and security logs are related but separate.
Do not put sensitive financial payloads into ordinary application logs
unnecessarily.
26. Permissions
Suggested permissions:
FINANCE_VIEW
FINANCE_CREATE_DRAFT
FINANCE_POST
FINANCE_REVERSE
FINANCE_ADJUST
FINANCE_EXPORT
FINANCE_MIGRATE
FINANCE_RECONCILE
FINANCE_CONFIGURE
Recommended:
- owner: full access,
- trusted finance/admin: scoped write/post,
- ordinary employee: no Finance access,
- employee self-view can later expose only their own payment history
  if explicitly desired.
Sensitive operations:
- reversal,
- opening balance,
- manual adjustment,
- split-rule change,
- tax configuration,
- migration finalization
should require elevated permission and explicit confirmation.
27. API design
Overview
GET /api/v1/finance/overview
GET /api/v1/finance/reconciliation
Accounts
GET /api/v1/finance/accounts
GET /api/v1/finance/accounts/{id}
GET /api/v1/finance/accounts/{id}/ledger
POST /api/v1/finance/transfers
Transactions
GET  /api/v1/finance/transactions
GET  /api/v1/finance/transactions/{id}
POST /api/v1/finance/expenses
POST /api/v1/finance/receipts
POST /api/v1/finance/adjustments
POST /api/v1/finance/transactions/{id}/reverse
Production
GET  /api/v1/productions/{id}/finance
POST /api/v1/productions/{id}/receipts
POST /api/v1/productions/{id}/expenses
Employee
GET  /api/v1/employees/{id}/finance
POST /api/v1/employees/{id}/earnings
POST /api/v1/employees/{id}/payments
GET  /api/v1/finance/employee-payables
Party
GET /api/v1/finance/counterparties
GET /api/v1/finance/counterparties/{id}/ledger
POST /api/v1/finance/counterparties/{id}/charges
POST /api/v1/finance/counterparties/{id}/receipts
Invoice
GET  /api/v1/finance/invoices
POST /api/v1/finance/invoices
GET  /api/v1/finance/invoices/{id}
POST /api/v1/finance/invoices/{id}/payments
Equipment
GET  /api/v1/finance/equipment-purchases
POST /api/v1/finance/equipment-purchases
POST /api/v1/finance/equipment-purchases/{id}/payments
POST /api/v1/finance/equipment-purchases/{id}/link-headquarters
Migration
POST /api/v1/finance/migrations/excel/preview
POST /api/v1/finance/migrations/{id}/validate
POST /api/v1/finance/migrations/{id}/commit
GET  /api/v1/finance/migrations/{id}/issues
28. UI interaction rules
28.1 Amount entry
- Indian rupee formatting.
- Accept plain numeric typing.
- Show formatted preview.
- Backend remains authoritative.
28.2 Payer/receiver selector
Never hide this inside an advanced section.
Example:
Who paid?
○ Azeem — AZ-2
○ Akash — AK-2
28.3 Confirm screen
For meaningful financial actions show:
You are recording:

₹17,000 payment to Roshan
Paid by: Azeem / AZ-2
Date: 24 Sep 2026

This will reduce Roshan's outstanding
and update AZ-2 automatically.
Then:
[Cancel] [Confirm Payment]
This follows "what you see is what you authorize" principles.
28.4 Reversal
Never show Delete transaction.
Show:
Reverse transaction
Require:
reason
confirmation
Then create compensating transaction.
29. Excel migration strategy
Do not import the workbook directly into production tables in one pass.
Use:
RAW IMPORT
   ↓
NORMALIZATION
   ↓
MATCHING / LINKING
   ↓
PARITY VALIDATION
   ↓
HUMAN REVIEW
   ↓
COMMIT
29.1 Preserve provenance
Every imported record should keep:
source_workbook
source_sheet
source_row
source_cell/range where practical
raw_values_json
migration_batch_id
Never discard the original source mapping.
29.2 Sheet mapping
Production sheets
Jan 26
JUL+DEC
Jul-Dec
jully dec P3 Led
Sound Jul+dec
All Date 26
Map:
Date → production/event date
Venue → venue
Name → customer/party/reference
Total → contracted amount
Add → receipt allocation type LEGACY_ADD
Payment → receipt allocation type LEGACY_PAYMENT
bal → expected legacy outstanding
Expense → production expense total
Buget → expected legacy realized result
employee columns → earning/payment records
Owner accounts
az 26
az-2
G Pay Aakash 26
Ak-2
Map into owner-account ledger.
Party ledgers
शीट4 → counterparties + charges + payments.
GST
GST → invoices + tax + TDS + payment allocations.
LED/Sound
→ equipment purchases + purchase payments.
Final
Do not import Final as ordinary transactions.
Use it as golden expected output for reconciliation/parity.
30. Migration deduplication
The same real transaction may exist in multiple sheets.
Example:
event sheet payment
+
AK-2 From entry
must become one canonical transaction with two projections, not two
receipts.
Dedup candidate key:
date
amount
normalized party/name
direction
owner account
production proximity
legacy source type
But do not auto-merge solely on fuzzy matching.
Use confidence:
EXACT
HIGH_CONFIDENCE
REVIEW_REQUIRED
DISTINCT
Migration UI:
Potential duplicate

JUL+DEC row 8
Govind ₹10,000 receipt

Ak-2 row 20
Govind ₹10,000 From

[Link as same transaction] [Keep separate]
For known exact patterns, create deterministic rules and tests.
31. Golden workbook parity fixtures
Before replacing Excel, create a frozen copy of the supplied workbook as
a test fixture and assert the new system reproduces key outputs.
Known baseline examples from the analyzed workbook:
Jan 26
Total              ₹6,691,600
Add                  ₹801,580
Payment            ₹5,615,380
Expense            ₹3,857,755
Budget/Result      ₹1,603,683
Akash 35%            ₹561,289.05
Azeem 65%          ₹1,042,393.95
JUL+DEC
Total              ₹1,477,300
Add                  ₹356,000
Payment              ₹970,320
Outstanding          ₹150,980
Expense            ₹1,755,154
Budget/Result        -₹428,834
Akash share          -₹150,091.90
Azeem share          -₹278,742.10
P3
Profit/Result         ₹168,630
Akash 35%              ₹59,020.50
Azeem 65%             ₹109,609.50
AZ-2
Credit              ₹2,457,885
Expense             ₹4,155,407
Position           -₹1,697,522
AK-2 current observed block
From                  ₹529,760
To                    ₹425,665
Position              ₹104,095
Final reconciliation control
L8 = ₹0
These are migration fixtures, not values to hardcode into
production.
32. Proof harness
Finance cannot be marked complete because the UI looks correct.
It must pass deterministic proof.
32.1 Unit tests
At minimum:
production outstanding
realized margin
contracted margin
65/35 split
employee earned/paid/outstanding
monthly salary deduction
partial employee payment
multi-payment invoice
TDS balance
equipment outstanding
AZ/AK signed balance
transfer
reversal
overpayment handling
rounding
32.2 Integration tests
Use real PostgreSQL via Testcontainers.
Test:
- transaction atomicity,
- idempotency,
- FK/unique/check constraints,
- reversal,
- concurrent allocation,
- serialization retry where used,
- projection rebuild.
32.3 Workbook parity tests
Import frozen workbook into a clean DB.
Assert:
legacy production totals match
legacy receipts match
legacy outstanding match
legacy employee balances match where source semantics are unambiguous
AZ/AK positions match
invoice totals/balances match
equipment purchase totals match
Final control difference matches
Any mismatch must output a report:
Metric
Workbook value
SA Command value
Difference
Source sheet/cells
Reason
32.4 Concurrency tests
Examples:
Double payment allocation
Two threads try to allocate the same remaining ₹10,000.
Expected:
one succeeds
other fails/retries
total allocated never > ₹10,000
Duplicate request
Same idempotency key sent 20 times.
Expected:
one financial transaction
one journal
one account effect
Reversal race
Payment and reversal race.
Expected:
valid deterministic state
no orphan allocations
no double reversal
33. Required proof report
Create endpoint or test artifact:
GET /api/v1/finance/evaluation/report
or generate CI report.
Required output:
Workbook parity
Account integrity
Journal balance
Allocation integrity
Idempotency
Concurrency
Reversal safety
Reconciliation
Migration unresolved rows
Zero-tolerance gates:
0 duplicate financial effects
0 unbalanced posted journals
0 over-allocated payments
0 posted transaction hard-deletes
0 unauthorized posting paths
0 unexplained reconciliation difference
0 money calculations using float/double
Do not claim "proved" until these gates actually pass.
34. Projection rebuild
Every dashboard number must be rebuildable from canonical posted
transactions.
Provide a maintenance/test operation:
rebuildFinanceProjections()
Flow:
canonical posted ledger
      ↓
clear/rebuild projections
      ↓
compare before/after
If rebuilding changes balances, the system has a projection bug.
This is a critical deterministic test.
35. Reconciliation design
Use multiple reconciliation layers.
Layer 1 --- transaction integrity
journal debits == credits
Layer 2 --- allocation integrity
payment amount >= allocations
receipt amount >= allocations
Layer 3 --- domain balances
production outstanding
employee outstanding
invoice outstanding
equipment outstanding
Layer 4 --- owner positions
AZ-2
AK-2
Layer 5 --- legacy parity during migration
SA Command projection
vs
Excel expected values
Layer 6 --- business control
Replacement for Final.
36. Reporting
Finance reports:
Overall Finance
Production Profitability
Production Collections
Outstanding Receivables
Employee Payables
Employee Payment History
AZ-2 Ledger
AK-2 Ledger
Invoices / GST / TDS
Equipment Purchases
General Expenses
Reconciliation
Exports:
- CSV
- Excel-compatible export if needed
- PDF only where business requires a fixed report
Exports are views of system truth; exported files must not become a
second writable source of truth.
37. Search and filtering
Finance data will grow quickly.
All major lists need server-side:
- date range,
- text search,
- account,
- production,
- party,
- employee,
- status,
- type,
- amount range,
- outstanding only,
- source.
Use pagination.
Do not load every transaction into Tauri memory.
38. Performance
Indexes:
financial_transaction(effective_date)
financial_transaction(type, effective_date)
financial_transaction(production_id, effective_date)
financial_transaction(employee_id, effective_date)
financial_transaction(counterparty_id, effective_date)
journal_line(owner_account_id)
employee_payment_allocation(obligation_id)
invoice_payment_allocation(invoice_id)
production_receipt_allocation(production_id)
Use database aggregation/materialized projections only where needed.
Start with normal indexed SQL + projection tables. Do not introduce
Kafka/Redis/event-stream infrastructure for this local/private
application without measured need.
39. Backend package structure
Suggested:
com.saproduction.command.finance
├── FinanceModule.java
├── api
│   ├── FinanceOverviewController
│   ├── FinanceTransactionController
│   ├── FinanceAccountController
│   ├── InvoiceController
│   └── FinanceMigrationController
├── application
│   ├── PostExpenseService
│   ├── PostReceiptService
│   ├── EmployeeFinanceService
│   ├── InvoiceService
│   ├── EquipmentFinanceService
│   ├── ReversalService
│   └── ReconciliationService
├── domain
│   ├── transaction
│   ├── ledger
│   ├── account
│   ├── employee
│   ├── receivable
│   ├── invoice
│   ├── equipment
│   └── reconciliation
├── infrastructure
│   ├── persistence
│   ├── migration
│   └── projection
└── reporting
Keep controllers thin.
Business invariants belong in domain/application services, not React.
40. Events inside SA Command
Useful internal events:
FinanceTransactionPosted
FinanceTransactionReversed
EmployeeEarningRecorded
EmployeePaymentPosted
ProductionReceiptPosted
ProductionExpensePosted
InvoiceIssued
InvoicePaymentPosted
EquipmentPurchaseRecorded
EquipmentPaymentPosted
FinanceReconciliationChanged
Use transactional publication/outbox semantics so downstream projections
cannot silently miss events after the core transaction commits.
For the current modular monolith, Spring Modulith's event publication
registry is a strong optional fit.
Do not use an external message broker unless required later.
41. Open-source / web resources to reuse
License policy for this module:
Prefer Apache-2.0, MIT, BSD, ISC production dependencies. Do not
pull GPL/AGPL/SSPL/proprietary accounting code into SA Command without
explicit license review.

41.1 Backend / architecture
Spring Modulith --- Apache-2.0
Use for:
- module boundaries,
- module verification,
- application events,
- event publication registry,
- reliable internal event delivery.
Docs: https://docs.spring.io/spring-modulith/reference/
Repository: https://github.com/spring-projects/spring-modulith
Important event docs:
https://docs.spring.io/spring-modulith/reference/events.html
Use selectively. Do not rewrite the whole app merely to adopt it.
PostgreSQL --- PostgreSQL License
Use existing PostgreSQL for:
- constraints,
- foreign keys,
- unique constraints,
- row locking,
- transactional posting,
- serializable isolation for high-risk concurrent operations.
Constraints: https://www.postgresql.org/docs/18/ddl-constraints.html
Transaction isolation:
https://www.postgresql.org/docs/17/transaction-iso.html
The key point: critical financial correctness must be enforced in the
database as well as application code.
Apache POI --- Apache-2.0
Use for:
- reading the legacy .xlsx,
- extracting sheet/row/cell values,
- migration preview,
- provenance mapping,
- deterministic import tests.
Legal/license: https://poi.apache.org/legal.html
Do not use Excel itself as the runtime calculation engine.
Spring Batch --- Apache-2.0
Optional for the one-time/large migration pipeline if simple services
become too fragile.
Use for:
- chunked migration,
- restartable import,
- validation stages,
- migration job metadata.
Docs: https://docs.spring.io/spring-batch/reference/
Repository: https://github.com/spring-projects/spring-batch
Do not add it if the import remains small enough for a simpler explicit
migration service.
JSR 354 / Moneta --- Apache-2.0
Optional.
Use if it reduces money/currency mistakes cleanly.
Repository: https://github.com/JavaMoney/jsr354-ri
Otherwise use a strict internal Money value object backed by
BigDecimal.
Apache Commons CSV --- Apache-2.0
Use for clean CSV exports/import staging if needed.
https://commons.apache.org/proper/commons-csv/
Apache Fineract --- Apache-2.0 --- REFERENCE, not a dependency by default
Fineract is a large banking platform.
Use it to study:
- journal-entry patterns,
- transaction reversal concepts,
- accounting-entry structure,
- financial audit discipline.
Repository: https://github.com/apache/fineract
Docs: https://fineract.apache.org/docs/
Do not embed or fork the whole platform for SA Command. The business
is much smaller and the domain is event operations, not core banking.
41.2 Frontend
shadcn/ui --- MIT
Already a good fit for SA Command.
Use:
- Dialog
- AlertDialog
- Sheet
- Tabs
- Card
- Table
- Popover
- Command
- Select
- Tooltip
- Badge
- Calendar
https://github.com/shadcn-ui/ui
TanStack Table --- MIT
Use for finance ledgers:
- sorting,
- filtering,
- pagination,
- column visibility,
- row models.
https://github.com/TanStack/table
Do not build a spreadsheet clone. Use a proper ledger table.
TanStack Query --- MIT
Use for:
- finance server state,
- cache invalidation,
- mutations,
- pagination,
- refetch after posting.
https://github.com/TanStack/query
React Hook Form --- MIT
Use for:
- transaction forms,
- invoice forms,
- expense entry,
- salary/payment entry.
https://github.com/react-hook-form/react-hook-form
Zod --- MIT
Use for frontend validation/schema alignment.
https://github.com/colinhacks/zod
Backend validation remains authoritative.
Recharts --- MIT
Use sparingly for:
- profit/loss trend,
- collections vs expense,
- receivable aging if useful.
https://github.com/recharts/recharts
Do not turn Finance into a chart wall.
Lucide --- ISC
Use existing icon language.
https://github.com/lucide-icons/lucide
41.3 Testing
Testcontainers Java --- MIT
Use real PostgreSQL integration tests.
https://github.com/testcontainers/testcontainers-java
Playwright --- Apache-2.0
Use for end-to-end flows:
- post expense,
- payer selection,
- partial employee payment,
- invoice multi-payment,
- reversal,
- Finance Overview refresh.
https://github.com/microsoft/playwright
41.4 Security guidance
OWASP Transaction Authorization
Use its principle that users must see/acknowledge significant
transaction data and that authorization is enforced server-side.
https://cheatsheetseries.owasp.org/cheatsheets/Transaction_Authorization_Cheat_Sheet.html
OWASP Logging
Use for security logging discipline while keeping financial audit trails
purpose-specific.
https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html
42. What NOT to import
Do not add an open-source ERP/accounting system just because it has
finance screens.
Avoid copying code from:
- GPL/AGPL ERP projects,
- restrictive spreadsheet/data-grid products,
- random accounting GitHub repos with unclear licenses,
- abandoned "double-entry" packages with no deterministic tests.
SA Command already has its own domain model and UX.
Borrow patterns, not unnecessary platform complexity.
43. Frontend screen map
Finance
├── Overview
├── Transactions
├── Accounts
│   ├── AZ-2
│   └── AK-2
├── Production Finance
├── Employee Payables
├── Parties / Receivables
├── Invoices / GST
├── Equipment Purchases
├── Expenses
├── Reconciliation
└── Import / Migration   [admin only]
44. Finance navigation behavior
Finance Overview should open instantly from local/private deployment.
Do not make the owner wait for huge ledger queries.
Use:
finance_overview_projection
updated transactionally/asynchronously with reliable event publication.
Detailed screens query their own data.
45. Error handling
Financial errors must be explicit.
Examples:
Payment exceeds remaining employee payable.
Receipt exceeds invoice balance.
This transaction was already posted.
The selected production no longer accepts edits.
This payment was changed by another user. Refresh and retry.
Reconciliation failed after posting; transaction rolled back.
Never show:
Something went wrong.
for a financial invariant failure.
46. Migration issue handling
Not every workbook cell is clean.
Expected issues:
- mixed date formats,
- text dates,
- misspelled employee names,
- duplicate names,
- inconsistent employee column orientation,
- negative employee balances,
- manual adjustments,
- cells containing comments/labels instead of numbers,
- same transaction repeated across sheets,
- old and new account sheets overlapping,
- GST rows with ad-hoc notes.
Migration must produce an issue queue.
Example:
REVIEW REQUIRED
Source: Jan 26!N21
Value: ₹1,000
Employee mapping: Roshan
Potential linked account transaction: ...
Confidence: 0.92
Do not silently "fix" source data.
47. Legacy compatibility view
During rollout, Finance should optionally expose a read-only Excel
Parity panel for trusted users.
Example:
Legacy view

Total       ₹...
Add         ₹...
Payment     ₹...
Balance     ₹...
Expense     ₹...
Budget      ₹...
Akash 35%   ₹...
Azeem 65%   ₹...
This helps Azeem trust the migration.
Once confidence is established, the main UI remains normalized.
48. Cutover plan
Phase F0 --- freeze and prove understanding
- keep original workbook unchanged,
- store checksum,
- create workbook fixture,
- document formulas,
- map names/entities,
- define parity metrics.
Phase F1 --- Finance core
- accounts,
- transactions,
- journal,
- payer/receiver,
- idempotency,
- reversal,
- audit.
Phase F2 --- Production finance
- receipts,
- expenses,
- realized/contracted margin,
- AZ/AK propagation.
Phase F3 --- Employee finance
- work-based earning,
- fixed salary,
- deductions,
- partial payments,
- allocations.
Phase F4 --- Parties / receivables
- counterparty ledger,
- charges,
- receipts,
- outstanding.
Phase F5 --- Invoices / GST / TDS
- invoice model,
- tax profiles,
- unlimited payments,
- balance.
Phase F6 --- Equipment finance
- LED/Sound purchase migration,
- Headquarters links,
- outstanding/payments.
Phase F7 --- Final / reconciliation + Command Overview
- reconciliation engine,
- overall result,
- AZ-2,
- AK-2,
- control difference,
- small Overview card.
Phase F8 --- Migration
- preview,
- dedup,
- review queue,
- parity report,
- commit.
Phase F9 --- hardening
- concurrency,
- failure injection,
- backup/restore test,
- projection rebuild,
- release packaging.
Do not start by building every screen.
Build financial truth first.
49. Implementation order inside each phase
For every Finance capability:
1. Domain rule
2. DB migration/constraints
3. Application service
4. Unit tests
5. Integration tests
6. API
7. UI
8. Playwright
9. Reconciliation/parity test
10. Documentation
Do not let UI define backend behavior.
50. Acceptance scenarios
Scenario A --- partial employee payment
Given:
Roshan earned total ₹30,000
paid = ₹0
When:
₹17,000 paid by AZ-2
Then:
Roshan paid = ₹17,000
Roshan remaining = ₹13,000
AZ-2 decreases by ₹17,000
production expense does not duplicate
audit exists
journal balanced
Then when:
₹13,000 paid by AK-2
Then:
Roshan remaining = ₹0
AZ-2 retains first effect
AK-2 decreases by ₹13,000
Scenario B --- event advance
Given:
contracted ₹60,000
expense ₹10,000
When:
advance ₹20,000 received into AK-2
Then:
received ₹20,000
outstanding ₹40,000
realized margin ₹10,000
contracted margin ₹50,000
AK-2 +₹20,000
Scenario C --- payer choice
When user records:
Food expense ₹6,500
UI must block confirmation until:
Azeem or Akash selected
If Azeem:
AZ-2 -₹6,500
AK-2 unchanged
Scenario D --- negative owner position
Given:
AZ-2 = ₹10,00,000
When:
₹20,00,000 equipment payment posted by AZ-2
Then:
AZ-2 = -₹10,00,000
Transaction succeeds if otherwise valid.
Do not reject because balance became negative.
Scenario E --- invoice installments
Invoice:
₹4,58,784
TDS ₹7,776
Payments:
₹1,00,000
₹51,592
₹30,000
₹1,69,416
Balance is derived from allocations, not fixed payment columns.
Scenario F --- duplicate API retry
Send same expense command 10 times with same idempotency key.
Expected:
one transaction
one AZ/AK effect
one expense
Scenario G --- reversal
Post ₹10,000 receipt into AK-2 by mistake.
Reverse.
Expected:
original remains visible
reversal visible
net AK-2 effect = 0
receivable restored
audit complete
51. Overview acceptance
Main SA Command Overview Finance card passes only if:
- overall profit/loss comes from Finance projection,
- AZ-2 matches Finance account,
- AK-2 matches Finance account,
- reconciliation status comes from Finance,
- no duplicate calculation exists in Overview code,
- clicking View Finance opens Finance,
- card refreshes after a posted transaction,
- card does not expose sensitive detail beyond the intended summary.
52. Security acceptance
- all Finance endpoints authenticated,
- authorization server-side,
- employee cannot escalate via client payload,
- payer account cannot be spoofed without permission,
- posted transaction mutation rejected,
- reversal permission enforced,
- migration endpoints owner/admin only,
- audit records cannot be edited from normal APIs,
- SQL injection tests,
- validation on amount/date/reference fields,
- no financial secrets dumped to logs.
53. Backup / recovery
Because this replaces the workbook as operating truth:
- database backup must be tested,
- restore must be tested,
- Finance projection rebuild must be tested after restore,
- original posted ledger must survive projection deletion,
- migrations must be reversible before final commit,
- Excel source remains archived read-only.
Do not make the application the only copy of financial history without a
recovery plan.
54. Observability
Track:
finance.transactions.posted
finance.transactions.reversed
finance.idempotency.replays
finance.reconciliation.failures
finance.serialization.retries
finance.migration.unresolved
finance.projection.rebuild.duration
No need for a public monitoring stack if the private deployment does not
have one; structured application metrics/logs are enough initially.
55. UX tone
Finance is an owner/operator tool.
Use labels like:
Received
Paid
Remaining
Who paid?
Who received?
Outstanding
Profit
Loss
Azeem position
Akash position
Reconciled
Avoid forcing:
Debit
Credit
Contra
Journal
Sub-ledger
onto normal users.
Those can exist internally or in an advanced audit view.
56. What counts as DONE
Finance broad update is not done until all of the following are true.
Core
- immutable posted transactions
- reversal
- idempotency
- AZ-2
- AK-2
- payer/receiver mandatory
- BigDecimal money
- balanced journal
- audit
Production
- receipts
- advances
- expenses
- outstanding
- realized margin
- contracted margin
- 65/35 profit projection
Employee
- work-based earning
- fixed salary
- leave deduction
- partial payments
- allocations
- running balance
Party
- generic counterparty
- charges
- receipts
- outstanding ledger
GST
- invoice
- CGST/SGST
- IGST
- TDS
- unlimited payments
- remaining balance
Equipment
- purchase
- payment
- outstanding
- Headquarters reference
Reconciliation
- Final replacement
- control difference
- reconciliation status
- projection rebuild
- workbook parity report
Overview
- overall profit/loss
- AZ-2
- AK-2
- reconciliation status
- View Finance
Proof
- unit suite
- PostgreSQL integration suite
- concurrency suite
- idempotency suite
- Playwright finance flows
- workbook migration parity
- zero duplicate financial effects
- zero unexplained reconciliation difference
No README, UI badge, PR description, or demo may claim "Finance
complete / validated / proved" until these gates pass.
57. Final architecture
                         SA COMMAND

Productions      People/Payroll      Headquarters
     │                 │                  │
     └─────────────────┼──────────────────┘
                       │
                       ▼
                FINANCE APPLICATION
                       │
             Commands + Validation
                       │
                       ▼
              FINANCIAL TRANSACTION
                       │
                       ▼
               BALANCED JOURNAL
                       │
        ┌──────────────┼───────────────┐
        │              │               │
        ▼              ▼               ▼
      AZ-2           AK-2         Domain Allocations
        │              │               │
        │              │       ┌───────┼────────┐
        │              │       │       │        │
        │              │   Production Employee Party
        │              │       │       │        │
        │              │    Invoice  Equipment  │
        └──────────────┴──────────┬──────────────┘
                                  │
                                  ▼
                         RECONCILIATION ENGINE
                                  │
                       ┌──────────┴──────────┐
                       ▼                     ▼
                 FINANCE OVERVIEW       COMMAND OVERVIEW
                 full control plane     small first-look card
58. Final product principle
The workbook must remain recognizable in its results, but not in its
complexity.
The target is:
Excel:
one real event
→ several manual entries
→ several formulas
→ Final

SA Command:
one real event
→ one validated transaction
→ automatic linked projections
→ reconciliation
→ Overview
The Finance module succeeds when Azeem can stop maintaining the
interconnected Excel chain without losing the control, balances,
employee settlement behavior, party history, equipment finance, GST
records, or final overall position that the workbook currently gives
him.
Simpler interaction. Same or stronger financial truth. Full
traceability. No silent assumptions.