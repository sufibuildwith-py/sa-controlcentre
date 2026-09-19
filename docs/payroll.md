# Payroll policy and immutability

SA Command stores money only as signed integer minor units. ₹3,000 is persisted and transported as `300000`; floating point is never used for salary arithmetic.

The default demo strategy is `PER_WORKING_DAY` with 26 configured working days. An `ABSENT` record contributes one unpaid day and `HALF_DAY` contributes 0.5; other attendance states contribute zero. The deduction is `base salary × unpaid days ÷ working days`, rounded once to the nearest minor unit with `HALF_UP`. The alternative `NONE` policy makes no attendance deduction. These are demonstration defaults, not a claim about SA Production's final HR policy; both are isolated behind `PayrollCalculationPolicy` and configured in `application.yml` or environment variables.

Adjustments require a reason. BONUS, OVERTIME, DEDUCTION and ADVANCE accept positive magnitudes. CORRECTION and OTHER may be positive or negative. Net salary is the snapshotted base less attendance deduction, plus overtime and bonus, less advances, plus signed manual adjustments.

The state machine is `DRAFT → CALCULATED → APPROVED → PAID → LOCKED`. Recalculation and adjustment are permitted only while CALCULATED. Individual payment updates are allowed while APPROVED; when no pending item remains the period becomes PAID. Only PAID can be locked. LOCKED is terminal for normal V1 writes: later employee salary or attendance changes cannot modify the historical item snapshot.
