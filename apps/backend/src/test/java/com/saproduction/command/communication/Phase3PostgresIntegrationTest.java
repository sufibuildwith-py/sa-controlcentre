package com.saproduction.command.communication;

import static org.assertj.core.api.Assertions.*;

import com.saproduction.command.employee.*;
import com.saproduction.command.meeting.*;
import com.saproduction.command.payroll.*;
import com.saproduction.command.production.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class Phase3PostgresIntegrationTest {
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

  @Autowired EmployeeRepository employees;
  @Autowired ProductionService productions;
  @Autowired MeetingService meetings;
  @Autowired PayrollService payroll;
  @Autowired OutboxEventRepository outbox;
  @Autowired OutboundMessageRepository messages;
  @Autowired OutboundDeliveryAttemptRepository attempts;
  @Autowired NotificationPolicyService policy;
  @Autowired InboundMessagingService inbound;
  Employee employee;
  LocalDate day = LocalDate.now().plusDays(7);

  @BeforeEach
  void person() {
    employee = new Employee();
    employee.employeeCode = "P3-" + UUID.randomUUID().toString().substring(0, 7);
    employee.firstName = "Amaan";
    employee.displayName = "Amaan Phase3";
    employee.phone = "+919000000000";
    employee.whatsappPhone = employee.phone;
    employee.roleTitle = "Editor";
    employee.department = "Post";
    employee.employmentType = "FULL_TIME";
    employee.joiningDate = LocalDate.of(2024, 1, 1);
    employee.baseSalaryMinor = 4_200_000;
    employee.salaryCurrency = "INR";
    employee.status = Employee.Status.ACTIVE;
    employees.saveAndFlush(employee);
  }

  @Test
  void assignmentOutboxCreatesOneIdempotentMessageAndReplyClosesLoop() {
    var production =
        productions.create(
            new ProductionService.Input(
                "Wedding",
                "Client",
                null,
                day,
                LocalTime.of(10, 0),
                LocalTime.of(14, 0),
                "Studio",
                null,
                Production.Priority.HIGH,
                0));
    productions.addMember(
        production.id(),
        new ProductionService.MemberInput(
            employee.id, "Editor", true, ProductionMember.Status.PENDING, false, null));
    OutboxEvent event =
        outbox.findAll().stream()
            .filter(e -> e.eventType.equals("PRODUCTION_ASSIGNED"))
            .findFirst()
            .orElseThrow();
    assertThat(policy.evaluate(event)).isEqualTo(1);
    assertThat(policy.evaluate(event)).isZero();
    OutboundMessage message = messages.findAll().getFirst();
    assertThat(message.status).isEqualTo(OutboundMessage.Status.QUEUED);
    inbound.simulateResponse(message.id, "CONFIRM");
    assertThat(productions.get(production.id()).members())
        .singleElement()
        .extracting(ProductionService.MemberView::assignmentStatus)
        .isEqualTo(ProductionMember.Status.CONFIRMED);
    assertThat(messages.findById(message.id).orElseThrow().response).isEqualTo("CONFIRMED");
  }

  @Test
  void meetingInviteReplyUpdatesAttendee() {
    Instant start = day.atTime(15, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
    var meeting =
        meetings.create(
            new MeetingService.Input(
                "Review",
                null,
                null,
                start,
                start.plusSeconds(3600),
                "Studio",
                List.of(employee.id),
                false,
                null));
    OutboxEvent event =
        outbox.findAll().stream()
            .filter(e -> e.eventType.equals("MEETING_CREATED"))
            .findFirst()
            .orElseThrow();
    policy.evaluate(event);
    OutboundMessage message = messages.findAll().getFirst();
    inbound.simulateResponse(message.id, "ACCEPT");
    assertThat(meetings.get(meeting.id()).attendees())
        .singleElement()
        .extracting(MeetingService.Attendee::response)
        .isEqualTo("ACCEPTED");
  }

  @Test
  void fullSettlementEmitsOneMinimalSalaryEvent() {
    var period = payroll.calculate(day.getYear(), day.getMonthValue());
    period = payroll.approve(period.id());
    var item = period.items().getFirst();
    payroll.recordPayment(
        period.id(),
        item.id(),
        new PayrollService.PaymentInput(
            UUID.randomUUID(),
            1_000_000,
            Instant.now(),
            PayrollPayment.Method.UPI,
            "PARTIAL",
            null));
    payroll.recordPayment(
        period.id(),
        item.id(),
        new PayrollService.PaymentInput(
            UUID.randomUUID(),
            item.netPayable() - 1_000_000,
            Instant.now(),
            PayrollPayment.Method.BANK_TRANSFER,
            "FINAL",
            null));
    assertThat(outbox.findAll().stream().filter(e -> e.eventType.equals("SALARY_PROCESSED")))
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.payloadJson).contains("amountMinor");
              assertThat(e.payloadJson).doesNotContain("notes", "reason");
            });
  }

  @Test
  void duplicateProviderReceiptIsIgnored() {
    OutboundMessage m = message("wamid.test", OutboundMessage.Status.SENT);
    OutboundDeliveryAttempt a = attempt(m, "wamid.test", 1, OutboundDeliveryAttempt.Status.SENT);
    assertThat(inbound.providerStatus("receipt-1", "wamid.test", "DELIVERED", null)).isTrue();
    assertThat(inbound.providerStatus("receipt-1", "wamid.test", "DELIVERED", null)).isFalse();
    assertThat(messages.findById(m.id).orElseThrow().status)
        .isEqualTo(OutboundMessage.Status.DELIVERED);
    assertThat(attempts.findById(a.id).orElseThrow().status)
        .isEqualTo(OutboundDeliveryAttempt.Status.DELIVERED);
  }

  @Test
  void lateOldAttemptFailureCannotCorruptSuccessfulRetry() {
    OutboundMessage m = message("wamid.new", OutboundMessage.Status.DELIVERED);
    attempt(m, "wamid.old", 1, OutboundDeliveryAttempt.Status.SENT);
    attempt(m, "wamid.new", 2, OutboundDeliveryAttempt.Status.DELIVERED);
    inbound.providerStatus("late-failure", "wamid.old", "FAILED", "late failure");
    assertThat(messages.findById(m.id).orElseThrow().status)
        .isEqualTo(OutboundMessage.Status.DELIVERED);
    assertThat(attempts.findByProviderMessageId("wamid.old").orElseThrow().status)
        .isEqualTo(OutboundDeliveryAttempt.Status.FAILED);
    assertThat(attempts.findAllByOutboundMessageIdOrderByAttemptNumber(m.id)).hasSize(2);
  }

  @Test
  void oldFailureCannotCancelPendingRetryAndStaleCallbacksCannotRegressRead() {
    OutboundMessage m = message("wamid.current", OutboundMessage.Status.SENT);
    attempt(m, "wamid.previous", 1, OutboundDeliveryAttempt.Status.SENT);
    attempt(m, "wamid.current", 2, OutboundDeliveryAttempt.Status.SENT);
    inbound.providerStatus("old-failure", "wamid.previous", "FAILED", "late");
    assertThat(messages.findById(m.id).orElseThrow().status).isEqualTo(OutboundMessage.Status.SENT);
    inbound.providerStatus("read", "wamid.current", "READ", null);
    inbound.providerStatus("stale-delivered", "wamid.current", "DELIVERED", null);
    inbound.providerStatus("stale-sent", "wamid.current", "SENT", null);
    inbound.providerStatus("stale-failed", "wamid.current", "FAILED", "late");
    assertThat(messages.findById(m.id).orElseThrow().status).isEqualTo(OutboundMessage.Status.READ);
    assertThat(attempts.findByProviderMessageId("wamid.current").orElseThrow().status)
        .isEqualTo(OutboundDeliveryAttempt.Status.READ);
  }

  private OutboundMessage message(String providerId, OutboundMessage.Status status) {
    OutboundMessage m = new OutboundMessage();
    m.employeeId = employee.id;
    m.category = "MANUAL";
    m.templateKey = "notice";
    m.templateVariablesJson = "{}";
    m.bodyPreview = "Test";
    m.idempotencyKey = UUID.randomUUID().toString();
    m.providerMessageId = providerId;
    m.status = status;
    return messages.saveAndFlush(m);
  }

  private OutboundDeliveryAttempt attempt(
      OutboundMessage m, String providerId, int number, OutboundDeliveryAttempt.Status status) {
    m.attemptCount = Math.max(m.attemptCount, number);
    messages.saveAndFlush(m);
    OutboundDeliveryAttempt a = new OutboundDeliveryAttempt();
    a.outboundMessageId = m.id;
    a.attemptNumber = number;
    a.providerMessageId = providerId;
    a.status = status;
    a.startedAt = Instant.now();
    return attempts.saveAndFlush(a);
  }
}
