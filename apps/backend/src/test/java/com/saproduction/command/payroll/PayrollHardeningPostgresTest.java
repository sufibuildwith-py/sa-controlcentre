package com.saproduction.command.payroll;

import static org.assertj.core.api.Assertions.*;

import com.saproduction.command.employee.*;
import com.saproduction.command.shared.ApiException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PayrollHardeningPostgresTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", postgres::getJdbcUrl);
    p.add("spring.datasource.username", postgres::getUsername);
    p.add("spring.datasource.password", postgres::getPassword);
    p.add("app.demo-seed", () -> false);
    p.add("app.messaging.worker-enabled", () -> false);
  }

  @Autowired PayrollService payroll;
  @Autowired EmployeeRepository employees;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clean() {
    jdbc.update("delete from payroll_payments");
    jdbc.update("delete from payroll_adjustments");
    jdbc.update("delete from payroll_items");
    jdbc.update("delete from payroll_periods");
    jdbc.update("delete from employees");
  }

  @Test
  void duplicatePaymentRequestCreatesOneLedgerRow() {
    var setup = approved(2031, 1, 4_000_000);
    UUID requestId = UUID.randomUUID();
    Instant paidAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    var input =
        new PayrollService.PaymentInput(
            requestId, 500_000, paidAt, PayrollPayment.Method.UPI, "REF", "Part payment");
    payroll.recordPayment(setup.periodId(), setup.itemId(), input);
    payroll.recordPayment(setup.periodId(), setup.itemId(), input);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from payroll_payments where request_id=?", Long.class, requestId))
        .isEqualTo(1);
  }

  @Test
  void concurrentDuplicatePaymentCreatesOneLedgerRow() throws Exception {
    var setup = approved(2031, 2, 4_000_000);
    UUID requestId = UUID.randomUUID();
    Instant paidAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    var input =
        new PayrollService.PaymentInput(
            requestId, 500_000, paidAt, PayrollPayment.Method.BANK_TRANSFER, "CONCURRENT", null);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Callable<Void> first =
          () -> {
            payroll.recordPayment(setup.periodId(), setup.itemId(), input);
            return null;
          };
      Callable<Void> second =
          () -> {
            payroll.recordPayment(setup.periodId(), setup.itemId(), input);
            return null;
          };
      for (Future<Void> result : pool.invokeAll(List.of(first, second))) result.get();
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from payroll_payments where request_id=?", Long.class, requestId))
        .isEqualTo(1);
  }

  @Test
  void finalPaymentRetryStillSucceedsAfterPeriodIsLocked() {
    var setup = approved(2031, 5, 4_000_000);
    long balance = payroll.get(setup.periodId()).items().getFirst().remaining();
    var input =
        new PayrollService.PaymentInput(
            UUID.randomUUID(),
            balance,
            Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
            PayrollPayment.Method.UPI,
            "FINAL",
            null);
    payroll.recordPayment(setup.periodId(), setup.itemId(), input);
    payroll.lock(setup.periodId());
    assertThat(payroll.recordPayment(setup.periodId(), setup.itemId(), input).status())
        .isEqualTo(PayrollPeriod.Status.LOCKED);
    assertThat(payroll.paymentHistory(setup.periodId(), setup.itemId()).payments()).hasSize(1);
  }

  @Test
  void reusedRequestIdWithDifferentPayloadConflicts() {
    var setup = approved(2031, 3, 4_000_000);
    UUID requestId = UUID.randomUUID();
    Instant paidAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    payroll.recordPayment(
        setup.periodId(),
        setup.itemId(),
        new PayrollService.PaymentInput(
            requestId, 500_000, paidAt, PayrollPayment.Method.CASH, null, null));
    assertThatThrownBy(
            () ->
                payroll.recordPayment(
                    setup.periodId(),
                    setup.itemId(),
                    new PayrollService.PaymentInput(
                        requestId, 600_000, paidAt, PayrollPayment.Method.CASH, null, null)))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code).isEqualTo("IDEMPOTENCY_CONFLICT"));
  }

  @Test
  void databaseRejectsNonInrSalary() {
    Employee employee = employee(1_000_000);
    employee.salaryCurrency = "USD";
    assertThatThrownBy(() -> employees.saveAndFlush(employee))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void zeroNetEmployeeRequiresNoPaymentAndPeriodSettles() {
    employees.saveAndFlush(employee(0));
    var period = payroll.calculate(2031, 4);
    var item = period.items().getFirst();
    assertThat(item.paymentStatus()).isEqualTo(PayrollItem.PaymentStatus.PAID);
    assertThat(item.remaining()).isZero();
    assertThat(payroll.approve(period.id()).status()).isEqualTo(PayrollPeriod.Status.PAID);
  }

  private Setup approved(int year, int month, long salary) {
    employees.saveAndFlush(employee(salary));
    var period = payroll.calculate(year, month);
    UUID itemId = period.items().getFirst().id();
    period = payroll.approve(period.id());
    return new Setup(period.id(), itemId);
  }

  private Employee employee(long salary) {
    Employee e = new Employee();
    e.employeeCode = "H-" + UUID.randomUUID().toString().substring(0, 8);
    e.firstName = "Amaan";
    e.displayName = "Amaan Test";
    e.phone = "+919000000000";
    e.whatsappPhone = e.phone;
    e.roleTitle = "Editor";
    e.department = "Post";
    e.employmentType = "FULL_TIME";
    e.joiningDate = LocalDate.of(2024, 1, 1);
    e.baseSalaryMinor = salary;
    e.salaryCurrency = "INR";
    e.status = Employee.Status.ACTIVE;
    return e;
  }

  record Setup(UUID periodId, UUID itemId) {}
}
