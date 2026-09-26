package com.saproduction.command.payroll;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.auth.UserRepository;
import com.saproduction.command.communication.DomainEventService;
import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.finance.FinanceCommands;
import com.saproduction.command.finance.FinancePostingService;
import com.saproduction.command.shared.ApiException;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PayrollBridgeServiceTest {
  private PayrollPeriodRepository periods;
  private PayrollItemRepository items;
  private PayrollAdjustmentRepository adjustments;
  private PayrollPaymentRepository payments;
  private EmployeeRepository employees;
  private PayrollCalculationPolicy policy;
  private UserRepository users;
  private AuditService audit;
  private DomainEventService events;
  private EntityManager entityManager;
  private FinancePostingService financePosting;

  private PayrollService payrollService;

  @BeforeEach
  void setUp() {
    periods = mock(PayrollPeriodRepository.class);
    items = mock(PayrollItemRepository.class);
    adjustments = mock(PayrollAdjustmentRepository.class);
    payments = mock(PayrollPaymentRepository.class);
    employees = mock(EmployeeRepository.class);
    policy = mock(PayrollCalculationPolicy.class);
    when(policy.name()).thenReturn("DEFAULT_POLICY");
    users = mock(UserRepository.class);
    audit = mock(AuditService.class);
    events = mock(DomainEventService.class);
    entityManager = mock(EntityManager.class);
    jakarta.persistence.Query queryMock = mock(jakarta.persistence.Query.class);
    when(entityManager.createNativeQuery(anyString())).thenReturn(queryMock);
    when(queryMock.setParameter(anyInt(), any())).thenReturn(queryMock);
    when(queryMock.getSingleResult()).thenReturn(1);

    financePosting = mock(FinancePostingService.class);

    payrollService =
        new PayrollService(
            periods,
            items,
            adjustments,
            payments,
            employees,
            policy,
            users,
            audit,
            events,
            entityManager,
            financePosting);
  }

  @Test
  void approveAccruesCanonicalSalaryWithDeterministicIdempotency() {
    UUID periodId = UUID.randomUUID();
    PayrollPeriod period = new PayrollPeriod();
    period.id = periodId;
    period.year = 2026;
    period.month = 10;
    period.status = PayrollPeriod.Status.CALCULATED;

    when(periods.findById(periodId)).thenReturn(Optional.of(period));

    UUID emp1Id = UUID.randomUUID();
    PayrollItem item1 = new PayrollItem();
    item1.id = UUID.randomUUID();
    item1.payrollPeriodId = periodId;
    item1.employeeId = emp1Id;
    item1.employeeNameSnapshot = "Amaan Khan";
    item1.baseSalaryMinor = 4_000_000L;
    item1.overtimeMinor = 200_000L;
    item1.bonusMinor = 300_000L;
    item1.attendanceDeductionMinor = 100_000L;
    item1.advanceDeductionMinor = 50_000L;
    item1.manualAdjustmentMinor = -10_000L;
    item1.netSalaryMinor = 4_340_000L; // 40000 + 2000 + 3000 - 1000 - 500 - 100 = 43400
    item1.paymentStatus = PayrollItem.PaymentStatus.UNPAID;

    UUID emp2Id = UUID.randomUUID();
    PayrollItem item2 = new PayrollItem();
    item2.id = UUID.randomUUID();
    item2.payrollPeriodId = periodId;
    item2.employeeId = emp2Id;
    item2.employeeNameSnapshot = "Zero Salary Emp";
    item2.netSalaryMinor = 0L;
    item2.paymentStatus = PayrollItem.PaymentStatus.PAID;

    when(items.findAllByPayrollPeriodIdOrderByEmployeeNameSnapshot(periodId))
        .thenReturn(List.of(item1, item2));

    Employee emp1 = new Employee();
    emp1.id = emp1Id;
    emp1.roleTitle = "Audio Lead";
    when(employees.findAllById(any())).thenReturn(List.of(emp1));

    payrollService.approve(periodId);

    assertThat(period.status).isEqualTo(PayrollPeriod.Status.APPROVED);
    assertThat(period.approvedAt).isNotNull();

    ArgumentCaptor<FinanceCommands.Salary> salaryCaptor =
        ArgumentCaptor.forClass(FinanceCommands.Salary.class);
    verify(financePosting, times(1)).salary(salaryCaptor.capture());

    FinanceCommands.Salary posted = salaryCaptor.getValue();
    UUID expectedKey =
        UUID.nameUUIDFromBytes(
            ("payroll-item-salary-" + item1.id).getBytes(StandardCharsets.UTF_8));
    assertThat(posted.idempotencyKey()).isEqualTo(expectedKey);
    assertThat(posted.employeeId()).isEqualTo(emp1Id);
    assertThat(posted.payrollItemId()).isEqualTo(item1.id);
    assertThat(posted.gross())
        .isEqualByComparingTo(new BigDecimal("45000.00")); // 40000 + 2000 + 3000
    assertThat(posted.approvedDeductions())
        .isEqualByComparingTo(new BigDecimal("1500.00")); // 1000 + 500
    assertThat(posted.adjustment()).isEqualByComparingTo(new BigDecimal("-100.00"));
    assertThat(posted.date()).isEqualTo(YearMonth.of(2026, 10).atEndOfMonth());
    assertThat(posted.description()).contains("Amaan Khan").contains("10/2026");
  }

  @Test
  void recordPaymentRejectsInvalidPayerAccount() {
    UUID periodId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    PayrollPeriod period = new PayrollPeriod();
    period.id = periodId;
    period.status = PayrollPeriod.Status.APPROVED;
    when(periods.findById(periodId)).thenReturn(Optional.of(period));

    PayrollService.PaymentInput invalidPayer =
        new PayrollService.PaymentInput(
            UUID.randomUUID(),
            100_000L,
            Instant.now(),
            PayrollPayment.Method.BANK_TRANSFER,
            "UNKNOWN-ACC",
            "REF-01",
            "Invalid payer");

    assertThatThrownBy(() -> payrollService.recordPayment(periodId, itemId, invalidPayer))
        .isInstanceOf(ApiException.class)
        .extracting("code")
        .isEqualTo("INVALID_PAYER_ACCOUNT");
  }

  @Test
  void recordPaymentWithoutPayerDoesNotDefaultPayerOrPostCanonicalPayment() {
    UUID periodId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();

    PayrollPeriod period = new PayrollPeriod();
    period.id = periodId;
    period.year = 2026;
    period.month = 10;
    period.status = PayrollPeriod.Status.APPROVED;
    when(periods.findById(periodId)).thenReturn(Optional.of(period));

    PayrollItem item = new PayrollItem();
    item.id = itemId;
    item.payrollPeriodId = periodId;
    item.employeeId = employeeId;
    item.employeeNameSnapshot = "Amaan Khan";
    item.netSalaryMinor = 4_000_000L;
    item.paymentStatus = PayrollItem.PaymentStatus.UNPAID;

    when(items.findOneById(itemId)).thenReturn(Optional.of(item));
    when(items.findById(itemId)).thenReturn(Optional.of(item));
    when(items.findAllByPayrollPeriodIdOrderByEmployeeNameSnapshot(periodId))
        .thenReturn(List.of(item));

    UUID requestId = UUID.randomUUID();
    Instant paidAt = Instant.now();

    PayrollService.PaymentInput operationalOnly =
        new PayrollService.PaymentInput(
            requestId,
            1_000_000L,
            paidAt,
            PayrollPayment.Method.CASH,
            null,
            "CASH-REF",
            "Operational without payer evidence");

    when(payments.totalFor(itemId)).thenReturn(0L);

    payrollService.recordPayment(periodId, itemId, operationalOnly);

    // Operational payment is recorded
    verify(payments).saveAndFlush(any(PayrollPayment.class));
    // Canonical finance posting must NOT be called without explicit owner account
    verify(financePosting, never()).employeePayment(any());
  }

  @Test
  void recordPaymentPostsCanonicalEmployeePaymentWithExplicitPayerAndSupportsPartialPayment() {
    UUID periodId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID employeeId = UUID.randomUUID();

    PayrollPeriod period = new PayrollPeriod();
    period.id = periodId;
    period.year = 2026;
    period.month = 10;
    period.status = PayrollPeriod.Status.APPROVED;
    when(periods.findById(periodId)).thenReturn(Optional.of(period));

    PayrollItem item = new PayrollItem();
    item.id = itemId;
    item.payrollPeriodId = periodId;
    item.employeeId = employeeId;
    item.employeeNameSnapshot = "Amaan Khan";
    item.netSalaryMinor = 4_000_000L; // 40,000 INR
    item.paymentStatus = PayrollItem.PaymentStatus.UNPAID;

    when(items.findOneById(itemId)).thenReturn(Optional.of(item));
    when(items.findById(itemId)).thenReturn(Optional.of(item));
    when(items.findAllByPayrollPeriodIdOrderByEmployeeNameSnapshot(periodId))
        .thenReturn(List.of(item));

    UUID requestId = UUID.randomUUID();
    Instant paidAt = Instant.now();

    // Partial payment: 15,000 of 40,000
    PayrollService.PaymentInput partialPayment =
        new PayrollService.PaymentInput(
            requestId,
            1_500_000L,
            paidAt,
            PayrollPayment.Method.BANK_TRANSFER,
            "AZ-2",
            "UTR-12345",
            "Partial first installment");

    when(payments.totalFor(itemId)).thenReturn(0L);

    payrollService.recordPayment(periodId, itemId, partialPayment);

    // Verify operational payment saved
    verify(payments).saveAndFlush(any(PayrollPayment.class));
    assertThat(item.paymentStatus).isEqualTo(PayrollItem.PaymentStatus.PARTIALLY_PAID);

    // Verify canonical finance payment called
    ArgumentCaptor<FinanceCommands.EmployeePayment> paymentCaptor =
        ArgumentCaptor.forClass(FinanceCommands.EmployeePayment.class);
    verify(financePosting, times(1)).employeePayment(paymentCaptor.capture());

    FinanceCommands.EmployeePayment posted = paymentCaptor.getValue();
    assertThat(posted.idempotencyKey()).isEqualTo(requestId);
    assertThat(posted.employeeId()).isEqualTo(employeeId);
    assertThat(posted.amount()).isEqualByComparingTo(new BigDecimal("15000.00"));
    assertThat(posted.payerAccount()).isEqualTo("AZ-2");
    assertThat(posted.description()).contains("Amaan Khan").contains("UTR-12345");
  }

  @Test
  void retryWithSameRequestIdReturnsExistingWithoutDuplicatePosting() {
    UUID periodId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    PayrollPeriod period = new PayrollPeriod();
    period.id = periodId;
    period.status = PayrollPeriod.Status.APPROVED;
    when(periods.findById(periodId)).thenReturn(Optional.of(period));

    UUID requestId = UUID.randomUUID();
    Instant paidAt = Instant.now();

    PayrollPayment existing = new PayrollPayment();
    existing.id = UUID.randomUUID();
    existing.requestId = requestId;
    existing.payrollItemId = itemId;
    existing.amountMinor = 1_500_000L;
    existing.paidAt = paidAt;
    existing.paymentMethod = PayrollPayment.Method.BANK_TRANSFER;
    existing.reference = "UTR-123";
    existing.note = "Installment";

    when(payments.findByRequestId(requestId)).thenReturn(Optional.of(existing));

    PayrollService.PaymentInput retryInput =
        new PayrollService.PaymentInput(
            requestId,
            1_500_000L,
            paidAt,
            PayrollPayment.Method.BANK_TRANSFER,
            "AZ-2",
            "UTR-123",
            "Installment");

    var result = payrollService.recordPayment(periodId, itemId, retryInput);

    assertThat(result).isNotNull();
    // Neither payments repository nor financePosting should be called on retry
    verify(payments, never()).saveAndFlush(any());
    verify(financePosting, never()).employeePayment(any());
  }
}
