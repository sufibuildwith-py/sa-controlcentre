package com.saproduction.command.headquarters;

import static com.saproduction.command.headquarters.HeadquartersController.*;

import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class HeadquartersDemoDataConfig {
  @Bean
  @Order(30)
  CommandLineRunner headquartersDemo(
      @Value("${app.demo-seed}") boolean enabled,
      HeadquartersService service,
      ProductionRepository productions,
      JdbcTemplate jdbc) {
    return args -> {
      if (!enabled || count(jdbc, "hq_equipment") > 0) return;

      UUID operations = category(service, "Operations", null);
      UUID camera = category(service, "Camera", operations);
      UUID lighting = category(service, "Lighting", operations);
      UUID audio = category(service, "Audio", operations);
      UUID consumables = category(service, "Consumables", operations);

      UUID piece = unit(service, "Piece", "pc", false);
      UUID roll = unit(service, "Roll", "roll", false);
      UUID pack = unit(service, "Pack", "pack", false);

      UUID warehouse = location(service, "Main Warehouse", "HEADQUARTERS", null, null);
      UUID cameraCage = location(service, "Camera Cage", "STORAGE_ZONE", warehouse, null);
      UUID lightingBay = location(service, "Lighting Bay", "STORAGE_ZONE", warehouse, null);
      UUID consumablesStore =
          location(service, "Consumables Store", "STORAGE_ZONE", warehouse, null);
      location(service, "Service Workshop", "WORKSHOP", warehouse, null);

      List<Production> productionList = productions.findAll();
      Production primary = productionList.isEmpty() ? null : productionList.getFirst();
      Production secondary = productionList.size() > 1 ? productionList.get(1) : primary;
      UUID primaryVenue =
          location(
              service,
              primary == null ? "Demo Venue A" : primary.title + " · Venue",
              "PRODUCTION_LOCATION",
              null,
              primary == null ? null : primary.id);
      UUID secondaryVenue =
          location(
              service,
              secondary == null ? "Demo Venue B" : secondary.title + " · Venue",
              "PRODUCTION_LOCATION",
              null,
              secondary == null ? null : secondary.id);

      UUID cases =
          equipment(service, "Flight Cases", "DEMO-HQ-001", "Synthetic quantity-tracked demo equipment", operations, piece, "QUANTITY", "5", warehouse, "SA_OWNED");
      UUID lights =
          equipment(service, "LED Light Panels", "DEMO-HQ-002", "Synthetic lighting stock for availability and logistics", lighting, piece, "QUANTITY", "4", lightingBay, "SA_OWNED");
      UUID cameras =
          equipment(service, "Cinema Camera Package", "DEMO-HQ-003", "Synthetic individually tracked camera assets", camera, piece, "SERIALIZED", "1", cameraCage, "SA_OWNED");
      UUID wirelessAudio =
          equipment(service, "Wireless Audio Kit", "DEMO-HQ-004", "Synthetic serialized audio equipment", audio, piece, "SERIALIZED", "1", cameraCage, "SA_OWNED");
      UUID tape =
          equipment(service, "Gaffer Tape", "DEMO-HQ-005", "Synthetic consumable demo stock", consumables, roll, "CONSUMABLE", "20", consumablesStore, "SA_OWNED");
      UUID batteries =
          equipment(service, "AA Battery Packs", "DEMO-HQ-006", "Synthetic consumable demo stock", consumables, pack, "CONSUMABLE", "30", consumablesStore, "SA_OWNED");
      UUID rentedStands =
          equipment(service, "Rented C-Stands", "DEMO-HQ-007", "Synthetic rental custody; excluded from owned inventory", lighting, piece, "QUANTITY", "0", lightingBay, "RENTED");
      UUID clientProps =
          equipment(service, "Client-Supplied Props", "DEMO-HQ-008", "Synthetic client property held in operational custody", operations, piece, "QUANTITY", "0", warehouse, "CLIENT_SUPPLIED");

      stock(service, cases, warehouse, "60", "Demo opening count");
      stock(service, lights, lightingBay, "36", "Demo opening count");
      stock(service, tape, consumablesStore, "120", "Demo opening count");
      stock(service, batteries, consumablesStore, "240", "Demo opening count");
      stock(service, rentedStands, lightingBay, "12", "Demo rental received");
      stock(service, clientProps, warehouse, "18", "Demo client property received");

      asset(service, cameras, "CAM-A01", "DEMO-CAMERA-001", cameraCage);
      asset(service, cameras, "CAM-A02", "DEMO-CAMERA-002", cameraCage);
      UUID serviceCamera = asset(service, cameras, "CAM-A03", "DEMO-CAMERA-003", cameraCage);
      asset(service, wirelessAudio, "AUD-W01", "DEMO-AUDIO-001", cameraCage);
      asset(service, wirelessAudio, "AUD-W02", "DEMO-AUDIO-002", cameraCage);

      service.maintenance(
          new MaintenanceInput(cameras, serviceCamera, cameraCage, "SCHEDULED_INSPECTION", BigDecimal.ONE, Instant.now().plus(Duration.ofDays(2)), "Synthetic sensor inspection example", key("maintenance-camera-a03")));
      service.issue(
          new IssueInput("MISSING", clientProps, null, BigDecimal.ONE, warehouse, primary == null ? null : primary.id, "WARNING", "Synthetic missing-item case for owner review"));

      if (primary != null) {
        Instant starts = primary.eventDate.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
        service.reserve(
            new ReservationInput(
                primary.id,
                starts,
                starts.plus(Duration.ofHours(12)),
                List.of(
                    new ReservationLine(cases, primaryVenue, new BigDecimal("20")),
                    new ReservationLine(lights, primaryVenue, new BigDecimal("12")))));

        UUID dispatch =
            id(service.createDispatch(new DispatchInput(primary.id, warehouse, primaryVenue, starts.minus(Duration.ofHours(2)), "Synthetic production dispatch", List.of(new OperationLine(cases, null, new BigDecimal("8"))))));
        service.confirmDispatch(dispatch, key("dispatch-primary-cases"));

        UUID returned =
            id(service.createReturn(new ReturnInput(primary.id, primaryVenue, warehouse, "Synthetic partial-condition return", List.of(new ReturnLine(cases, null, new BigDecimal("3"), new BigDecimal("2"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO)))));
        service.confirmReturn(returned, key("return-primary-cases"));

        if (secondary != null && !secondary.id.equals(primary.id)) {
          UUID transfer =
              id(service.createTransfer(new TransferInput(primary.id, secondary.id, primaryVenue, secondaryVenue, "Synthetic direct venue transfer", List.of(new OperationLine(cases, null, new BigDecimal("2"))))));
          service.confirmTransfer(transfer, key("transfer-cases-between-productions"));
        }
      }
    };
  }

  private static long count(JdbcTemplate jdbc, String table) {
    Long value = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    return value == null ? 0 : value;
  }

  private static UUID category(HeadquartersService service, String name, UUID parentId) {
    return (UUID) service.category(new CategoryInput(name, parentId, "Synthetic Headquarters demonstration data")).get("id");
  }

  private static UUID unit(HeadquartersService service, String name, String symbol, boolean decimalAllowed) {
    return (UUID) service.unit(new UnitInput(name, symbol, decimalAllowed)).get("id");
  }

  private static UUID location(HeadquartersService service, String name, String type, UUID parentId, UUID productionId) {
    return (UUID) service.location(new LocationInput(name, type, parentId, productionId)).get("id");
  }

  @SuppressWarnings("unchecked")
  private static UUID equipment(HeadquartersService service, String name, String code, String description, UUID category, UUID unit, String tracking, String reserve, UUID location, String ownership) {
    Map<String, Object> item =
        (Map<String, Object>) service.createEquipment(new EquipmentInput(name, code, description, category, unit, tracking, new BigDecimal(reserve), location, ownership)).get("item");
    return (UUID) item.get("id");
  }

  private static void stock(HeadquartersService service, UUID equipment, UUID location, String quantity, String reason) {
    service.stock(equipment, new StockInput(location, new BigDecimal(quantity), reason, key("stock-" + equipment)));
  }

  private static UUID asset(HeadquartersService service, UUID equipment, String assetCode, String serialNumber, UUID location) {
    return (UUID) service.asset(equipment, new AssetInput(assetCode, serialNumber, location, "Synthetic serialized demo asset", key("asset-" + assetCode))).get("id");
  }

  private static UUID id(Map<String, Object> value) {
    return (UUID) value.get("id");
  }

  private static UUID key(String value) {
    return UUID.nameUUIDFromBytes(("headquarters-demo:" + value).getBytes(StandardCharsets.UTF_8));
  }
}
