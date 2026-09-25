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
}
