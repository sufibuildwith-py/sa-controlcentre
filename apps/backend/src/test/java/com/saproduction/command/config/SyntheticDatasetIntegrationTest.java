package com.saproduction.command.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionMemberRepository;
import com.saproduction.command.production.ProductionRepository;
import com.saproduction.command.work.WorkTask;
import com.saproduction.command.work.WorkTaskRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SyntheticDatasetIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", postgres::getJdbcUrl);
    properties.add("spring.datasource.username", postgres::getUsername);
    properties.add("spring.datasource.password", postgres::getPassword);
    properties.add("app.demo-seed", () -> false);
  }

  @Autowired private SyntheticDatasetSeeder seeder;
  @Autowired private EmployeeRepository employeeRepository;
  @Autowired private ProductionRepository productionRepository;
  @Autowired private ProductionMemberRepository memberRepository;
  @Autowired private WorkTaskRepository taskRepository;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("Synthetic dataset seeding creates 50 employees and 50 productions with finance, crew, and tasks; second run is idempotent")
  void testSyntheticSeedingAndIdempotency() {
    // Run 1: First seed
    SyntheticDatasetSeeder.SeedResult run1 = seeder.seed();

    assertThat(run1.employeesCreated()).isEqualTo(50);
    assertThat(run1.employeesExisting()).isEqualTo(0);
    assertThat(run1.productionsCreated()).isEqualTo(50);
    assertThat(run1.productionsExisting()).isEqualTo(0);
    assertThat(run1.contractsCreated()).isEqualTo(50);
    assertThat(run1.advancesCreated()).isEqualTo(50);
    assertThat(run1.crewAssignmentsCreated()).isGreaterThanOrEqualTo(200);
    assertThat(run1.tasksCreated()).isGreaterThanOrEqualTo(140);

    // Verify employee Aarav Mehta (EMP-001)
    Employee aarav = employeeRepository.findByEmployeeCodeIgnoreCase("EMP-001").orElseThrow();
    assertThat(aarav.displayName).isEqualTo("Aarav Mehta");
    assertThat(aarav.roleTitle).isEqualTo("Production Manager");
    assertThat(aarav.department).isEqualTo("Production");
    assertThat(aarav.employmentType).isEqualTo("FULL_TIME");
    assertThat(aarav.baseSalaryMinor).isEqualTo(6500000L);
    assertThat(aarav.status).isEqualTo(Employee.Status.ACTIVE);
    assertThat(aarav.salaryCurrency).isEqualTo("INR");

    // Verify employee Nikhil Arora (EMP-027 on leave)
    Employee nikhil = employeeRepository.findByEmployeeCodeIgnoreCase("EMP-027").orElseThrow();
    assertThat(nikhil.displayName).isEqualTo("Nikhil Arora");
    assertThat(nikhil.status).isEqualTo(Employee.Status.ON_LEAVE);

    // Verify production: Sharma Wedding (2026-09-30)
    Production sharmaWedding =
        productionRepository.findAll().stream()
            .filter(p -> p.title.equals("Sharma Wedding") && p.eventDate.equals(LocalDate.of(2026, 9, 30)))
            .findFirst()
            .orElseThrow();
    assertThat(sharmaWedding.clientName).isEqualTo("Sharma Family");
    assertThat(sharmaWedding.venueName).isEqualTo("Royal Orchid");
    assertThat(sharmaWedding.venueAddress).isEqualTo("MG Road, Bengaluru");
    assertThat(sharmaWedding.priority).isEqualTo(Production.Priority.HIGH);
    assertThat(sharmaWedding.description).contains("Main wedding ceremony");
    assertThat(sharmaWedding.description).contains("Equipment needed: 2 Cameras, 4 Lights, Audio Kit");

    // Verify crew members assigned to Sharma Wedding (5 crew members)
    var members = memberRepository.findAllByProductionIdOrderByCreatedAt(sharmaWedding.id);
    assertThat(members).hasSize(5);

    // Verify tasks assigned to Sharma Wedding (3 tasks)
    List<WorkTask> tasks = taskRepository.findAllByProductionId(sharmaWedding.id);
    assertThat(tasks).hasSize(3);

    // Verify canonical finance profile and transactions for Sharma Wedding
    BigDecimal contractedAmount =
        jdbc.queryForObject(
            "SELECT contracted_amount FROM finance_production_profiles WHERE production_id = ?",
            BigDecimal.class,
            sharmaWedding.id);
    assertThat(contractedAmount).isEqualByComparingTo("280000.00");

    BigDecimal receivedAmount =
        jdbc.queryForObject(
            """
            SELECT coalesce(sum(a.amount), 0)
            FROM finance_production_receipt_allocations a
            JOIN finance_transactions t ON t.id = a.transaction_id
            WHERE a.production_id = ? AND t.status = 'POSTED'
            """,
            BigDecimal.class,
            sharmaWedding.id);
    assertThat(receivedAmount).isEqualByComparingTo("100000.00");

    // Total database counts after Run 1
    assertThat(employeeRepository.count()).isEqualTo(50);
    assertThat(productionRepository.count()).isEqualTo(50);

    // Run 2: Second seed to verify idempotency
    SyntheticDatasetSeeder.SeedResult run2 = seeder.seed();

    assertThat(run2.employeesCreated()).isEqualTo(0);
    assertThat(run2.employeesExisting()).isEqualTo(50);
    assertThat(run2.productionsCreated()).isEqualTo(0);
    assertThat(run2.productionsExisting()).isEqualTo(50);

    // Assert counts are unchanged
    assertThat(employeeRepository.count()).isEqualTo(50);
    assertThat(productionRepository.count()).isEqualTo(50);
    assertThat(memberRepository.findAllByProductionIdOrderByCreatedAt(sharmaWedding.id)).hasSize(5);
    assertThat(taskRepository.findAllByProductionId(sharmaWedding.id)).hasSize(3);
  }
}
