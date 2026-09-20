package com.saproduction.command;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @Test
  void attendanceIsUniquePerEmployeeAndDate() throws Exception {
    try (Connection c =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      UUIDs ids = insertOwnerAndEmployee(c);
      insertAttendance(c, ids.employee());
      assertThatThrownBy(() -> insertAttendance(c, ids.employee()))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void payrollPaymentRequiresPositiveAmountAndKnownMethod() throws Exception {
    String item = java.util.UUID.randomUUID().toString();
    try (Connection c =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      UUIDs ids = insertOwnerAndEmployee(c);
      try (PreparedStatement p =
          c.prepareStatement(
              "insert into payroll_periods(year,month,status) values (2028,1,'APPROVED') returning id")) {
        ResultSet r = p.executeQuery();
        r.next();
        String period = r.getString(1);
        try (PreparedStatement i =
            c.prepareStatement(
                "insert into payroll_items(id,payroll_period_id,employee_id,employee_name_snapshot,salary_currency,base_salary_minor,net_salary_minor,payment_status) values (?::uuid,?::uuid,?::uuid,'Test','INR',4200000,4200000,'UNPAID')")) {
          i.setString(1, item);
          i.setString(2, period);
          i.setString(3, ids.employee());
          i.executeUpdate();
        }
      }
    }
    assertThatThrownBy(() -> insertPayment(item, 0, "CASH")).isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertPayment(item, 1, "CRYPTO")).isInstanceOf(SQLException.class);
  }

  private UUIDs insertOwnerAndEmployee(Connection c) throws Exception {
    String user = java.util.UUID.randomUUID().toString(),
        employee = java.util.UUID.randomUUID().toString(),
        suffix = user.substring(0, 8);
    try (PreparedStatement p =
        c.prepareStatement(
            "insert into users(id,email,password_hash,display_name,role) values (?::uuid,?,'hash','Owner','OWNER')")) {
      p.setString(1, user);
      p.setString(2, "test-" + suffix + "@sa.local");
      p.executeUpdate();
    }
    try (PreparedStatement p =
        c.prepareStatement(
            "insert into employees(id,employee_code,first_name,display_name,phone,role_title,department,employment_type,joining_date,base_salary_minor,salary_currency,status) values (?::uuid,?,'Test','Test',?,'Editor','Post','FULL_TIME',current_date,4200000,'INR','ACTIVE')")) {
      p.setString(1, employee);
      p.setString(2, "SA-" + suffix);
      p.setString(3, "+91" + Math.abs(user.hashCode()));
      p.executeUpdate();
    }
    return new UUIDs(user, employee);
  }

  private void insertAttendance(Connection c, String employee) throws Exception {
    try (PreparedStatement p =
        c.prepareStatement(
            "insert into attendance_records(employee_id,attendance_date,status) values (?::uuid,current_date,'PRESENT')")) {
      p.setString(1, employee);
      p.executeUpdate();
    }
  }

  private void insertPayment(String item, long amount, String method) throws Exception {
    try (Connection c =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        PreparedStatement p =
            c.prepareStatement(
                "insert into payroll_payments(payroll_item_id,amount_minor,paid_at,payment_method,request_id) values (?::uuid,?,now(),?,gen_random_uuid())")) {
      p.setString(1, item);
      p.setLong(2, amount);
      p.setString(3, method);
      p.executeUpdate();
    }
  }

  record UUIDs(String owner, String employee) {}
}
