package com.saproduction.command.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.calendar.CalendarEvent;
import com.saproduction.command.calendar.CalendarService;
import com.saproduction.command.communication.DomainEventService;
import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.headquarters.HeadquartersController;
import com.saproduction.command.headquarters.HeadquartersService;
import com.saproduction.command.shared.ApiException;
import com.saproduction.command.work.WorkTask;
import com.saproduction.command.work.WorkTaskService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ProductionServiceTest {

  private ProductionRepository productions;
  private ProductionMemberRepository members;
  private CalendarService calendar;
  private EmployeeService employees;
  private WorkTaskService workTasks;
  private HeadquartersService headquarters;
  private JdbcTemplate jdbc;
  private AuditService audit;
  private DomainEventService events;
  private ProductionService service;

  private final UUID productionId = UUID.randomUUID();
  private final UUID employeeId = UUID.randomUUID();
  private final UUID equipmentId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    productions = mock(ProductionRepository.class);
    members = mock(ProductionMemberRepository.class);
    calendar = mock(CalendarService.class);
    employees = mock(EmployeeService.class);
    workTasks = mock(WorkTaskService.class);
    headquarters = mock(HeadquartersService.class);
    jdbc = mock(JdbcTemplate.class);
    audit = mock(AuditService.class);
    events = mock(DomainEventService.class);

    service =
        new ProductionService(
            productions,
            members,
            calendar,
            employees,
            workTasks,
            headquarters,
            jdbc,
            audit,
            events,
            "Asia/Kolkata");

    // Standard stubbing
    when(productions.saveAndFlush(any(Production.class)))
        .thenAnswer(
            inv -> {
              Production p = inv.getArgument(0);
              if (p.id == null) p.id = productionId;
              return p;
            });
    when(productions.findById(productionId))
        .thenAnswer(
            inv -> {
              Production p = new Production();
              p.id = productionId;
              p.title = "Sharma Wedding";
              p.clientName = "Sharma Family";
              p.eventDate = LocalDate.of(2026, 9, 26);
              p.venueName = "Royal Orchid";
              p.status = Production.Status.DRAFT;
              p.priority = Production.Priority.NORMAL;
              return Optional.of(p);
            });
    when(jdbc.queryForObject(contains("select count(*) from tasks"), eq(Long.class), any()))
        .thenReturn(0L);
    when(headquarters.equipmentForProduction(any())).thenReturn(List.of());
  }

  @Test
  void create_withRequiredCoreDetailsOnly_succeedsWithoutOptionalSections() {
    var input =
        new ProductionService.CreateInput(
            "Sharma Wedding",
            "Sharma Family",
            "VIP wedding production",
            LocalDate.of(2026, 9, 26),
            null, // no startTime
            null, // no endTime
            "Royal Orchid",
            "MG Road",
            Production.Priority.HIGH,
            0,
            null, // no crew
            null, // no tasks
            null // no equipment
            );

    var view = service.create(input);

    assertThat(view).isNotNull();
    assertThat(view.title()).isEqualTo("Sharma Wedding");
    assertThat(view.startTime()).isNull();
    assertThat(view.endTime()).isNull();
    assertThat(view.members()).isEmpty();
    assertThat(view.equipment()).isEmpty();

    // Verify calendar event was not created for unscheduled production
    verify(calendar, never())
        .syncProduction(any(), any(), any(), any(), any(), any(), any());
    verify(workTasks, never()).create(any());
    verify(headquarters, never()).reserve(any());
  }

  @Test
  void create_withFullCompositeSections_orchestratesAllDomainsAtomically() {
    var crew =
        List.of(
            new ProductionService.MemberInput(
                employeeId,
                "Lead Sound Engineer",
                true,
                ProductionMember.Status.PENDING,
                false,
                null));

    var tasks =
        List.of(
            new ProductionService.TaskInput(
                "Confirm venue power",
                "Check 3-phase supply",
                employeeId,
                WorkTask.Status.TODO,
                WorkTask.Priority.HIGH,
                LocalDate.of(2026, 9, 25),
                null));

    var equipment =
        List.of(
            new ProductionService.EquipmentInput(
                equipmentId, BigDecimal.valueOf(4), null, "Main stage cabinets"));

    var input =
        new ProductionService.CreateInput(
            "Sharma Wedding",
            "Sharma Family",
            "VIP wedding production",
            LocalDate.of(2026, 9, 26),
            LocalTime.of(16, 30),
            LocalTime.of(21, 30),
            "Royal Orchid",
            "MG Road",
            Production.Priority.HIGH,
            0,
            crew,
            tasks,
            equipment);

    CalendarEvent mockEvent = new CalendarEvent();
    mockEvent.id = UUID.randomUUID();
    when(calendar.syncProduction(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(mockEvent);
    when(calendar.findEventForProduction(any())).thenReturn(Optional.of(mockEvent));

    var view = service.create(input);

    assertThat(view).isNotNull();
    // 1. Production saved
    verify(productions).saveAndFlush(any(Production.class));

    // 2. Schedule synced with calendar
    verify(calendar)
        .syncProduction(
            eq(productionId),
            eq("Sharma Wedding"),
            any(),
            any(Instant.class),
            any(Instant.class),
            eq("Royal Orchid"),
            eq("MG Road"));

    // 3. Crew member assigned
    verify(members).saveAndFlush(any(ProductionMember.class));
    verify(calendar).addAttendee(eq(mockEvent), eq(employeeId), eq(false), any());

    // 4. Tasks created through WorkTaskService
    ArgumentCaptor<WorkTaskService.Input> taskCaptor =
        ArgumentCaptor.forClass(WorkTaskService.Input.class);
    verify(workTasks).create(taskCaptor.capture());
    assertThat(taskCaptor.getValue().title()).isEqualTo("Confirm venue power");
    assertThat(taskCaptor.getValue().productionId()).isEqualTo(productionId);

    // 5. Equipment reserved through HeadquartersService
    ArgumentCaptor<HeadquartersController.ReservationInput> eqCaptor =
        ArgumentCaptor.forClass(HeadquartersController.ReservationInput.class);
    verify(headquarters).reserve(eqCaptor.capture());
    assertThat(eqCaptor.getValue().productionId()).isEqualTo(productionId);
    assertThat(eqCaptor.getValue().lines()).hasSize(1);
    assertThat(eqCaptor.getValue().lines().get(0).equipmentId()).isEqualTo(equipmentId);
    assertThat(eqCaptor.getValue().lines().get(0).quantity()).isEqualByComparingTo("4");
  }

  @Test
  void create_invalidTimeOrder_throwsBadRequest() {
    var input =
        new ProductionService.CreateInput(
            "Sharma Wedding",
            "Sharma Family",
            null,
            LocalDate.of(2026, 9, 26),
            LocalTime.of(21, 30),
            LocalTime.of(16, 30), // end before start
            "Royal Orchid",
            null,
            Production.Priority.NORMAL,
            0,
            null,
            null,
            null);

    assertThatThrownBy(() -> service.create(input))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("End time must be after start time");
  }

  @Test
  void addEquipment_and_removeEquipment_callHeadquarters() {
    Production mockProd = new Production();
    mockProd.id = productionId;
    mockProd.title = "Sharma Wedding";
    mockProd.clientName = "Sharma Family";
    mockProd.eventDate = LocalDate.of(2026, 9, 26);
    mockProd.venueName = "Royal Orchid";
    mockProd.startTime = LocalTime.of(10, 0);
    mockProd.endTime = LocalTime.of(18, 0);
    mockProd.status = Production.Status.DRAFT;
    when(productions.findById(productionId)).thenReturn(Optional.of(mockProd));

    // Add equipment
    var eqIn =
        new ProductionService.EquipmentInput(
            equipmentId, BigDecimal.valueOf(2), null, "Stage mic kit");
    service.addEquipment(productionId, eqIn);

    verify(headquarters).reserve(any(HeadquartersController.ReservationInput.class));

    // Remove equipment
    UUID lineId = UUID.randomUUID();
    when(jdbc.queryForList(anyString(), eq(UUID.class), eq(productionId), eq(equipmentId)))
        .thenReturn(List.of(lineId));

    service.removeEquipment(productionId, equipmentId);
    verify(headquarters).removeReservationLine(lineId);
  }

  @Test
  void create_withEmptyOrNullOperationalNotes_succeedsAndStoresNullDescription() {
    // 1. Empty string description
    var inputEmptyNotes =
        new ProductionService.CreateInput(
            "Sharma Wedding",
            "Sharma Family",
            "",
            LocalDate.of(2026, 9, 26),
            null,
            null,
            "Royal Orchid",
            null,
            Production.Priority.NORMAL,
            0,
            null,
            null,
            null);

    var viewEmpty = service.create(inputEmptyNotes);
    assertThat(viewEmpty).isNotNull();
    assertThat(viewEmpty.description()).isNull();

    // 2. Null description
    var inputNullNotes =
        new ProductionService.CreateInput(
            "Sharma Wedding",
            "Sharma Family",
            null,
            LocalDate.of(2026, 9, 26),
            null,
            null,
            "Royal Orchid",
            null,
            Production.Priority.NORMAL,
            0,
            null,
            null,
            null);

    var viewNull = service.create(inputNullNotes);
    assertThat(viewNull).isNotNull();
    assertThat(viewNull.description()).isNull();

    // 3. Populated description
    var inputWithNotes =
        new ProductionService.CreateInput(
            "Sharma Wedding",
            "Sharma Family",
            "VIP guest arrival instructions",
            LocalDate.of(2026, 9, 26),
            null,
            null,
            "Royal Orchid",
            null,
            Production.Priority.NORMAL,
            0,
            null,
            null,
            null);

    var viewWithNotes = service.create(inputWithNotes);
    assertThat(viewWithNotes).isNotNull();
    assertThat(viewWithNotes.description()).isEqualTo("VIP guest arrival instructions");
  }

  @Test
  void beanValidation_verifiesRequiredFields_and_ensuresOperationalNotesIsOptional() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    // Valid: Operational notes is empty string
    var emptyNotesInput =
        new ProductionService.CreateInput(
            "Sharma Wedding",
            "Sharma Family",
            "",
            LocalDate.of(2026, 9, 26),
            null,
            null,
            "Royal Orchid",
            null,
            Production.Priority.NORMAL,
            0,
            null,
            null,
            null);
    assertThat(validator.validate(emptyNotesInput)).isEmpty();

    // Valid: Operational notes is null
    var nullNotesInput =
        new ProductionService.CreateInput(
            "Sharma Wedding",
            "Sharma Family",
            null,
            LocalDate.of(2026, 9, 26),
            null,
            null,
            "Royal Orchid",
            null,
            Production.Priority.NORMAL,
            0,
            null,
            null,
            null);
    assertThat(validator.validate(nullNotesInput)).isEmpty();

    // Invalid: Missing title
    var missingTitle =
        new ProductionService.CreateInput(
            "",
            "Sharma Family",
            "",
            LocalDate.of(2026, 9, 26),
            null,
            null,
            "Royal Orchid",
            null,
            Production.Priority.NORMAL,
            0,
            null,
            null,
            null);
    var titleViolations = validator.validate(missingTitle);
    assertThat(titleViolations).anyMatch(v -> v.getPropertyPath().toString().equals("title"));

    // Invalid: Missing client
    var missingClient =
        new ProductionService.CreateInput(
            "Sharma Wedding",
            "",
            "",
            LocalDate.of(2026, 9, 26),
            null,
            null,
            "Royal Orchid",
            null,
            Production.Priority.NORMAL,
            0,
            null,
            null,
            null);
    var clientViolations = validator.validate(missingClient);
    assertThat(clientViolations).anyMatch(v -> v.getPropertyPath().toString().equals("clientName"));

    // Invalid: Missing venue
    var missingVenue =
        new ProductionService.CreateInput(
            "Sharma Wedding",
            "Sharma Family",
            "",
            LocalDate.of(2026, 9, 26),
            null,
            null,
            "",
            null,
            Production.Priority.NORMAL,
            0,
            null,
            null,
            null);
    var venueViolations = validator.validate(missingVenue);
    assertThat(venueViolations).anyMatch(v -> v.getPropertyPath().toString().equals("venueName"));

    // Invalid: Missing event date
    var missingDate =
        new ProductionService.CreateInput(
            "Sharma Wedding",
            "Sharma Family",
            "",
            null,
            null,
            null,
            "Royal Orchid",
            null,
            Production.Priority.NORMAL,
            0,
            null,
            null,
            null);
    var dateViolations = validator.validate(missingDate);
    assertThat(dateViolations).anyMatch(v -> v.getPropertyPath().toString().equals("eventDate"));
  }

  @Test
  void assignTeam_assignsExistingMembersAndCreatesNewMembersWithTeamName() {
    UUID emp1 = UUID.randomUUID();
    UUID emp2 = UUID.randomUUID();

    ProductionMember existingPm = new ProductionMember();
    existingPm.id = UUID.randomUUID();
    existingPm.productionId = productionId;
    existingPm.employeeId = emp1;
    existingPm.productionRole = "Photographer";

    when(members.findAllByProductionIdOrderByCreatedAt(productionId))
        .thenReturn(new ArrayList<>(List.of(existingPm)));

    Employee e1 = new Employee();
    e1.id = emp1;
    e1.displayName = "Sarah Khan";
    e1.roleTitle = "Photographer";

    Employee e2 = new Employee();
    e2.id = emp2;
    e2.displayName = "Rehan Ali";
    e2.roleTitle = "Camera Operator";

    when(employees.getEntity(emp1)).thenReturn(e1);
    when(employees.getEntity(emp2)).thenReturn(e2);

    ProductionMember savedPm2 = new ProductionMember();
    savedPm2.id = UUID.randomUUID();
    savedPm2.productionId = productionId;
    savedPm2.employeeId = emp2;
    savedPm2.productionRole = "Camera Operator";
    savedPm2.teamName = "Camera Crew";

    when(members.findAllByProductionIdAndTeamNameIgnoreCase(productionId, "Camera Crew"))
        .thenReturn(List.of(existingPm, savedPm2));
    when(jdbc.queryForObject(contains("select display_name"), eq(String.class), eq(emp1)))
        .thenReturn("Sarah Khan");
    when(jdbc.queryForObject(contains("select display_name"), eq(String.class), eq(emp2)))
        .thenReturn("Rehan Ali");

    var result =
        service.assignTeam(
            productionId,
            new ProductionService.TeamInput("Camera Crew", List.of(emp1, emp2)));

    assertThat(result.teamName()).isEqualTo("Camera Crew");
    assertThat(result.memberCount()).isEqualTo(2);
    assertThat(result.members()).hasSize(2);
    assertThat(result.members().stream().map(ProductionService.TeamMemberView::employeeName))
        .containsExactlyInAnyOrder("Sarah Khan", "Rehan Ali");
    assertThat(existingPm.teamName).isEqualTo("Camera Crew");

    verify(audit)
        .record(
            eq("PRODUCTION"),
            eq("PRODUCTION_TEAM_ASSIGNED"),
            eq(productionId.toString()),
            isNull(),
            any());
  }

  @Test
  void assignTeam_clearsTeamForUnselectedEmployeesPreviouslyInTeam() {
    UUID emp1 = UUID.randomUUID();
    UUID emp2 = UUID.randomUUID();

    ProductionMember pm1 = new ProductionMember();
    pm1.id = UUID.randomUUID();
    pm1.productionId = productionId;
    pm1.employeeId = emp1;
    pm1.productionRole = "Photographer";
    pm1.teamName = "Camera Crew";

    ProductionMember pm2 = new ProductionMember();
    pm2.id = UUID.randomUUID();
    pm2.productionId = productionId;
    pm2.employeeId = emp2;
    pm2.productionRole = "Assistant";
    pm2.teamName = "Camera Crew";

    when(members.findAllByProductionIdOrderByCreatedAt(productionId))
        .thenReturn(new ArrayList<>(List.of(pm1, pm2)));

    Employee e1 = new Employee();
    e1.id = emp1;
    e1.displayName = "Sarah Khan";
    e1.roleTitle = "Photographer";
    when(employees.getEntity(emp1)).thenReturn(e1);

    when(members.findAllByProductionIdAndTeamNameIgnoreCase(productionId, "Camera Crew"))
        .thenReturn(List.of(pm1));
    when(jdbc.queryForObject(contains("select display_name"), eq(String.class), eq(emp1)))
        .thenReturn("Sarah Khan");

    // Only emp1 is retained in Camera Crew
    var result =
        service.assignTeam(
            productionId,
            new ProductionService.TeamInput("Camera Crew", List.of(emp1)));

    assertThat(result.memberCount()).isEqualTo(1);
    assertThat(pm1.teamName).isEqualTo("Camera Crew");
    assertThat(pm2.teamName).isNull(); // Cleared
  }

  @Test
  void getTeams_groupsMembersByTeamName() {
    UUID emp1 = UUID.randomUUID();
    UUID emp2 = UUID.randomUUID();

    ProductionMember pm1 = new ProductionMember();
    pm1.id = UUID.randomUUID();
    pm1.productionId = productionId;
    pm1.employeeId = emp1;
    pm1.productionRole = "Photographer";
    pm1.teamName = "Camera Crew";

    ProductionMember pm2 = new ProductionMember();
    pm2.id = UUID.randomUUID();
    pm2.productionId = productionId;
    pm2.employeeId = emp2;
    pm2.productionRole = "Sound Engineer";
    pm2.teamName = "Sound Crew";

    when(members.findAllByProductionIdOrderByCreatedAt(productionId)).thenReturn(List.of(pm1, pm2));
    when(jdbc.queryForObject(contains("select display_name"), eq(String.class), eq(emp1)))
        .thenReturn("Sarah Khan");
    when(jdbc.queryForObject(contains("select display_name"), eq(String.class), eq(emp2)))
        .thenReturn("Ahmed");

    var teams = service.getTeams(productionId);
    assertThat(teams).hasSize(2);
    assertThat(teams.stream().map(ProductionService.ProductionTeamView::teamName))
        .containsExactly("Camera Crew", "Sound Crew");
  }

  @Test
  void deleteTeam_clearsTeamNameFromMembers() {
    UUID emp1 = UUID.randomUUID();
    ProductionMember pm1 = new ProductionMember();
    pm1.id = UUID.randomUUID();
    pm1.productionId = productionId;
    pm1.employeeId = emp1;
    pm1.productionRole = "Photographer";
    pm1.teamName = "Camera Crew";

    when(members.findAllByProductionIdAndTeamNameIgnoreCase(productionId, "Camera Crew"))
        .thenReturn(List.of(pm1));

    service.deleteTeam(productionId, "Camera Crew");

    assertThat(pm1.teamName).isNull();
    verify(audit)
        .record(
            eq("PRODUCTION"),
            eq("PRODUCTION_TEAM_REMOVED"),
            eq(productionId.toString()),
            any(),
            isNull());
  }

  @Test
  void assignTeam_validatesTeamNameAndEmployees() {
    assertThatThrownBy(
            () ->
                service.assignTeam(
                    productionId,
                    new ProductionService.TeamInput("", List.of(UUID.randomUUID()))))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Team name cannot be empty");

    assertThatThrownBy(
            () ->
                service.assignTeam(
                    productionId,
                    new ProductionService.TeamInput("Camera Crew", List.of())))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("At least one employee must be selected");
  }
}
