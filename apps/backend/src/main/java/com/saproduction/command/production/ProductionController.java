package com.saproduction.command.production;
import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/productions")
public class ProductionController {
  private final ProductionService service;public ProductionController(ProductionService service){this.service=service;}
  @GetMapping public ApiEnvelope<List<ProductionService.View>> list(@RequestParam(required=false) Production.Status status,@RequestParam(required=false) Production.Priority priority,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,@RequestParam(required=false) String search){return ApiEnvelope.of(service.list(status,priority,from,to,search));}
  @PostMapping public ApiEnvelope<ProductionService.View> create(@Valid @RequestBody ProductionService.Input input){return ApiEnvelope.of(service.create(input));}
  @GetMapping("/{id}") public ApiEnvelope<ProductionService.View> get(@PathVariable UUID id){return ApiEnvelope.of(service.get(id));}
  @PatchMapping("/{id}") public ApiEnvelope<ProductionService.View> update(@PathVariable UUID id,@Valid @RequestBody ProductionService.Input input){return ApiEnvelope.of(service.update(id,input));}
  @PostMapping("/{id}/transition") public ApiEnvelope<ProductionService.View> transition(@PathVariable UUID id,@Valid @RequestBody ProductionService.Transition input){return ApiEnvelope.of(service.transition(id,input.status()));}
  @PostMapping("/{id}/members") public ApiEnvelope<ProductionService.View> member(@PathVariable UUID id,@Valid @RequestBody ProductionService.MemberInput input){return ApiEnvelope.of(service.addMember(id,input));}
  @PatchMapping("/{id}/members/{employeeId}") public ApiEnvelope<ProductionService.View> memberStatus(@PathVariable UUID id,@PathVariable UUID employeeId,@Valid @RequestBody ProductionService.MemberStatusInput input){return ApiEnvelope.of(service.updateMemberStatus(id,employeeId,input));}
  @DeleteMapping("/{id}/members/{employeeId}") public ApiEnvelope<Map<String,Boolean>> remove(@PathVariable UUID id,@PathVariable UUID employeeId){service.removeMember(id,employeeId);return ApiEnvelope.of(Map.of("removed",true));}
}
