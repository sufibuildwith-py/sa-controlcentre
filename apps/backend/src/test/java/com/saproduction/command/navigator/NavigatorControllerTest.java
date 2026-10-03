package com.saproduction.command.navigator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionMember;
import com.saproduction.command.production.ProductionMemberRepository;
import com.saproduction.command.production.ProductionRepository;
import com.saproduction.command.production.ProductionService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NavigatorControllerTest {

  private NavigatorGatewayClient gateway;
  private EmployeeRepository employees;
  private ProductionRepository productions;
  private ProductionMemberRepository members;
  private ProductionService productionService;
  private AuditService audit;
  private NavigatorConfig config;
  private NavigatorController controller;

  @BeforeEach
  void setUp() {
    gateway = mock(NavigatorGatewayClient.class);
    employees = mock(EmployeeRepository.class);
    productions = mock(ProductionRepository.class);
    members = mock(ProductionMemberRepository.class);
    productionService = mock(ProductionService.class);
    audit = mock(AuditService.class);
    config = mock(NavigatorConfig.class);

    when(config.getOrganizationPublicId()).thenReturn(UUID.randomUUID().toString());

    controller =
        new NavigatorController(
            gateway,
            employees,
            productions,
            members,
            productionService,
            audit,
            config);
  }

  @Test
  void live_resolvesDynamicProductionAndTeam_withoutHardcoding() {
    UUID emp1 = UUID.randomUUID();
    UUID emp2 = UUID.randomUUID();
    UUID prodId = UUID.randomUUID();

    // Gateway reports emp1 and emp2
    List<Map<String, Object>> remoteItems = List.of(
        Map.of("employeeRef", emp1.toString(), "deviceId", "d1", "state", "LIVE"),
        Map.of("employeeRef", emp2.toString(), "deviceId", "d2", "state", "OFF_DUTY")
    );
    when(gateway.live()).thenReturn(remoteItems);

    // People
    Employee e1 = new Employee();
    e1.id = emp1;
    e1.displayName = "Sarah Khan";
    e1.roleTitle = "Photographer";

    Employee e2 = new Employee();
    e2.id = emp2;
    e2.displayName = "Rehan Ali";
    e2.roleTitle = "Camera Operator";

    when(employees.findAllById(any())).thenReturn(List.of(e1, e2));

    // Production: Today's Varanasi Heritage Gala
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    Production prod = new Production();
    prod.id = prodId;
    prod.title = "Varanasi Heritage Gala";
    prod.eventDate = today;
    prod.status = Production.Status.PRODUCTION;

    when(productions.findById(prodId)).thenReturn(Optional.of(prod));

    // Membership: emp1 is in Camera Crew on Varanasi Heritage Gala
    ProductionMember pm1 = new ProductionMember();
    pm1.id = UUID.randomUUID();
    pm1.productionId = prodId;
    pm1.employeeId = emp1;
    pm1.productionRole = "Lead Photographer";
    pm1.teamName = "Camera Crew";

    when(members.findByEmployeeId(emp1)).thenReturn(List.of(pm1));
    // emp2 is unassigned
    when(members.findByEmployeeId(emp2)).thenReturn(List.of());

    var response = controller.live();
    assertThat(response.data()).isNotNull();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) response.data().get("items");
    assertThat(items).hasSize(2);

    // emp1 has dynamic production and team name
    var item1 = items.stream().filter(i -> i.get("employeeRef").equals(emp1.toString())).findFirst().orElseThrow();
    assertThat(item1.get("employeeName")).isEqualTo("Sarah Khan");
    assertThat(item1.get("productionTitle")).isEqualTo("Varanasi Heritage Gala");
    assertThat(item1.get("teamName")).isEqualTo("Camera Crew");
    assertThat(item1.get("productionRole")).isEqualTo("Lead Photographer");

    // emp2 has null production and null team
    var item2 = items.stream().filter(i -> i.get("employeeRef").equals(emp2.toString())).findFirst().orElseThrow();
    assertThat(item2.get("employeeName")).isEqualTo("Rehan Ali");
    assertThat(item2.get("productionTitle")).isNull();
    assertThat(item2.get("teamName")).isNull();
  }

  @Test
  void teams_delegatesToProductionService() {
    var teamView =
        new ProductionService.ProductionTeamView(
            UUID.randomUUID(),
            "Varanasi Heritage Gala",
            "Camera Crew",
            2,
            List.of());
    when(productionService.getAllActiveTeams()).thenReturn(List.of(teamView));

    var response = controller.teams();
    assertThat(response.data()).hasSize(1);
    assertThat(response.data().get(0).teamName()).isEqualTo("Camera Crew");
  }
}
