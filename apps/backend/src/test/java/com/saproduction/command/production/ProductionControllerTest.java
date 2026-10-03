package com.saproduction.command.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.saproduction.command.shared.ApiEnvelope;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductionControllerTest {

  private ProductionService service;
  private ProductionOnboardingService onboardingService;
  private ProductionController controller;

  @BeforeEach
  void setUp() {
    service = mock(ProductionService.class);
    onboardingService = mock(ProductionOnboardingService.class);
    controller = new ProductionController(service, onboardingService);
  }

  @Test
  void assignTeam_postEndpoint_delegatesToService() {
    UUID prodId = UUID.randomUUID();
    UUID emp1 = UUID.randomUUID();
    UUID emp2 = UUID.randomUUID();
    var input = new ProductionService.TeamInput("Camera Crew", List.of(emp1, emp2));

    var expectedView =
        new ProductionService.ProductionTeamView(
            prodId,
            "Gala Night",
            "Camera Crew",
            2,
            List.of(
                new ProductionService.TeamMemberView(
                    emp1, "Aarav Mehta", "Cinematographer", ProductionMember.Status.CONFIRMED),
                new ProductionService.TeamMemberView(
                    emp2, "Aditi Sharma", "Gaffer", ProductionMember.Status.CONFIRMED)));

    when(service.assignTeam(prodId, input)).thenReturn(expectedView);

    ApiEnvelope<ProductionService.ProductionTeamView> response = controller.assignTeam(prodId, input);

    assertThat(response).isNotNull();
    assertThat(response.data()).isSameAs(expectedView);
    assertThat(response.data().teamName()).isEqualTo("Camera Crew");
    assertThat(response.data().memberCount()).isEqualTo(2);
    verify(service).assignTeam(prodId, input);
  }

  @Test
  void assignTeamMembers_putEndpoint_delegatesToService() {
    UUID prodId = UUID.randomUUID();
    UUID emp1 = UUID.randomUUID();
    var input = new ProductionService.TeamInput("Sound Unit", List.of(emp1));

    var expectedView =
        new ProductionService.ProductionTeamView(
            prodId,
            "Gala Night",
            "Sound Unit",
            1,
            List.of(
                new ProductionService.TeamMemberView(
                    emp1, "Farhan Ali", "Sound Recordist", ProductionMember.Status.CONFIRMED)));

    when(service.assignTeam(prodId, input)).thenReturn(expectedView);

    ApiEnvelope<ProductionService.ProductionTeamView> response = controller.assignTeamMembers(prodId, input);

    assertThat(response).isNotNull();
    assertThat(response.data()).isSameAs(expectedView);
    verify(service).assignTeam(prodId, input);
  }

  @Test
  void deleteTeam_delegatesToServiceAndReturnsRemovedMap() {
    UUID prodId = UUID.randomUUID();
    var response = controller.deleteTeam(prodId, "Camera Crew");

    assertThat(response).isNotNull();
    assertThat(response.data()).containsEntry("removed", true);
    verify(service).deleteTeam(prodId, "Camera Crew");
  }

  @Test
  void teams_delegatesToService() {
    UUID prodId = UUID.randomUUID();
    when(service.getTeams(prodId)).thenReturn(List.of());

    var response = controller.teams(prodId);
    assertThat(response.data()).isEmpty();
    verify(service).getTeams(prodId);
  }

  @Test
  void allTeams_delegatesToService() {
    when(service.getAllActiveTeams()).thenReturn(List.of());

    var response = controller.allTeams();
    assertThat(response.data()).isEmpty();
    verify(service).getAllActiveTeams();
  }
}
