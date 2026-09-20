package com.saproduction.command.config;

import com.saproduction.command.attendance.*;
import com.saproduction.command.auth.*;
import com.saproduction.command.employee.*;
import com.saproduction.command.leave.*;
import java.time.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DemoDataConfig {
  record Person(
      String code, String first, String last, String role, String department, long salary) {}

  @Bean
  @Order(10)
  CommandLineRunner demoData(
      @Value("${app.demo-seed}") boolean enabled,
      UserRepository users,
      EmployeeRepository employees,
      AttendanceRepository attendance,
      LeaveRepository leaves,
      PasswordEncoder encoder) {
    return args -> {
      if (!enabled) return;
      User owner =
          users
              .findByEmailIgnoreCase("owner@saproduction.local")
              .orElseGet(
                  () -> {
                    User u = new User();
                    u.email = "owner@saproduction.local";
                    u.displayName = "Owner";
                    u.role = "OWNER";
                    u.passwordHash = encoder.encode("SADemo!2026");
                    return users.save(u);
                  });
      if (employees.count() > 0) return;
      List<Person> people =
          List.of(
              new Person(
                  "SA-001", "Amaan", "Khan", "Senior Video Editor", "Post Production", 4200000),
              new Person("SA-002", "Rehan", "Ali", "Camera Operator", "Production", 3600000),
              new Person("SA-003", "Sarah", "Khan", "Photographer", "Production", 3900000),
              new Person("SA-004", "Farhan", "", "Production Assistant", "Operations", 2600000),
              new Person("SA-005", "Zoya", "Mirza", "Creative Producer", "Production", 4800000),
              new Person("SA-006", "Kabir", "Singh", "Drone Operator", "Production", 3500000),
              new Person("SA-007", "Meher", "Ansari", "Photo Editor", "Post Production", 3300000),
              new Person("SA-008", "Arjun", "Verma", "Sound Recordist", "Production", 3400000),
              new Person("SA-009", "Sana", "Rahman", "Client Coordinator", "Operations", 3100000),
              new Person("SA-010", "Vikram", "Rao", "Lighting Technician", "Production", 3000000),
              new Person("SA-011", "Nida", "Khan", "Motion Designer", "Post Production", 4100000),
              new Person("SA-012", "Imran", "Sheikh", "Equipment Manager", "Operations", 2950000));
      int i = 0;
      for (Person p : people) {
        Employee e = new Employee();
        e.employeeCode = p.code;
        e.firstName = p.first;
        e.lastName = p.last.isBlank() ? null : p.last;
        e.displayName = (p.first + " " + p.last).trim();
        e.phone = "+91 90000 00" + String.format("%03d", i + 1);
        e.whatsappPhone = e.phone;
        e.email =
            (p.first + "." + (p.last.isBlank() ? "sa" : p.last) + "@saproduction.local")
                .toLowerCase();
        e.roleTitle = p.role;
        e.department = p.department;
        e.employmentType = "FULL_TIME";
        e.joiningDate = LocalDate.of(2023 + (i % 3), (i % 12) + 1, Math.min(12, i + 1));
        e.baseSalaryMinor = p.salary;
        e.salaryCurrency = "INR";
        e.status = Employee.Status.ACTIVE;
        employees.save(e);
        AttendanceRecord r = new AttendanceRecord();
        r.employee = e;
        r.date = LocalDate.now();
        r.status =
            switch (i % 6) {
              case 1 -> AttendanceRecord.Status.LATE;
              case 2 -> AttendanceRecord.Status.ABSENT;
              case 3 -> AttendanceRecord.Status.LEAVE;
              default -> AttendanceRecord.Status.PRESENT;
            };
        r.checkInTime =
            r.status == AttendanceRecord.Status.PRESENT
                ? LocalTime.of(9, 28)
                : r.status == AttendanceRecord.Status.LATE ? LocalTime.of(9, 47) : null;
        r.minutesLate = r.status == AttendanceRecord.Status.LATE ? 17 : 0;
        r.recordedBy = owner.id;
        attendance.save(r);
        i++;
      }
      Employee farhan = employees.findByEmployeeCodeIgnoreCase("SA-004").orElseThrow();
      LeaveRequest leave = new LeaveRequest();
      leave.employee = farhan;
      leave.startDate = LocalDate.now().plusDays(4);
      leave.endDate = LocalDate.now().plusDays(5);
      leave.leaveType = "Personal";
      leave.reason = "Family commitment";
      leaves.save(leave);
    };
  }
}
