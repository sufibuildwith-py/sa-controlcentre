package com.saproduction.command.config;

import com.saproduction.command.employee.*;
import com.saproduction.command.production.*;
import com.saproduction.command.work.WorkTask;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Seeds synthetic datasets (people_data.txt: 50 employees, production_data.txt: 50 productions)
 * using canonical domain services and enforcing idempotent re-runs.
 */
@Service
public class SyntheticDatasetSeeder {
  private static final Logger log = LoggerFactory.getLogger(SyntheticDatasetSeeder.class);
  private static final DateTimeFormatter PROD_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

  public record SeedResult(
      int employeesCreated,
      int employeesExisting,
      int productionsCreated,
      int productionsExisting,
      int crewAssignmentsCreated,
      int tasksCreated,
      int contractsCreated,
      int advancesCreated) {}

  private final EmployeeRepository employeeRepository;
  private final EmployeeService employeeService;
  private final ProductionRepository productionRepository;
  private final ProductionOnboardingService productionOnboardingService;
  private final ProductionService productionService;
  private final JdbcTemplate jdbc;

  public SyntheticDatasetSeeder(
      EmployeeRepository employeeRepository,
      EmployeeService employeeService,
      ProductionRepository productionRepository,
      ProductionOnboardingService productionOnboardingService,
      ProductionService productionService,
      JdbcTemplate jdbc) {
    this.employeeRepository = employeeRepository;
    this.employeeService = employeeService;
    this.productionRepository = productionRepository;
    this.productionOnboardingService = productionOnboardingService;
    this.productionService = productionService;
    this.jdbc = jdbc;
  }

  public SeedResult seed() {
    log.info("Starting synthetic dataset seeding...");
    Map<String, UUID> employeeIdMap = new HashMap<>();
    Map<String, String> employeeRoleMap = new HashMap<>();

    int empCreated = 0;
    int empExisting = 0;

    List<String> peopleLines = loadLines("people_data.txt");
    for (String line : peopleLines) {
      if (!isDataRow(line)) continue;
      String[] parts = line.split("\\|");
      if (parts.length < 18) continue;

      String employeeCode = parts[2].trim();
      LocalDate joiningDate = LocalDate.parse(parts[3].trim());
      String firstName = parts[4].trim();
      String lastName = parts[5].trim();
      String displayName = parts[6].trim();
      String roleTitle = parts[7].trim();
      String department = parts[8].trim();
      String empTypeRaw = parts[9].trim();

      String employmentType = switch (empTypeRaw.toLowerCase(Locale.ROOT)) {
        case "full time" -> "FULL_TIME";
        case "part time" -> "PART_TIME";
        case "contract" -> "CONTRACT";
        default -> empTypeRaw.toUpperCase(Locale.ROOT).replace(' ', '_');
      };

      String phone = parts[13].trim();
      String whatsapp = parts[14].trim();
      String email = parts[15].trim();
      String statusRaw = parts[16].trim();

      Employee.Status status = switch (statusRaw.toLowerCase(Locale.ROOT)) {
        case "on leave" -> Employee.Status.ON_LEAVE;
        case "inactive" -> Employee.Status.INACTIVE;
        default -> Employee.Status.ACTIVE;
      };

      String salaryRaw = parts[17].trim().replace(",", "");
      long baseSalaryMinor = Long.parseLong(salaryRaw) * 100L;
      String currency = parts[18].trim();
      if (currency.isBlank()) currency = "INR";
      String notes = parts.length > 19 ? parts[19].trim() : null;

      var existingOpt = employeeRepository.findByEmployeeCodeIgnoreCase(employeeCode);
      UUID empId;
      String role;
      if (existingOpt.isPresent()) {
        Employee existing = existingOpt.get();
        empId = existing.id;
        role = existing.roleTitle;
        empExisting++;
      } else {
        EmployeeDtos.Input input =
            new EmployeeDtos.Input(
                employeeCode,
                firstName,
                lastName.isEmpty() ? null : lastName,
                displayName,
                phone,
                whatsapp.isEmpty() ? null : whatsapp,
                email.isEmpty() ? null : email,
                roleTitle,
                department,
                employmentType,
                joiningDate,
                baseSalaryMinor,
                currency,
                status,
                null,
                notes == null || notes.isEmpty() ? null : notes);
        EmployeeDtos.View created = employeeService.create(input);
        empId = created.id();
        role = created.roleTitle();
        empCreated++;
      }
      employeeIdMap.put(displayName, empId);
      employeeRoleMap.put(displayName, role);
    }
    log.info("Employees processed: created={}, existing={}", empCreated, empExisting);

    // Seed Productions
    int prodCreated = 0;
    int prodExisting = 0;
    int crewCount = 0;
    int taskCount = 0;
    int contractCount = 0;
    int advanceCount = 0;

    List<String> prodLines = loadLines("production_data.txt");
    for (String line : prodLines) {
      if (!isDataRow(line)) continue;
      String[] parts = line.split("\\|");
      if (parts.length < 14) continue;

      String title = parts[2].trim();
      String clientName = parts[3].trim();
      String dateStr = parts[4].trim();
      LocalDate eventDate = LocalDate.parse(dateStr, PROD_DATE_FMT);
      String priorityStr = parts[5].trim();
      Production.Priority priority = Production.Priority.valueOf(priorityStr.toUpperCase(Locale.ROOT));
      String venueName = parts[6].trim();
      String venueAddress = parts[7].trim();
      String equipmentNeeded = parts[8].trim();
      String contractStr = parts[9].trim().replace(",", "").replace("₹", "");
      BigDecimal contractAmount = new BigDecimal(contractStr);
      String advanceStr = parts[10].trim().replace(",", "").replace("₹", "");
      BigDecimal advanceAmount = new BigDecimal(advanceStr);
      String timeStr = parts[11].trim();
      String crewStr = parts[12].trim();
      String tasksStr = parts[13].trim();
      String notesStr = parts.length > 14 ? parts[14].trim() : "";

      LocalTime startTime = null;
      LocalTime endTime = null;
      if (!timeStr.isBlank()) {
        String[] times = timeStr.split("[–\\-]");
        if (times.length == 2) {
          startTime = LocalTime.parse(times[0].trim());
          endTime = LocalTime.parse(times[1].trim());
        }
      }

      StringBuilder descBuilder = new StringBuilder();
      if (!notesStr.isBlank()) {
        descBuilder.append(notesStr);
      }
      if (!equipmentNeeded.isBlank()) {
        if (!descBuilder.isEmpty()) {
          descBuilder.append("\n\n");
        }
        descBuilder.append("Equipment needed: ").append(equipmentNeeded);
      }
      String description = descBuilder.isEmpty() ? null : descBuilder.toString();

      UUID contractKey =
          UUID.nameUUIDFromBytes(
              ("synthetic:contract:" + title + ":" + dateStr).getBytes(StandardCharsets.UTF_8));
      UUID advanceKey =
          UUID.nameUUIDFromBytes(
              ("synthetic:advance:" + title + ":" + dateStr).getBytes(StandardCharsets.UTF_8));

      // Idempotency check 1: finance contract transaction
      var existingContract =
          jdbc.queryForList(
              "SELECT id, production_id FROM finance_transactions WHERE idempotency_key = ?",
              contractKey);
      if (!existingContract.isEmpty()) {
        prodExisting++;
        continue;
      }

      // Idempotency check 2: production title and eventDate
      // Archive any obsolete demo production with same title but old date so it doesn't cause ambiguity
      productionRepository.findAll().stream()
          .filter(p -> p.title.equalsIgnoreCase(title) && !p.eventDate.equals(eventDate) && p.status != Production.Status.CANCELLED)
          .forEach(p -> {
            p.status = Production.Status.CANCELLED;
            productionRepository.save(p);
          });

      var existingProd =
          productionRepository.findAll().stream()
              .filter(p -> p.title.equalsIgnoreCase(title) && p.eventDate.equals(eventDate))
              .findFirst();
      if (existingProd.isPresent()) {
        prodExisting++;
        continue;
      }

      // Crew member inputs
      List<ProductionService.MemberInput> crewList = new ArrayList<>();
      if (!crewStr.isBlank()) {
        for (String memberName : crewStr.split(",")) {
          String mName = memberName.trim();
          if (!mName.isEmpty()) {
            UUID empId = employeeIdMap.get(mName);
            if (empId == null) {
              // Try finding in DB if not in map
              var empOpt = employeeRepository.findAll().stream()
                  .filter(e -> e.displayName.equalsIgnoreCase(mName))
                  .findFirst();
              if (empOpt.isPresent()) {
                empId = empOpt.get().id;
                employeeRoleMap.put(mName, empOpt.get().roleTitle);
              } else {
                throw new IllegalStateException("Crew member not found in employees: " + mName);
              }
            }
            String role = employeeRoleMap.getOrDefault(mName, "Crew");
            crewList.add(
                new ProductionService.MemberInput(
                    empId,
                    role,
                    true,
                    ProductionMember.Status.CONFIRMED,
                    true,
                    "Synthetic dataset assignment"));
          }
        }
      }

      // Operational tasks inputs
      List<ProductionService.TaskInput> taskList = new ArrayList<>();
      if (!tasksStr.isBlank()) {
        for (String taskItem : tasksStr.split(";")) {
          String tTitle = taskItem.trim();
          if (!tTitle.isEmpty()) {
            taskList.add(
                new ProductionService.TaskInput(
                    tTitle,
                    null,
                    null,
                    WorkTask.Status.TODO,
                    WorkTask.Priority.NORMAL,
                    null,
                    null));
          }
        }
      }

      ProductionOnboardingRequest req =
          new ProductionOnboardingRequest(
              title,
              clientName,
              description,
              eventDate,
              startTime,
              endTime,
              venueName,
              venueAddress.isEmpty() ? null : venueAddress,
              priority,
              0,
              crewList,
              taskList,
              List.of(),
              new ProductionOnboardingRequest.ContractInput(
                  contractKey,
                  contractAmount,
                  eventDate,
                  "Contract for " + title),
              advanceAmount.compareTo(BigDecimal.ZERO) > 0
                  ? new ProductionOnboardingRequest.AdvanceInput(
                      advanceKey,
                      advanceAmount,
                      eventDate,
                      "AZ-2",
                      "Client advance for " + title)
                  : null);

      productionOnboardingService.onboard(req);
      prodCreated++;
      crewCount += crewList.size();
      taskCount += taskList.size();
      contractCount++;
      if (advanceAmount.compareTo(BigDecimal.ZERO) > 0) {
        advanceCount++;
      }
    }

    log.info(
        "Productions processed: created={}, existing={}, crew={}, tasks={}, contracts={}, advances={}",
        prodCreated,
        prodExisting,
        crewCount,
        taskCount,
        contractCount,
        advanceCount);

    return new SeedResult(
        empCreated,
        empExisting,
        prodCreated,
        prodExisting,
        crewCount,
        taskCount,
        contractCount,
        advanceCount);
  }

  private boolean isDataRow(String line) {
    if (line == null) return false;
    return line.matches("^\\s*\\|\\s*\\d+\\s*\\|.*");
  }

  private List<String> loadLines(String fileName) {
    // 1. Try classpath resource
    try (InputStream in = getClass().getResourceAsStream("/seed/" + fileName)) {
      if (in != null) {
        return readLines(in);
      }
    } catch (IOException ignored) {
    }

    // 2. Try file system paths
    List<Path> candidatePaths =
        List.of(
            Path.of(fileName),
            Path.of("../../" + fileName),
            Path.of("apps/backend/src/main/resources/seed/" + fileName),
            Path.of("c:/Users/xtrar/Desktop/ERP/" + fileName));

    for (Path p : candidatePaths) {
      if (Files.exists(p)) {
        try (InputStream in = Files.newInputStream(p)) {
          return readLines(in);
        } catch (IOException ignored) {
        }
      }
    }

    throw new IllegalStateException("Could not find synthetic dataset file: " + fileName);
  }

  private List<String> readLines(InputStream in) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      return reader.lines().toList();
    }
  }
}
