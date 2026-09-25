package com.saproduction.command.config;

import com.saproduction.command.calendar.*;
import com.saproduction.command.employee.*;
import com.saproduction.command.meeting.*;
import com.saproduction.command.payroll.*;
import com.saproduction.command.production.*;
import com.saproduction.command.work.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class Phase2DemoDataConfig {
  @Bean
  @Order(20)
  CommandLineRunner phase2DemoData(
      @Value("${app.demo-seed}") boolean enabled,
      @Value("${app.time-zone:Asia/Kolkata}") String zoneName,
      ProductionRepository productionRepository,
      EmployeeRepository employeeRepository,
      ProductionService productions,
      CalendarService calendar,
      WorkTaskService tasks,
      MeetingService meetings,
      PayrollService payroll,
      JdbcTemplate jdbc) {
    return args -> {
      if (!enabled) return;
      seedCounterparties(jdbc);
      if (productionRepository.count() > 0) return;
      ZoneId zone = ZoneId.of(zoneName);
      LocalDate today = LocalDate.now(zone);
      List<Employee> people =
          employeeRepository.findAllByStatusNotOrderByDisplayName(Employee.Status.INACTIVE);
      Map<String, Employee> byName = new HashMap<>();
      for (Employee e : people) byName.put(e.firstName, e);
      ProductionService.View sharma =
          create(
              productions,
              "Sharma Wedding",
              "Sharma Family",
              today.plusDays(7),
              LocalTime.of(16, 30),
              LocalTime.of(21, 30),
              "Royal Orchid, Lucknow",
              Production.Priority.HIGH,
              78,
              Production.Status.PRE_PRODUCTION);
      ProductionService.View brand =
          create(
              productions,
              "Corporate Brand Film",
              "Northstar Foods",
              today.plusDays(10),
              LocalTime.of(10, 0),
              LocalTime.of(17, 0),
              "SA Studio",
              Production.Priority.NORMAL,
              42,
              Production.Status.POST_PRODUCTION);
      ProductionService.View product =
          create(
              productions,
              "Product Shoot",
              "Nivara Living",
              today.plusDays(2),
              LocalTime.of(11, 0),
              LocalTime.of(15, 0),
              "Nivara Showroom",
              Production.Priority.URGENT,
              88,
              Production.Status.REVIEW);
      ProductionService.View delivered =
          create(
              productions,
              "Heritage Campaign",
              "Lucknow Heritage",
              today.minusDays(18),
              LocalTime.of(8, 0),
              LocalTime.of(14, 0),
              "Residency Grounds",
              Production.Priority.NORMAL,
              100,
              Production.Status.DELIVERED);
      Instant overlapStart = sharma.eventDate().atTime(14, 0).atZone(zone).toInstant(),
          overlapEnd = sharma.eventDate().atTime(18, 0).atZone(zone).toInstant();
      calendar.create(
          new CalendarService.Input(
              CalendarEvent.Type.INTERNAL,
              "Camera systems check",
              "Deterministic overlap fixture",
              overlapStart,
              overlapEnd,
              "SA Studio",
              null,
              List.of(byName.get("Amaan").id),
              false,
              null));
      productions.addMember(
          sharma.id(),
          new ProductionService.MemberInput(
              byName.get("Amaan").id,
              "Lead editor",
              true,
              ProductionMember.Status.CONFIRMED,
              true,
              "Owner-approved demo overlap"));
      productions.addMember(
          sharma.id(),
          new ProductionService.MemberInput(
              byName.get("Rehan").id,
              "Camera operator",
              true,
              ProductionMember.Status.CONFIRMED,
              false,
              null));
      productions.addMember(
          sharma.id(),
          new ProductionService.MemberInput(
              byName.get("Farhan").id,
              "Production assistant",
              true,
              ProductionMember.Status.PENDING,
              false,
              null));
      productions.addMember(
          brand.id(),
          new ProductionService.MemberInput(
              byName.get("Zoya").id,
              "Producer",
              true,
              ProductionMember.Status.CONFIRMED,
              false,
              null));
      productions.addMember(
          product.id(),
          new ProductionService.MemberInput(
              byName.get("Sarah").id,
              "Photographer",
              true,
              ProductionMember.Status.CONFIRMED,
              false,
              null));
      String[] titles = {
        "Wedding teaser edit",
        "Prepare camera bodies",
        "Photography shortlist",
        "Client review export",
        "Deliver master files",
        "Check lighting kit",
        "Confirm batteries",
        "Back up raw footage",
        "Prepare call sheet",
        "Color grade highlights",
        "Mix ceremony audio",
        "Create social cut",
        "Check drone permits",
        "Pack lenses",
        "Share review link",
        "Apply client notes",
        "Archive project files",
        "Prepare thumbnail selects",
        "Confirm venue access",
        "Export final gallery",
        "Label memory cards",
        "Review equipment checklist",
        "Schedule client handoff",
        "Finalize credits"
      };
      for (int i = 0; i < titles.length; i++) {
        WorkTask.Status status =
            i % 6 == 0
                ? WorkTask.Status.DONE
                : i % 7 == 0
                    ? WorkTask.Status.BLOCKED
                    : i % 3 == 0 ? WorkTask.Status.IN_PROGRESS : WorkTask.Status.TODO;
        int progress =
            status == WorkTask.Status.DONE
                ? 100
                : status == WorkTask.Status.BLOCKED
                    ? 65
                    : status == WorkTask.Status.IN_PROGRESS ? 45 + (i % 4) * 10 : 0;
        Instant due = today.plusDays((i % 9) - 2).atTime(18, 0).atZone(zone).toInstant();
        tasks.create(
            new WorkTaskService.Input(
                i < 9 ? sharma.id() : i < 16 ? brand.id() : product.id(),
                null,
                titles[i],
                i % 7 == 0 ? "Waiting for client input" : null,
                people.get(i % people.size()).id,
                status,
                i % 5 == 0 ? WorkTask.Priority.HIGH : WorkTask.Priority.NORMAL,
                today.minusDays(i % 3),
                due,
                progress));
      }
      MeetingService.View upcoming =
          meetings.create(
              new MeetingService.Input(
                  "Weekly Production Review",
                  "Delivery and staffing review",
                  "Last week's deliveries\nSharma wedding preparation\nEquipment preparation",
                  today.plusDays(1).atTime(11, 30).atZone(zone).toInstant(),
                  today.plusDays(1).atTime(12, 15).atZone(zone).toInstant(),
                  "SA Studio",
                  List.of(byName.get("Amaan").id, byName.get("Rehan").id, byName.get("Sarah").id),
                  false,
                  null));
      meetings.addNote(
          upcoming.id(),
          new MeetingService.NoteInput("Confirm final crew availability before the client call."));
      meetings.action(
          upcoming.id(),
          new MeetingService.ActionInput(
              "Finish teaser export",
              "Meeting action item",
              byName.get("Amaan").id,
              today.plusDays(2).atTime(17, 0).atZone(zone).toInstant()));
      meetings.create(
          new MeetingService.Input(
              "Client Review Prep",
              "Prepare review package",
              null,
              today.plusDays(3).atTime(15, 0).atZone(zone).toInstant(),
              today.plusDays(3).atTime(16, 0).atZone(zone).toInstant(),
              "Edit Suite",
              List.of(byName.get("Zoya").id),
              false,
              null));
      meetings.create(
          new MeetingService.Input(
              "September Operations Review",
              "Historical review",
              null,
              today.minusDays(7).atTime(10, 0).atZone(zone).toInstant(),
              today.minusDays(7).atTime(11, 0).atZone(zone).toInstant(),
              "SA Studio",
              List.of(),
              false,
              null));
      meetings.create(
          new MeetingService.Input(
              "Equipment Retrospective",
              "Historical notes",
              null,
              today.minusDays(14).atTime(16, 0).atZone(zone).toInstant(),
              today.minusDays(14).atTime(17, 0).atZone(zone).toInstant(),
              "Equipment Room",
              List.of(),
              false,
              null));
      YearMonth previous = YearMonth.from(today).minusMonths(1), current = YearMonth.from(today);
      PayrollService.View old = payroll.calculate(previous.getYear(), previous.getMonthValue());
      PayrollService.ItemView amaan =
          old.items().stream()
              .filter(x -> x.employeeId().equals(byName.get("Amaan").id))
              .findFirst()
              .orElseThrow();
      old =
          payroll.adjust(
              old.id(),
              new PayrollService.AdjustmentInput(
                  amaan.id(), PayrollAdjustment.Type.BONUS, 300000, "Performance bonus"));
      old = payroll.approve(old.id());
      for (PayrollService.ItemView item : old.items())
        old =
            payroll.recordPayment(
                old.id(),
                item.id(),
                new PayrollService.PaymentInput(
                    UUID.randomUUID(),
                    item.remaining(),
                    Instant.now(),
                    PayrollPayment.Method.BANK_TRANSFER,
                    "DEMO-" + item.id().toString().substring(0, 8),
                    "Demo salary settlement"));
      payroll.lock(old.id());
      payroll.calculate(current.getYear(), current.getMonthValue());
    };
  }

  private ProductionService.View create(
      ProductionService service,
      String title,
      String client,
      LocalDate date,
      LocalTime start,
      LocalTime end,
      String venue,
      Production.Priority priority,
      int progress,
      Production.Status target) {
    ProductionService.View value =
        service.create(
            new ProductionService.Input(
                title,
                client,
                "Operational production fixture",
                date,
                start,
                end,
                venue,
                "Lucknow",
                priority,
                progress));
    List<Production.Status> path =
        List.of(
            Production.Status.PLANNING,
            Production.Status.PRE_PRODUCTION,
            Production.Status.PRODUCTION,
            Production.Status.POST_PRODUCTION,
            Production.Status.REVIEW,
            Production.Status.DELIVERED);
    for (Production.Status next : path) {
      if (next.ordinal() > target.ordinal()) break;
      value = service.transition(value.id(), next);
    }
    return value;
  }

  private static void seedCounterparties(JdbcTemplate jdbc) {
    List<String> clients =
        List.of("Northstar Foods", "Sharma Family", "Nivara Living", "Lucknow Heritage");
    for (String client : clients) {
      Integer existing =
          jdbc.queryForObject(
              "SELECT count(*) FROM finance_counterparties WHERE lower(display_name) = lower(?)",
              Integer.class,
              client);
      if (existing == null || existing == 0) {
        jdbc.update(
            "INSERT INTO finance_counterparties(id, display_name, role, active) VALUES(?, ?, 'CUSTOMER', true)",
            UUID.randomUUID(),
            client);
      }
    }
  }
}
