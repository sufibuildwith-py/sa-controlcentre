package com.saproduction.command.headquarters;

import static com.saproduction.command.headquarters.HeadquartersController.*;
import static org.assertj.core.api.Assertions.*;

import com.saproduction.command.shared.ApiException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class HeadquartersPostgresTest {
  @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
  @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",postgres::getJdbcUrl);p.add("spring.datasource.username",postgres::getUsername);p.add("spring.datasource.password",postgres::getPassword);p.add("app.demo-seed",()->false);}
  @Autowired HeadquartersService service;
  @Autowired com.saproduction.command.production.ProductionService productionService;
  @Autowired JdbcTemplate jdbc;
  UUID equipment,location,production;

  @BeforeEach void setup(){
    var suffix=UUID.randomUUID().toString().substring(0,8);
    var unit=(UUID)service.unit(new UnitInput("Test unit "+suffix,"tu",false)).get("id");
    location=(UUID)service.location(new LocationInput("Test HQ "+suffix,"HEADQUARTERS",null,null)).get("id");
    equipment=(UUID)((Map<?,?>)service.createEquipment(new EquipmentInput("Test equipment "+suffix,"HQ-"+suffix,null,null,unit,"QUANTITY",BigDecimal.ZERO,location,"SA_OWNED")).get("item")).get("id");
    service.stock(equipment,new StockInput(location,new BigDecimal("50"),"Opening test position",UUID.randomUUID()));
    production=UUID.randomUUID();jdbc.update("INSERT INTO productions(id,title,client_name,event_date,start_time,end_time,venue_name,status,priority,progress_percent) VALUES(?,?,?,current_date,'10:00','18:00','Test venue','PLANNING','NORMAL',0)",production,"HQ Test "+suffix,"Test Client");
  }

  @Test void duplicateStockRequestDoesNotPostTwice(){var key=UUID.randomUUID();var input=new StockInput(location,new BigDecimal("5"),"Count correction",key);service.stock(equipment,input);service.stock(equipment,input);assertThat(jdbc.queryForObject("SELECT physical_quantity FROM hq_inventory_positions WHERE equipment_id=? AND location_id=? AND condition='GOOD'",BigDecimal.class,equipment,location)).isEqualByComparingTo("55");assertThat(jdbc.queryForObject("SELECT count(*) FROM hq_inventory_movements WHERE idempotency_key=?",Long.class,key)).isEqualTo(1);}

  @Test void concurrentReservationsNeverOvercommit(){
    var start=Instant.now().plus(Duration.ofDays(1));var end=start.plus(Duration.ofHours(8));var ready=new CountDownLatch(2);var go=new CountDownLatch(1);var success=new AtomicInteger();var conflicts=new AtomicInteger();
    try(var pool=Executors.newFixedThreadPool(2)){var futures=List.of(new BigDecimal("40"),new BigDecimal("30")).stream().map(q->pool.submit(()->{ready.countDown();go.await();try{service.reserve(new ReservationInput(production,start,end,List.of(new ReservationLine(equipment,null,q))));success.incrementAndGet();}catch(ApiException e){assertThat(e.code).isEqualTo("AVAILABILITY_CHANGED");conflicts.incrementAndGet();}return null;})).toList();ready.await(5,TimeUnit.SECONDS);go.countDown();for(var f:futures)f.get();}catch(Exception e){throw new AssertionError(e);}
    assertThat(success).hasValue(1);assertThat(conflicts).hasValue(1);var committed=jdbc.queryForObject("SELECT COALESCE(sum(rl.quantity),0) FROM hq_reservation_lines rl JOIN hq_reservations r ON r.id=rl.reservation_id WHERE rl.equipment_id=? AND r.status='CONFIRMED'",BigDecimal.class,equipment);assertThat(committed).isLessThanOrEqualTo(new BigDecimal("50"));
  }

  @Test void organizationBoundaryAndProjectionReconciliationHold(){
    var foreign=UUID.randomUUID();jdbc.update("INSERT INTO hq_equipment(id,organization_id,name,unit_id,tracking_mode,ownership_default) SELECT ?,?, 'Foreign equipment',unit_id,'QUANTITY','SA_OWNED' FROM hq_equipment WHERE id=?",foreign,UUID.randomUUID(),equipment);
    assertThatThrownBy(()->service.equipment(foreign)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code).isEqualTo("EQUIPMENT_NOT_FOUND"));
    assertThat(service.reconcile()).containsEntry("consistent",true);
  }

  @Test void dispatchTransferPartialReturnAndIssueRemainReconciled(){
    var venueA=(UUID)service.location(new LocationInput("Venue A "+UUID.randomUUID(),"PRODUCTION_LOCATION",null,production)).get("id");
    var venueB=(UUID)service.location(new LocationInput("Venue B "+UUID.randomUUID(),"PRODUCTION_LOCATION",null,production)).get("id");
    var dispatch=(UUID)service.createDispatch(new DispatchInput(production,location,venueA,null,"Field test",List.of(new OperationLine(equipment,null,new BigDecimal("20"))))).get("id");var dispatchKey=UUID.randomUUID();service.confirmDispatch(dispatch,dispatchKey);service.confirmDispatch(dispatch,dispatchKey);
    var transfer=(UUID)service.createTransfer(new TransferInput(production,production,venueA,venueB,"Direct venue transfer",List.of(new OperationLine(equipment,null,new BigDecimal("5"))))).get("id");service.confirmTransfer(transfer,UUID.randomUUID());
    var returns=(UUID)service.createReturn(new ReturnInput(production,venueA,location,"Partial reconciliation",List.of(new ReturnLine(equipment,null,new BigDecimal("15"),new BigDecimal("10"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("2"),new BigDecimal("1"))))).get("id");var returnKey=UUID.randomUUID();var result=service.confirmReturn(returns,returnKey);service.confirmReturn(returns,returnKey);
    assertThat(result).containsEntry("status","PARTIAL");assertThat(service.attention()).extracting(x->x.get("type")).contains("UNACCOUNTED_RETURN","DAMAGED_AWAITING_DECISION","MISSING_EQUIPMENT");assertThat(jdbc.queryForObject("SELECT count(*) FROM hq_inventory_movements WHERE dispatch_id=?",Long.class,dispatch)).isEqualTo(1);assertThat(service.reconcile()).containsEntry("consistent",true);
  }

  @Test
  void addEquipmentEndToEnd_persistsReservationAndRefreshesProductionView() {
    var view =
        productionService.addEquipment(
            production,
            new com.saproduction.command.production.ProductionService.EquipmentInput(
                equipment, BigDecimal.valueOf(5), null, null));

    assertThat(view.equipment()).hasSize(1);
    assertThat(view.equipment().getFirst().equipmentId()).isEqualTo(equipment);
    assertThat(view.equipment().getFirst().quantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
    assertThat(view.equipment().getFirst().status()).isEqualTo("CONFIRMED");

    // Visible from headquarters
    var hqList = service.equipmentForProduction(production);
    assertThat(hqList).hasSize(1);
    assertThat(hqList.getFirst()).containsEntry("equipmentId", equipment);

    // Remove equipment
    var afterRemove = productionService.removeEquipment(production, equipment);
    assertThat(afterRemove.equipment()).isEmpty();
    assertThat(service.equipmentForProduction(production)).isEmpty();
  }

  @Test
  void addEquipment_withoutScheduledTimes_usesWholeDayWindow() {
    UUID unscheduledProd = UUID.randomUUID();
    LocalDate today = LocalDate.now();
    jdbc.update(
        "INSERT INTO productions(id,title,client_name,event_date,start_time,end_time,venue_name,status,priority,progress_percent) VALUES(?,?,?,current_date,null,null,'Test venue','PLANNING','NORMAL',0)",
        unscheduledProd,
        "Unscheduled Prod",
        "Test Client");

    var view =
        productionService.addEquipment(
            unscheduledProd,
            new com.saproduction.command.production.ProductionService.EquipmentInput(
                equipment, BigDecimal.valueOf(3), null, null));

    assertThat(view.equipment()).hasSize(1);
    assertThat(view.equipment().getFirst().equipmentId()).isEqualTo(equipment);

    // Verify DB timestamps match whole-day bounds (00:00:00 to 23:59:59 in Asia/Kolkata)
    ZoneId zone = ZoneId.of("Asia/Kolkata");
    Instant expectedStart = today.atStartOfDay(zone).toInstant();
    Instant expectedEnd = today.atTime(23, 59, 59).atZone(zone).toInstant();

    var res =
        jdbc.queryForMap(
            "SELECT starts_at, ends_at FROM hq_reservations WHERE production_id=?",
            unscheduledProd);
    Instant actualStart = ((java.sql.Timestamp) res.get("starts_at")).toInstant();
    Instant actualEnd = ((java.sql.Timestamp) res.get("ends_at")).toInstant();

    assertThat(actualStart).isEqualTo(expectedStart);
    assertThat(actualEnd).isEqualTo(expectedEnd);
  }

  @Test
  void addEquipment_withValidSchedule_reservesExactProductionWindow() {
    LocalDate today = LocalDate.now();
    var created =
        productionService.create(
            new com.saproduction.command.production.ProductionService.CreateInput(
                "Scheduled Prod",
                "Test Client",
                null,
                today,
                LocalTime.of(16, 30),
                LocalTime.of(21, 30),
                "Scheduled Venue",
                null,
                null,
                null,
                null,
                null,
                null));
    UUID scheduledProd = created.id();

    var view =
        productionService.addEquipment(
            scheduledProd,
            new com.saproduction.command.production.ProductionService.EquipmentInput(
                equipment, BigDecimal.valueOf(2), null, null));

    assertThat(view.equipment()).hasSize(1);

    ZoneId zone = ZoneId.of("Asia/Kolkata");
    Instant expectedStart = today.atTime(16, 30).atZone(zone).toInstant();
    Instant expectedEnd = today.atTime(21, 30).atZone(zone).toInstant();

    var res =
        jdbc.queryForMap(
            "SELECT starts_at, ends_at FROM hq_reservations WHERE production_id=?",
            scheduledProd);
    Instant actualStart = ((java.sql.Timestamp) res.get("starts_at")).toInstant();
    Instant actualEnd = ((java.sql.Timestamp) res.get("ends_at")).toInstant();

    assertThat(actualStart).isEqualTo(expectedStart);
    assertThat(actualEnd).isEqualTo(expectedEnd);
  }

  @Test
  void schedule_withSingleBoundary_rejectsWithInvalidProductionTime() {
    LocalDate today = LocalDate.now();
    assertThatThrownBy(
            () ->
                productionService.create(
                    new com.saproduction.command.production.ProductionService.CreateInput(
                        "Single Boundary Prod",
                        "Test Client",
                        null,
                        today,
                        LocalTime.of(16, 30),
                        null,
                        "Incomplete Venue",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e -> {
              ApiException ex = (ApiException) e;
              assertThat(ex.code).isEqualTo("INVALID_PRODUCTION_TIME");
              assertThat(ex.getMessage()).contains("Both start time and end time must be specified");
            });
  }

  @Test
  void schedule_withInvertedTimes_rejectsAndDoesNotSilentlyFallback() {
    LocalDate today = LocalDate.now();
    assertThatThrownBy(
            () ->
                productionService.create(
                    new com.saproduction.command.production.ProductionService.CreateInput(
                        "Inverted Prod",
                        "Test Client",
                        null,
                        today,
                        LocalTime.of(16, 30),
                        LocalTime.of(15, 30),
                        "Inverted Venue",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e -> {
              ApiException ex = (ApiException) e;
              assertThat(ex.code).isEqualTo("INVALID_PRODUCTION_TIME");
              assertThat(ex.getMessage()).contains("End time must be after start time");
            });
  }

  @Test
  void schedule_withEqualStartAndEndTime_rejectsWithInvalidProductionTime() {
    LocalDate today = LocalDate.now();
    assertThatThrownBy(
            () ->
                productionService.create(
                    new com.saproduction.command.production.ProductionService.CreateInput(
                        "Equal Time Prod",
                        "Test Client",
                        null,
                        today,
                        LocalTime.of(16, 30),
                        LocalTime.of(16, 30),
                        "Equal Time Venue",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e -> {
              ApiException ex = (ApiException) e;
              assertThat(ex.code).isEqualTo("INVALID_PRODUCTION_TIME");
              assertThat(ex.getMessage()).contains("End time must be after start time");
            });
  }

  @Test
  void addEquipment_idempotentRetry_doesNotCreateDuplicateReservation() {
    LocalDate today = LocalDate.now();
    var created =
        productionService.create(
            new com.saproduction.command.production.ProductionService.CreateInput(
                "Retry Prod",
                "Test Client",
                null,
                today,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "Retry Venue",
                null,
                null,
                null,
                null,
                null,
                null));
    UUID retryProd = created.id();

    var input =
        new com.saproduction.command.production.ProductionService.EquipmentInput(
            equipment, BigDecimal.valueOf(2), null, null);

    // Call 1
    var view1 = productionService.addEquipment(retryProd, input);
    assertThat(view1.equipment()).hasSize(1);

    // Call 2 (retry / double click with identical quantity)
    var view2 = productionService.addEquipment(retryProd, input);
    assertThat(view2.equipment()).hasSize(1);

    // Only 1 reservation line in DB
    var lines = service.equipmentForProduction(retryProd);
    assertThat(lines).hasSize(1);
    assertThat((BigDecimal) lines.getFirst().get("quantity")).isEqualByComparingTo("2");
  }

  @Test
  void addEquipment_retryWithDifferentQuantity_rejectsWithConflictAndGuidance() {
    LocalDate today = LocalDate.now();
    var created =
        productionService.create(
            new com.saproduction.command.production.ProductionService.CreateInput(
                "Qty Prod",
                "Test Client",
                null,
                today,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "Qty Venue",
                null,
                null,
                null,
                null,
                null,
                null));
    UUID qtyProd = created.id();

    var initialInput =
        new com.saproduction.command.production.ProductionService.EquipmentInput(
            equipment, BigDecimal.valueOf(2), null, null);
    productionService.addEquipment(qtyProd, initialInput);

    // Attempting to add same equipment with quantity 5 while actively reserved
    var changedInput =
        new com.saproduction.command.production.ProductionService.EquipmentInput(
            equipment, BigDecimal.valueOf(5), null, null);

    assertThatThrownBy(() -> productionService.addEquipment(qtyProd, changedInput))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e -> {
              ApiException ex = (ApiException) e;
              assertThat(ex.code).isEqualTo("EQUIPMENT_ALREADY_ASSIGNED");
              assertThat(ex.getMessage()).contains("Remove the existing assignment first to adjust quantity");
            });
  }

  @Test
  void addEquipment_legitimateQuantityChangeAfterRemoval_succeeds() {
    LocalDate today = LocalDate.now();
    var created =
        productionService.create(
            new com.saproduction.command.production.ProductionService.CreateInput(
                "Replace Prod",
                "Test Client",
                null,
                today,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                "Replace Venue",
                null,
                null,
                null,
                null,
                null,
                null));
    UUID replaceProd = created.id();

    // 1. Initial assignment: 1 unit
    productionService.addEquipment(
        replaceProd,
        new com.saproduction.command.production.ProductionService.EquipmentInput(
            equipment, BigDecimal.valueOf(1), null, null));
    var lines1 = service.equipmentForProduction(replaceProd);
    assertThat(lines1).hasSize(1);
    assertThat((BigDecimal) lines1.getFirst().get("quantity")).isEqualByComparingTo("1");

    // 2. Remove assignment
    productionService.removeEquipment(replaceProd, equipment);
    assertThat(service.equipmentForProduction(replaceProd)).isEmpty();

    // 3. Re-assign with new quantity: 3 units (NOT a permanent no-op)
    var updatedView =
        productionService.addEquipment(
            replaceProd,
            new com.saproduction.command.production.ProductionService.EquipmentInput(
                equipment, BigDecimal.valueOf(3), null, null));
    assertThat(updatedView.equipment()).hasSize(1);
    assertThat(updatedView.equipment().getFirst().quantity()).isEqualByComparingTo(BigDecimal.valueOf(3));

    var lines2 = service.equipmentForProduction(replaceProd);
    assertThat(lines2).hasSize(1);
    assertThat((BigDecimal) lines2.getFirst().get("quantity")).isEqualByComparingTo("3");
  }
}
