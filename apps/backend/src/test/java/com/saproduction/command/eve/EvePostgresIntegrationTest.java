package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;

import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class EvePostgresIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", postgres::getJdbcUrl);
    p.add("spring.datasource.username", postgres::getUsername);
    p.add("spring.datasource.password", postgres::getPassword);
    p.add("app.demo-seed", () -> false);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired EveService eveService;
  @Autowired EveMemoryService memoryService;
  @Autowired EmployeeRepository employeeRepo;

  @Test
  void verifiesEvePersistenceTablesAndZeroBusinessMutation() {
    // 1. Verify V028 migration tables exist in PostgreSQL
    Integer sessionTableCount = jdbc.queryForObject(
        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'eve_sessions'",
        Integer.class);
    Integer messageTableCount = jdbc.queryForObject(
        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'eve_messages'",
        Integer.class);
    Integer traceTableCount = jdbc.queryForObject(
        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'eve_trace_events'",
        Integer.class);
    Integer memoryTableCount = jdbc.queryForObject(
        "SELECT count(*) FROM information_schema.tables WHERE table_name = 'eve_memory'",
        Integer.class);

    assertThat(sessionTableCount).isEqualTo(1);
    assertThat(messageTableCount).isEqualTo(1);
    assertThat(traceTableCount).isEqualTo(1);
    assertThat(memoryTableCount).isEqualTo(1);

    // 2. Insert test employee into canonical table
    Employee emp = new Employee();
    emp.employeeCode = "SA-99";
    emp.firstName = "Raj";
    emp.lastName = "Sharma";
    emp.displayName = "Raj Sharma";
    emp.roleTitle = "Audio Lead";
    emp.department = "Sound";
    emp.employmentType = "FULL_TIME";
    emp.joiningDate = LocalDate.now();
    emp.baseSalaryMinor = 5000000L;
    emp.salaryCurrency = "INR";
    emp.status = Employee.Status.ACTIVE;
    emp.phone = "+919876543210";
    emp = employeeRepo.saveAndFlush(emp);

    // Snapshot counts of canonical business tables before Eve query
    int initialEmployeeCount = jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class);
    int initialProdCount = jdbc.queryForObject("SELECT count(*) FROM productions", Integer.class);
    int initialMemberCount = jdbc.queryForObject("SELECT count(*) FROM production_members", Integer.class);
    int initialTxCount = jdbc.queryForObject("SELECT count(*) FROM finance_transactions", Integer.class);
    int initialTaskCount = jdbc.queryForObject("SELECT count(*) FROM tasks", Integer.class);

    // 3. Test Eve explicit memory learning against real PostgreSQL
    EveDtos.MemoryRequest memReq = new EveDtos.MemoryRequest(
        "VOCABULARY", "Raju", "EMPLOYEE", emp.id, "Raj Sharma");
    EveDtos.MemoryView savedMem = memoryService.remember(memReq, "OPERATOR_EXPLICIT");
    assertThat(savedMem.term()).isEqualTo("Raju");

    var recalled = memoryService.recall("raju");
    assertThat(recalled).isPresent();
    assertThat(recalled.get().canonicalName()).isEqualTo("Raj Sharma");

    int memoryRowsAfterExplicit = jdbc.queryForObject("SELECT count(*) FROM eve_memory", Integer.class);

    // 4. Execute Eve natural language query
    EveDtos.QueryResponse response = eveService.query(
        new EveDtos.QueryRequest("How much does Sharma still need?", null));

    assertThat(response.status()).isEqualTo("COMPLETED");
    assertThat(response.message().content()).contains("Raj Sharma (SA-99)");
    assertThat(response.trace()).isNotEmpty();

    // Turn 2: Querying Raju using explicit memory hint
    EveDtos.QueryResponse turn2 = eveService.query(
        new EveDtos.QueryRequest("How much does Raju still need?", response.sessionId()));
    assertThat(turn2.status()).isEqualTo("COMPLETED");
    assertThat(turn2.message().content()).contains("Raj Sharma (SA-99)");

    // STRICT INVARIANT: Implicit query must NEVER create new rows in eve_memory
    int memoryRowsAfterQuery = jdbc.queryForObject("SELECT count(*) FROM eve_memory", Integer.class);
    assertThat(memoryRowsAfterQuery).isEqualTo(memoryRowsAfterExplicit);

    // 5. Verify Eve persistence records were written in EVE-owned tables
    Integer sessionRows = jdbc.queryForObject("SELECT count(*) FROM eve_sessions", Integer.class);
    Integer messageRows = jdbc.queryForObject("SELECT count(*) FROM eve_messages", Integer.class);
    Integer traceRows = jdbc.queryForObject("SELECT count(*) FROM eve_trace_events", Integer.class);

    assertThat(sessionRows).isGreaterThan(0);
    assertThat(messageRows).isGreaterThan(0);
    assertThat(traceRows).isGreaterThan(0);

    // Verify trace events contain NO private reasoning, chain-of-thought, or secrets
    List<String> traceDetails = jdbc.query(
        "SELECT detail FROM eve_trace_events WHERE session_id = ?",
        (rs, rowNum) -> rs.getString("detail"),
        response.sessionId());
    for (String detail : traceDetails) {
      if (detail != null) {
        assertThat(detail).doesNotContain("chain-of-thought");
        assertThat(detail).doesNotContain("prompt");
        assertThat(detail).doesNotContain("password");
        assertThat(detail).doesNotContain("secret");
      }
    }

    // 6. STRICT INVARIANT: Verify ZERO mutations to canonical business tables occurred
    assertThat(jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class)).isEqualTo(initialEmployeeCount);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM productions", Integer.class)).isEqualTo(initialProdCount);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM production_members", Integer.class)).isEqualTo(initialMemberCount);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM finance_transactions", Integer.class)).isEqualTo(initialTxCount);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM tasks", Integer.class)).isEqualTo(initialTaskCount);
  }
}
