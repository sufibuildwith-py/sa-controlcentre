package com.saproduction.command.production;

import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/productions")
public class ProductionController {
  private final ProductionService service;
  private final ProductionOnboardingService onboardingService;

  public ProductionController(
      ProductionService service, ProductionOnboardingService onboardingService) {
    this.service = service;
    this.onboardingService = onboardingService;
  }

  @GetMapping
  public ApiEnvelope<List<ProductionService.View>> list(
      @RequestParam(required = false) Production.Status status,
      @RequestParam(required = false) Production.Priority priority,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) String search) {
    return ApiEnvelope.of(service.list(status, priority, from, to, search));
  }

  @PostMapping
  public ApiEnvelope<ProductionService.View> create(
      @Valid @RequestBody ProductionOnboardingRequest input) {
    return ApiEnvelope.of(onboardingService.onboard(input));
  }

  @GetMapping("/teams")
  public ApiEnvelope<List<ProductionService.ProductionTeamView>> allTeams() {
    return ApiEnvelope.of(service.getAllActiveTeams());
  }

  @GetMapping("/{id}")
  public ApiEnvelope<ProductionService.View> get(@PathVariable UUID id) {
    return ApiEnvelope.of(service.get(id));
  }

  @PatchMapping("/{id}")
  public ApiEnvelope<ProductionService.View> update(
      @PathVariable UUID id, @Valid @RequestBody ProductionService.Input input) {
    return ApiEnvelope.of(service.update(id, input));
  }

  @PostMapping("/{id}/transition")
  public ApiEnvelope<ProductionService.View> transition(
      @PathVariable UUID id, @Valid @RequestBody ProductionService.Transition input) {
    return ApiEnvelope.of(service.transition(id, input.status()));
  }

  @PostMapping("/{id}/members")
  public ApiEnvelope<ProductionService.View> member(
      @PathVariable UUID id, @Valid @RequestBody ProductionService.MemberInput input) {
    return ApiEnvelope.of(service.addMember(id, input));
  }

  @PatchMapping("/{id}/members/{employeeId}")
  public ApiEnvelope<ProductionService.View> memberStatus(
      @PathVariable UUID id,
      @PathVariable UUID employeeId,
      @Valid @RequestBody ProductionService.MemberStatusInput input) {
    return ApiEnvelope.of(service.updateMemberStatus(id, employeeId, input));
  }

  @DeleteMapping("/{id}/members/{employeeId}")
  public ApiEnvelope<Map<String, Boolean>> remove(
      @PathVariable UUID id, @PathVariable UUID employeeId) {
    service.removeMember(id, employeeId);
    return ApiEnvelope.of(Map.of("removed", true));
  }

  @GetMapping("/{id}/teams")
  public ApiEnvelope<List<ProductionService.ProductionTeamView>> teams(@PathVariable UUID id) {
    return ApiEnvelope.of(service.getTeams(id));
  }

  @PostMapping("/{id}/teams")
  public ApiEnvelope<ProductionService.ProductionTeamView> assignTeam(
      @PathVariable UUID id, @Valid @RequestBody ProductionService.TeamInput input) {
    return ApiEnvelope.of(service.assignTeam(id, input));
  }

  @PutMapping("/{id}/members/team")
  public ApiEnvelope<ProductionService.ProductionTeamView> assignTeamMembers(
      @PathVariable UUID id, @Valid @RequestBody ProductionService.TeamInput input) {
    return ApiEnvelope.of(service.assignTeam(id, input));
  }

  @DeleteMapping("/{id}/teams/{teamName}")
  public ApiEnvelope<Map<String, Boolean>> deleteTeam(
      @PathVariable UUID id, @PathVariable String teamName) {
    service.deleteTeam(id, teamName);
    return ApiEnvelope.of(Map.of("removed", true));
  }

  @PostMapping("/{id}/equipment")
  public ApiEnvelope<ProductionService.View> addEquipment(
      @PathVariable UUID id, @Valid @RequestBody ProductionService.EquipmentInput input) {
    return ApiEnvelope.of(service.addEquipment(id, input));
  }

  @DeleteMapping("/{id}/equipment/{equipmentId}")
  public ApiEnvelope<ProductionService.View> removeEquipment(
      @PathVariable UUID id, @PathVariable UUID equipmentId) {
    return ApiEnvelope.of(service.removeEquipment(id, equipmentId));
  }
}
