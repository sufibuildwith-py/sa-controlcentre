package com.saproduction.command.payroll;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.auth.UserRepository;
import com.saproduction.command.communication.DomainEventService;
import com.saproduction.command.employee.*;
import com.saproduction.command.finance.FinanceCommands;
import com.saproduction.command.finance.FinancePostingService;
import com.saproduction.command.shared.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayrollService {
  public record AdjustmentInput(
      @NotNull UUID itemId,
      @NotNull PayrollAdjustment.Type type,
      long amountMinor,
      @NotBlank @Size(max = 500) String reason) {}

  public record PaymentInput(
      @NotNull UUID requestId,
      @Positive long amountMinor,
      @NotNull @PastOrPresent Instant paidAt,
      @NotNull PayrollPayment.Method paymentMethod,
      String payerAccount,
      @Size(max = 160) String reference,
      @Size(max = 500) String note) {
    public PaymentInput(
        UUID requestId,
        long amountMinor,
        Instant paidAt,
        PayrollPayment.Method paymentMethod,
        String reference,
        String note) {
      this(requestId, amountMinor, paidAt, paymentMethod, null, reference, note);
    }
  }

  public record AdjustmentView(
      UUID id,
      PayrollAdjustment.Type type,
      long amountMinor,
      String reason,
      UUID createdBy,
      Instant createdAt) {}

  public record PaymentView(
      UUID id,
      long amountMinor,
      Instant paidAt,
      PayrollPayment.Method paymentMethod,
      String reference,
      String note,
      UUID recordedBy,
      Instant createdAt) {}

  public record ItemView(
      UUID id,
      UUID employeeId,
      String employeeName,
      String employeeRole,
      String salaryCurrency,
      long baseSalaryMinor,
      long attendanceDeductionMinor,
      long overtimeMinor,
      long bonusMinor,
      long advanceDeductionMinor,
      long manualAdjustmentMinor,
      long grossEarnings,
      long deductions,
      long netSalaryMinor,
      long netPayable,
      long totalPaid,
      long remaining,
      PayrollItem.PaymentStatus paymentStatus,
      Instant paidAt,
      PaymentView lastPayment,
      List<PaymentView> payments,
      List<AdjustmentView> adjustments) {}

  public record View(
      UUID id,
      int year,
      int month,
      PayrollPeriod.Status status,
      String policy,
      long totalMinor,
      long paidMinor,
      long pendingMinor,
      long remainingMinor,
      long paidCount,
      long partiallyPaidCount,
      long unpaidCount,
      List<ItemView> items,
      Instant calculatedAt,
      Instant approvedAt,
      Instant paidAt,
      Instant lockedAt) {}

  public record PeriodSummary(
      UUID id,
      int year,
      int month,
      PayrollPeriod.Status status,
      String policy,
      long totalMinor,
      long paidMinor,
      long pendingMinor,
      long remainingMinor,
      long paidCount,
      long partiallyPaidCount,
      long unpaidCount,
      Instant calculatedAt,
      Instant approvedAt,
      Instant paidAt,
      Instant lockedAt) {}

  private final PayrollPeriodRepository periods;
  private final PayrollItemRepository items;
  private final PayrollAdjustmentRepository adjustments;
  private final PayrollPaymentRepository payments;
  private final EmployeeRepository employees;
  private final PayrollCalculationPolicy policy;
  private final UserRepository users;
  private final AuditService audit;
  private final DomainEventService events;
  private final EntityManager entityManager;
  private final FinancePostingService financePosting;

  public PayrollService(
      PayrollPeriodRepository periods,
      PayrollItemRepository items,
      PayrollAdjustmentRepository adjustments,
      PayrollPaymentRepository payments,
      EmployeeRepository employees,
      PayrollCalculationPolicy policy,
      UserRepository users,
      AuditService audit,
      DomainEventService events,
      EntityManager entityManager,
      FinancePostingService financePosting) {
    this.periods = periods;
    this.items = items;
    this.adjustments = adjustments;
    this.payments = payments;
    this.employees = employees;
    this.policy = policy;
    this.users = users;
    this.audit = audit;
    this.events = events;
    this.entityManager = entityManager;
    this.financePosting = financePosting;
  }

  @Transactional(readOnly = true)
  public List<PeriodSummary> list() {
    return periods.summaries().stream()
        .map(
            s -> {
              long remaining = Math.max(s.getTotalMinor() - s.getPaidMinor(), 0);
              return new PeriodSummary(
                  s.getId(),
                  s.getYear(),
                  s.getMonth(),
                  PayrollPeriod.Status.valueOf(s.getStatus()),
                  policy.name(),
                  s.getTotalMinor(),
                  s.getPaidMinor(),
                  remaining,
                  remaining,
                  s.getPaidCount(),
                  s.getPartiallyPaidCount(),
                  s.getUnpaidCount(),
                  s.getCalculatedAt(),
                  s.getApprovedAt(),
                  s.getPaidAt(),
                  s.getLockedAt());
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public List<View> listForEmployee(UUID employeeId) {
    return periods.findAllByOrderByYearDescMonthDesc().stream()
        .map(this::view)
        .filter(p -> p.items().stream().anyMatch(i -> i.employeeId().equals(employeeId)))
        .map(
            p -> {
              var own = p.items().stream().filter(i -> i.employeeId().equals(employeeId)).toList();
              long total = own.stream().mapToLong(ItemView::netPayable).sum(),
                  paid = own.stream().mapToLong(ItemView::totalPaid).sum(),
                  remaining = own.stream().mapToLong(ItemView::remaining).sum();
              return new View(
                  p.id(),
                  p.year(),
                  p.month(),
                  p.status(),
                  p.policy(),
                  total,
                  paid,
                  remaining,
                  remaining,
                  own.stream()
                      .filter(i -> i.paymentStatus() == PayrollItem.PaymentStatus.PAID)
                      .count(),
                  own.stream()
                      .filter(i -> i.paymentStatus() == PayrollItem.PaymentStatus.PARTIALLY_PAID)
                      .count(),
                  own.stream()
                      .filter(i -> i.paymentStatus() == PayrollItem.PaymentStatus.UNPAID)
                      .count(),
                  own,
                  p.calculatedAt(),
                  p.approvedAt(),
                  p.paidAt(),
                  p.lockedAt());
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public View get(UUID id) {
    return view(period(id));
  }

  @Transactional(readOnly = true)
  public ItemView paymentHistory(UUID periodId, UUID itemId) {
    period(periodId);
    PayrollItem value = item(periodId, itemId);
    String role = employees.findById(value.employeeId).map(e -> e.roleTitle).orElse("Employee");
    return itemView(value, payments.totalFor(value.id), role, true);
  }

  @Transactional
  public View calculate(int year, int month) {
    YearMonth ym;
    try {
      ym = YearMonth.of(year, month);
    } catch (Exception e) {
      throw ApiException.badRequest("INVALID_PAYROLL_PERIOD", "Payroll month is invalid.");
    }
    PayrollPeriod p =
        periods
            .findByYearAndMonth(year, month)
            .orElseGet(
                () -> {
                  PayrollPeriod x = new PayrollPeriod();
                  x.year = year;
                  x.month = month;
                  return periods.saveAndFlush(x);
                });
    if (p.status != PayrollPeriod.Status.DRAFT && p.status != PayrollPeriod.Status.CALCULATED)
      throw state(p);
    boolean recalculation = p.status == PayrollPeriod.Status.CALCULATED;
    for (Employee employee :
        employees.findAllByStatusNotOrderByDisplayName(Employee.Status.INACTIVE)) {
      PayrollItem item =
          items.findByPayrollPeriodIdAndEmployeeId(p.id, employee.id).orElseGet(PayrollItem::new);
      item.payrollPeriodId = p.id;
      item.employeeId = employee.id;
      item.employeeNameSnapshot = employee.displayName;
      item.salaryCurrency = employee.salaryCurrency;
      item.baseSalaryMinor = employee.baseSalaryMinor;
      item.attendanceDeductionMinor = policy.attendanceDeduction(employee, ym);
      if (item.paymentStatus == null) item.paymentStatus = PayrollItem.PaymentStatus.UNPAID;
      items.saveAndFlush(item);
      recompute(item);
    }
    p.status = PayrollPeriod.Status.CALCULATED;
    p.calculatedAt = Instant.now();
    periods.save(p);
    var result = view(p);
    audit.record(
        "PAYROLL",
        recalculation ? "PAYROLL_RECALCULATED" : "PAYROLL_CALCULATED",
        p.id.toString(),
        null,
        result);
    return result;
  }

  @Transactional
  public View adjust(UUID periodId, AdjustmentInput in) {
    PayrollPeriod p = period(periodId);
    require(p, PayrollPeriod.Status.CALCULATED);
    PayrollItem item = item(periodId, in.itemId());
    if (in.amountMinor() == 0)
      throw ApiException.badRequest("INVALID_ADJUSTMENT", "Adjustment amount cannot be zero.");
    if (EnumSet.of(
                PayrollAdjustment.Type.BONUS,
                PayrollAdjustment.Type.DEDUCTION,
                PayrollAdjustment.Type.OVERTIME,
                PayrollAdjustment.Type.ADVANCE)
            .contains(in.type())
        && in.amountMinor() < 0)
      throw ApiException.badRequest(
          "INVALID_ADJUSTMENT", "This adjustment type requires a positive amount.");
    PayrollAdjustment a = new PayrollAdjustment();
    a.payrollItemId = item.id;
    a.type = in.type();
    a.amountMinor = in.amountMinor();
    a.reason = in.reason().trim();
    a.createdBy = currentUser();
    adjustments.save(a);
    recompute(item);
    audit.record(
        "PAYROLL",
        "PAYROLL_ADJUSTMENT_ADDED",
        periodId.toString(),
        null,
        Map.of(
            "itemId", item.id, "type", a.type, "amountMinor", a.amountMinor, "reason", a.reason));
    return view(p);
  }

  @Transactional
  public View approve(UUID id) {
    PayrollPeriod p = period(id);
    require(p, PayrollPeriod.Status.CALCULATED);
    p.status = PayrollPeriod.Status.APPROVED;
    p.approvedAt = Instant.now();
    var all = items.findAllByPayrollPeriodIdOrderByEmployeeNameSnapshot(id);
    if (!all.isEmpty() && all.stream().allMatch(x -> x.netSalaryMinor == 0)) {
      p.status = PayrollPeriod.Status.PAID;
      p.paidAt = Instant.now();
      all.forEach(
          x -> {
            x.paymentStatus = PayrollItem.PaymentStatus.PAID;
            items.save(x);
          });
    }
    periods.save(p);

    if (financePosting != null) {
      LocalDate effectiveDate = YearMonth.of(p.year, p.month).atEndOfMonth();
      for (PayrollItem item : all) {
        if (item.netSalaryMinor > 0) {
          UUID salaryKey =
              UUID.nameUUIDFromBytes(
                  ("payroll-item-salary-" + item.id).getBytes(StandardCharsets.UTF_8));
          BigDecimal gross =
              BigDecimal.valueOf(
                      Math.addExact(
                          Math.addExact(item.baseSalaryMinor, item.overtimeMinor), item.bonusMinor))
                  .movePointLeft(2);
          BigDecimal deductions =
              BigDecimal.valueOf(
                      Math.addExact(item.attendanceDeductionMinor, item.advanceDeductionMinor))
                  .movePointLeft(2);
          BigDecimal adjustment = BigDecimal.valueOf(item.manualAdjustmentMinor).movePointLeft(2);
          String desc =
              String.format(
                  "Monthly salary accrual for %s (%02d/%d)",
                  item.employeeNameSnapshot, p.month, p.year);
          financePosting.salary(
              new FinanceCommands.Salary(
                  salaryKey,
                  item.employeeId,
                  item.id,
                  gross,
                  deductions,
                  adjustment,
                  effectiveDate,
                  desc));
        }
      }
    }

    var result = view(p);
    audit.record(
        "PAYROLL",
        "PAYROLL_APPROVED",
        id.toString(),
        null,
        Map.of("periodId", id, "status", p.status));
    return result;
  }

  @Transactional
  public View recordPayment(UUID periodId, UUID itemId, PaymentInput in) {
    entityManager
        .createNativeQuery("select pg_advisory_xact_lock(hashtext(cast(?1 as text)))")
        .setParameter(1, in.requestId())
        .getSingleResult();
    PayrollPeriod p = period(periodId);
    PayrollPayment existing = payments.findByRequestId(in.requestId()).orElse(null);
    if (existing != null) {
      if (same(existing, itemId, in)) return view(p);
      throw ApiException.conflict(
          "IDEMPOTENCY_CONFLICT",
          "This payment request ID was already used with different payment details.");
    }
    require(p, PayrollPeriod.Status.APPROVED);

    String payer = null;
    if (in.payerAccount() != null && !in.payerAccount().isBlank()) {
      payer = in.payerAccount().trim().toUpperCase(Locale.ROOT);
      if (!Set.of("AZ-2", "AK-2").contains(payer)) {
        throw ApiException.badRequest(
            "INVALID_PAYER_ACCOUNT", "Payer account must be AZ-2 or AK-2.");
      }
    }

    PayrollItem item =
        items
            .findOneById(itemId)
            .filter(x -> x.payrollPeriodId.equals(periodId))
            .orElseThrow(
                () ->
                    ApiException.notFound("PAYROLL_ITEM_NOT_FOUND", "Payroll item was not found."));
    long paid = payments.totalFor(item.id), remaining = Math.max(item.netSalaryMinor - paid, 0);
    if (in.amountMinor() > remaining)
      throw ApiException.conflict(
          "PAYROLL_OVERPAYMENT", "Payment exceeds the remaining payroll balance.");
    PayrollItem.PaymentStatus before = status(item.netSalaryMinor, paid);
    PayrollPayment payment = new PayrollPayment();
    payment.requestId = in.requestId();
    payment.payrollItemId = item.id;
    payment.amountMinor = in.amountMinor();
    payment.paidAt = in.paidAt();
    payment.paymentMethod = in.paymentMethod();
    payment.reference = clean(in.reference());
    payment.note = clean(in.note());
    payment.recordedBy = currentUser();
    payments.saveAndFlush(payment);
    long newPaid = Math.addExact(paid, payment.amountMinor);
    item.paymentStatus = status(item.netSalaryMinor, newPaid);
    item.paidAt = item.paymentStatus == PayrollItem.PaymentStatus.PAID ? payment.paidAt : null;
    items.save(item);

    if (financePosting != null && payer != null) {
      BigDecimal paymentAmount = BigDecimal.valueOf(payment.amountMinor).movePointLeft(2);
      LocalDate paymentDate = in.paidAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
      String paymentDesc =
          "Payroll disbursement for "
              + item.employeeNameSnapshot
              + " ("
              + p.month
              + "/"
              + p.year
              + ")";
      if (clean(in.reference()) != null) {
        paymentDesc += " Ref: " + clean(in.reference());
      }
      financePosting.employeePayment(
          new FinanceCommands.EmployeePayment(
              in.requestId(), item.employeeId, paymentAmount, paymentDate, paymentDesc, payer));
    }

    Map<String, Object> evidence = new LinkedHashMap<>();
    evidence.put("employeeId", item.employeeId);
    evidence.put("employee", item.employeeNameSnapshot);
    evidence.put("payrollPeriod", p.month + "/" + p.year);
    evidence.put("amountMinor", payment.amountMinor);
    evidence.put("method", payment.paymentMethod);
    evidence.put("payerAccount", payer != null ? payer : "UNSPECIFIED");
    evidence.put("reference", payment.reference);
    evidence.put("timestamp", payment.paidAt);
    audit.record("PAYROLL", "PAYROLL_PAYMENT_RECORDED", periodId.toString(), null, evidence);
    if (before != PayrollItem.PaymentStatus.PAID
        && item.paymentStatus == PayrollItem.PaymentStatus.PAID) emitSalary(p, item);
    var all = items.findAllByPayrollPeriodIdOrderByEmployeeNameSnapshot(periodId);
    if (!all.isEmpty() && all.stream().allMatch(x -> payments.totalFor(x.id) >= x.netSalaryMinor)) {
      p.status = PayrollPeriod.Status.PAID;
      p.paidAt = Instant.now();
      periods.save(p);
    }
    return view(p);
  }

  @Transactional
  public View lock(UUID id) {
    PayrollPeriod p = period(id);
    require(p, PayrollPeriod.Status.PAID);
    p.status = PayrollPeriod.Status.LOCKED;
    p.lockedAt = Instant.now();
    periods.save(p);
    var result = view(p);
    audit.record("PAYROLL", "PAYROLL_LOCKED", id.toString(), null, result);
    return result;
  }

  private void recompute(PayrollItem item) {
    long bonus = 0, overtime = 0, advance = 0, manual = 0;
    for (PayrollAdjustment a : adjustments.findAllByPayrollItemIdOrderByCreatedAt(item.id)) {
      switch (a.type) {
        case BONUS -> bonus = Math.addExact(bonus, a.amountMinor);
        case OVERTIME -> overtime = Math.addExact(overtime, a.amountMinor);
        case ADVANCE -> advance = Math.addExact(advance, a.amountMinor);
        case DEDUCTION -> manual = Math.subtractExact(manual, a.amountMinor);
        case CORRECTION, OTHER -> manual = Math.addExact(manual, a.amountMinor);
      }
    }
    item.bonusMinor = bonus;
    item.overtimeMinor = overtime;
    item.advanceDeductionMinor = advance;
    item.manualAdjustmentMinor = manual;
    long net =
        Math.addExact(
            Math.subtractExact(item.baseSalaryMinor, item.attendanceDeductionMinor),
            Math.addExact(Math.addExact(overtime, bonus), Math.subtractExact(manual, advance)));
    if (net < 0)
      throw ApiException.badRequest(
          "INVALID_PAYROLL_TOTAL", "Adjustments cannot make net payable negative.");
    item.netSalaryMinor = net;
    items.save(item);
  }

  private View view(PayrollPeriod p) {
    List<PayrollItem> rows = items.findAllByPayrollPeriodIdOrderByEmployeeNameSnapshot(p.id);
    Map<UUID, Long> totals = new HashMap<>();
    payments.totalsForPeriod(p.id).forEach(x -> totals.put(x.getItemId(), x.getTotalPaid()));
    Map<UUID, String> roles = new HashMap<>();
    employees
        .findAllById(rows.stream().map(x -> x.employeeId).toList())
        .forEach(e -> roles.put(e.id, e.roleTitle));
    var views =
        rows.stream()
            .map(
                i ->
                    itemView(
                        i,
                        totals.getOrDefault(i.id, 0L),
                        roles.getOrDefault(i.employeeId, "Employee"),
                        false))
            .toList();
    long total = views.stream().mapToLong(ItemView::netPayable).sum(),
        paid = views.stream().mapToLong(ItemView::totalPaid).sum(),
        remaining = views.stream().mapToLong(ItemView::remaining).sum();
    return new View(
        p.id,
        p.year,
        p.month,
        p.status,
        policy.name(),
        total,
        paid,
        remaining,
        remaining,
        views.stream()
            .filter(i -> i.netPayable() > 0 && i.paymentStatus() == PayrollItem.PaymentStatus.PAID)
            .count(),
        views.stream()
            .filter(i -> i.paymentStatus() == PayrollItem.PaymentStatus.PARTIALLY_PAID)
            .count(),
        views.stream()
            .filter(
                i -> i.netPayable() > 0 && i.paymentStatus() == PayrollItem.PaymentStatus.UNPAID)
            .count(),
        views,
        p.calculatedAt,
        p.approvedAt,
        p.paidAt,
        p.lockedAt);
  }

  private ItemView itemView(PayrollItem i, long paid, String role, boolean detailed) {
    List<PayrollAdjustment> adjustmentRows =
        detailed ? adjustments.findAllByPayrollItemIdOrderByCreatedAt(i.id) : List.of();
    List<PaymentView> paymentViews =
        detailed
            ? payments.findAllByPayrollItemIdOrderByPaidAtAscCreatedAtAsc(i.id).stream()
                .map(
                    p ->
                        new PaymentView(
                            p.id,
                            p.amountMinor,
                            p.paidAt,
                            p.paymentMethod,
                            p.reference,
                            p.note,
                            p.recordedBy,
                            p.createdAt))
                .toList()
            : List.of();
    long remaining = Math.max(i.netSalaryMinor - paid, 0),
        positive = Math.max(i.manualAdjustmentMinor, 0),
        negative = Math.max(-i.manualAdjustmentMinor, 0);
    long
        gross =
            Math.addExact(
                Math.addExact(i.baseSalaryMinor, i.bonusMinor),
                Math.addExact(i.overtimeMinor, positive)),
        deductions =
            Math.addExact(
                Math.addExact(i.attendanceDeductionMinor, i.advanceDeductionMinor), negative);
    PaymentView last = paymentViews.isEmpty() ? null : paymentViews.getLast();
    return new ItemView(
        i.id,
        i.employeeId,
        i.employeeNameSnapshot,
        role,
        i.salaryCurrency,
        i.baseSalaryMinor,
        i.attendanceDeductionMinor,
        i.overtimeMinor,
        i.bonusMinor,
        i.advanceDeductionMinor,
        i.manualAdjustmentMinor,
        gross,
        deductions,
        i.netSalaryMinor,
        i.netSalaryMinor,
        paid,
        remaining,
        status(i.netSalaryMinor, paid),
        last == null ? i.paidAt : last.paidAt(),
        last,
        paymentViews,
        adjustmentRows.stream()
            .map(
                a ->
                    new AdjustmentView(
                        a.id, a.type, a.amountMinor, a.reason, a.createdBy, a.createdAt))
            .toList());
  }

  private PayrollItem.PaymentStatus status(long net, long paid) {
    if (net == 0) return PayrollItem.PaymentStatus.PAID;
    if (paid <= 0) return PayrollItem.PaymentStatus.UNPAID;
    return paid >= net ? PayrollItem.PaymentStatus.PAID : PayrollItem.PaymentStatus.PARTIALLY_PAID;
  }

  private boolean same(PayrollPayment payment, UUID itemId, PaymentInput in) {
    return payment.payrollItemId.equals(itemId)
        && payment.amountMinor == in.amountMinor()
        && payment.paidAt.equals(in.paidAt())
        && payment.paymentMethod == in.paymentMethod()
        && Objects.equals(payment.reference, clean(in.reference()))
        && Objects.equals(payment.note, clean(in.note()));
  }

  private PayrollItem item(UUID periodId, UUID itemId) {
    return items
        .findById(itemId)
        .filter(x -> x.payrollPeriodId.equals(periodId))
        .orElseThrow(
            () -> ApiException.notFound("PAYROLL_ITEM_NOT_FOUND", "Payroll item was not found."));
  }

  private void require(PayrollPeriod p, PayrollPeriod.Status expected) {
    if (p.status != expected) throw state(p);
  }

  private ApiException state(PayrollPeriod p) {
    return ApiException.conflict(
        p.status == PayrollPeriod.Status.LOCKED ? "PAYROLL_LOCKED" : "INVALID_PAYROLL_STATE",
        "Payroll action is not allowed while the period is " + p.status + ".");
  }

  private PayrollPeriod period(UUID id) {
    return periods
        .findById(id)
        .orElseThrow(
            () -> ApiException.notFound("PAYROLL_NOT_FOUND", "Payroll period was not found."));
  }

  private UUID currentUser() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    return a == null ? null : users.findByEmailIgnoreCase(a.getName()).map(u -> u.id).orElse(null);
  }

  private String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private void emitSalary(PayrollPeriod p, PayrollItem item) {
    events.emit(
        "SALARY_PROCESSED",
        "PAYROLL",
        p.id,
        Map.of(
            "employeeId",
            item.employeeId,
            "category",
            "PAYROLL",
            "relatedType",
            "PAYROLL",
            "relatedId",
            p.id,
            "bodyPreview",
            "Salary processed · " + p.month + "/" + p.year,
            "variables",
            Map.of(
                "period",
                p.month + "/" + p.year,
                "amountMinor",
                item.netSalaryMinor,
                "currency",
                item.salaryCurrency)));
  }
}
