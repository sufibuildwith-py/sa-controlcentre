# SA Command — Financial Rules & Invariants

Core accounting and financial rules that Eve must respect without exception.

## 1. Fundamental Distinctions
- **Earned != Paid** (`earned != paid`):
  - `earned`: Sum of all posted employee obligations (`finance_employee_obligations`).
  - `paid`: Sum of all posted payment allocations (`finance_employee_payment_allocations`).
  - `outstanding = earned - paid`.
- **Employee Earning ≠ Employee Payment**:
  - Earning creates an obligation (liability).
  - Payment settles an obligation with actual cash/bank outflow.
- **Invoice ≠ Direct Party Charge**:
  - Invoices are formal GST documents with tax calculations.
  - Direct party charges are immediate customer/vendor ledger debits.
- **Billing Export ≠ Money Posting**:
  - Generating or issuing a bill/invoice never alters cash or bank account balances.
  - Money movement requires a posted transaction.

## 2. Owner Attribution
- Outflow payments MUST have an explicit owner payer account:
  - `AZ-2`: Azeem Khan
  - `AK-2`: Akash
- Eve must never infer the payer account from the logged-in session user.

## 3. Immutability & Double-Entry Integrity
- Balances are NEVER modified directly by setting `balance = balance - X`.
- Balances are derived dynamically from posted transactions in `finance_transactions`.
