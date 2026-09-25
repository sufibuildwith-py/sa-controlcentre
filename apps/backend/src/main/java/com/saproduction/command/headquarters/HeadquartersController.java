package com.saproduction.command.headquarters;

import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/headquarters")
public class HeadquartersController {
  public record CategoryInput(@NotBlank @Size(max = 120) String name, UUID parentId, @Size(max = 500) String description) {}
  public record UnitInput(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 24) String symbol, boolean decimalAllowed) {}
  public record LocationInput(@NotBlank @Size(max = 140) String name, @NotBlank String locationType, UUID parentId, UUID productionId) {}
  public record EquipmentInput(@NotBlank @Size(max = 180) String name, @Size(max = 80) String internalCode, @Size(max = 4000) String description, UUID categoryId, @NotNull UUID unitId, @NotBlank String trackingMode, @DecimalMin("0") BigDecimal minimumReserve, UUID defaultLocationId, @NotBlank String ownership) {}
  public record StockInput(@NotNull UUID locationId, @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity, @NotBlank @Size(max = 500) String reason, @NotNull UUID idempotencyKey) {}
  public record AdjustmentInput(@NotNull UUID locationId, @NotNull BigDecimal difference, @NotBlank @Size(max = 500) String reason, @NotNull UUID idempotencyKey) {}
  public record AssetInput(@NotBlank @Size(max = 100) String assetCode, @Size(max = 160) String serialNumber, @NotNull UUID locationId, @Size(max = 1000) String notes, @NotNull UUID idempotencyKey) {}
  public record ReservationLine(@NotNull UUID equipmentId, UUID productionLocationId, @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity) {}
  public record ReservationInput(@NotNull UUID productionId, @NotNull Instant startsAt, @NotNull Instant endsAt, @NotEmpty List<@Valid ReservationLine> lines) {}
  public record OperationLine(@NotNull UUID equipmentId, UUID assetId, @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity) {}
  public record DispatchInput(@NotNull UUID productionId, @NotNull UUID sourceLocationId, @NotNull UUID destinationLocationId, Instant scheduledAt, @Size(max = 1000) String notes, @NotEmpty List<@Valid OperationLine> lines) {}
  public record TransferInput(UUID sourceProductionId, UUID destinationProductionId, @NotNull UUID sourceLocationId, @NotNull UUID destinationLocationId, @Size(max = 1000) String notes, @NotEmpty List<@Valid OperationLine> lines) {}
  public record ReturnLine(@NotNull UUID equipmentId, UUID assetId, @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal expected, @DecimalMin("0") BigDecimal returned, @DecimalMin("0") BigDecimal transferred, @DecimalMin("0") BigDecimal consumed, @DecimalMin("0") BigDecimal damaged, @DecimalMin("0") BigDecimal missing) {}
  public record ReturnInput(@NotNull UUID productionId, @NotNull UUID sourceLocationId, @NotNull UUID destinationLocationId, @Size(max = 1000) String notes, @NotEmpty List<@Valid ReturnLine> lines) {}
  public record ConfirmInput(@NotNull UUID idempotencyKey) {}
  public record IssueInput(@NotBlank String issueType, @NotNull UUID equipmentId, UUID assetId, @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity, UUID locationId, UUID productionId, String severity, @NotBlank @Size(max = 1500) String notes) {}
  public record ResolveInput(@NotBlank String resolution, @NotBlank @Size(max = 1000) String notes, @NotNull UUID idempotencyKey) {}
  public record MaintenanceInput(@NotNull UUID equipmentId, UUID assetId, @NotNull UUID locationId, @NotBlank @Size(max = 80) String maintenanceType, @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity, Instant dueAt, @Size(max = 1500) String notes, @NotNull UUID idempotencyKey) {}

  private final HeadquartersService service;
  public HeadquartersController(HeadquartersService service) { this.service = service; }

  @GetMapping("/overview") public ApiEnvelope<Map<String,Object>> overview() { return ApiEnvelope.of(service.overview()); }
  @GetMapping("/equipment") public ApiEnvelope<Map<String,Object>> equipment(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(required=false) String query,@RequestParam(required=false) String tracking,@RequestParam(required=false) UUID category) { return ApiEnvelope.of(service.equipment(page,size,query,tracking,category)); }
  @GetMapping("/equipment/{id}") public ApiEnvelope<Map<String,Object>> equipment(@PathVariable UUID id) { return ApiEnvelope.of(service.equipment(id)); }
  @GetMapping("/movements") public ApiEnvelope<Map<String,Object>> movements(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size,@RequestParam(required=false) UUID equipmentId) { return ApiEnvelope.of(service.movements(page,size,equipmentId)); }
  @GetMapping("/attention") public ApiEnvelope<List<Map<String,Object>>> attention() { return ApiEnvelope.of(service.attention()); }
  @GetMapping("/config") public ApiEnvelope<Map<String,Object>> config() { return ApiEnvelope.of(service.config()); }
  @GetMapping("/availability/{equipmentId}") public ApiEnvelope<Map<String,Object>> availability(@PathVariable UUID equipmentId,@RequestParam Instant startsAt,@RequestParam Instant endsAt) { return ApiEnvelope.of(service.availability(equipmentId,startsAt,endsAt)); }

  @PostMapping("/categories") public ApiEnvelope<Map<String,Object>> category(@Valid @RequestBody CategoryInput in){return ApiEnvelope.of(service.category(in));}
  @PostMapping("/units") public ApiEnvelope<Map<String,Object>> unit(@Valid @RequestBody UnitInput in){return ApiEnvelope.of(service.unit(in));}
  @PostMapping("/locations") public ApiEnvelope<Map<String,Object>> location(@Valid @RequestBody LocationInput in){return ApiEnvelope.of(service.location(in));}
  @PostMapping("/equipment") public ApiEnvelope<Map<String,Object>> equipment(@Valid @RequestBody EquipmentInput in){return ApiEnvelope.of(service.createEquipment(in));}
  @PostMapping("/equipment/{id}/stock-in") public ApiEnvelope<Map<String,Object>> stock(@PathVariable UUID id,@Valid @RequestBody StockInput in){return ApiEnvelope.of(service.stock(id,in));}
  @PostMapping("/equipment/{id}/adjust") public ApiEnvelope<Map<String,Object>> adjust(@PathVariable UUID id,@Valid @RequestBody AdjustmentInput in){return ApiEnvelope.of(service.adjust(id,in));}
  @PostMapping("/equipment/{id}/assets") public ApiEnvelope<Map<String,Object>> asset(@PathVariable UUID id,@Valid @RequestBody AssetInput in){return ApiEnvelope.of(service.asset(id,in));}
  @PostMapping("/reservations") public ApiEnvelope<Map<String,Object>> reservation(@Valid @RequestBody ReservationInput in){return ApiEnvelope.of(service.reserve(in));}
  @PostMapping("/dispatches") public ApiEnvelope<Map<String,Object>> dispatch(@Valid @RequestBody DispatchInput in){return ApiEnvelope.of(service.createDispatch(in));}
  @PostMapping("/dispatches/{id}/confirm") public ApiEnvelope<Map<String,Object>> confirmDispatch(@PathVariable UUID id,@Valid @RequestBody ConfirmInput in){return ApiEnvelope.of(service.confirmDispatch(id,in.idempotencyKey()));}
  @PostMapping("/transfers") public ApiEnvelope<Map<String,Object>> transfer(@Valid @RequestBody TransferInput in){return ApiEnvelope.of(service.createTransfer(in));}
  @PostMapping("/transfers/{id}/confirm") public ApiEnvelope<Map<String,Object>> confirmTransfer(@PathVariable UUID id,@Valid @RequestBody ConfirmInput in){return ApiEnvelope.of(service.confirmTransfer(id,in.idempotencyKey()));}
  @PostMapping("/returns") public ApiEnvelope<Map<String,Object>> returns(@Valid @RequestBody ReturnInput in){return ApiEnvelope.of(service.createReturn(in));}
  @PostMapping("/returns/{id}/confirm") public ApiEnvelope<Map<String,Object>> confirmReturn(@PathVariable UUID id,@Valid @RequestBody ConfirmInput in){return ApiEnvelope.of(service.confirmReturn(id,in.idempotencyKey()));}
  @PostMapping("/issues") public ApiEnvelope<Map<String,Object>> issue(@Valid @RequestBody IssueInput in){return ApiEnvelope.of(service.issue(in));}
  @PostMapping("/issues/{id}/resolve") public ApiEnvelope<Map<String,Object>> resolve(@PathVariable UUID id,@Valid @RequestBody ResolveInput in){return ApiEnvelope.of(service.resolveIssue(id,in));}
  @PostMapping("/maintenance") public ApiEnvelope<Map<String,Object>> maintenance(@Valid @RequestBody MaintenanceInput in){return ApiEnvelope.of(service.maintenance(in));}
  @PostMapping("/maintenance/{id}/complete") public ApiEnvelope<Map<String,Object>> maintenanceComplete(@PathVariable UUID id,@Valid @RequestBody ConfirmInput in){return ApiEnvelope.of(service.completeMaintenance(id,in.idempotencyKey()));}
  @PostMapping("/reconcile") public ApiEnvelope<Map<String,Object>> reconcile(){return ApiEnvelope.of(service.reconcile());}
}
