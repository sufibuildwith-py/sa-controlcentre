package com.saproduction.navigator;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers(disabledWithoutDocker=true)
@SpringBootTest
class NavigatorPostgresTest {
  @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
  @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
  @Autowired JdbcTemplate jdbc;
  UUID org,device,employee,session;
  @BeforeEach void seed(){jdbc.update("DELETE FROM navigator_location_points;DELETE FROM navigator_latest_locations;DELETE FROM navigator_location_sessions;DELETE FROM navigator_devices;DELETE FROM navigator_pairing_invites;DELETE FROM navigator_security_events;DELETE FROM navigator_organizations");org=UUID.randomUUID();device=UUID.randomUUID();employee=UUID.randomUUID();session=UUID.randomUUID();var now=Timestamp.from(Instant.now());jdbc.update("INSERT INTO navigator_organizations VALUES (?,?,?)",org,UUID.randomUUID(),now);jdbc.update("INSERT INTO navigator_devices(id,organization_id,employee_ref,token_hash,platform,registered_at) VALUES (?,?,?,?,?,?)",device,org,employee,UUID.randomUUID().toString(),"ANDROID",now);jdbc.update("INSERT INTO navigator_location_sessions(id,organization_id,employee_ref,device_id,consent_version,trigger,started_at,created_at) VALUES (?,?,?,?,?,?,?,?)",session,org,employee,device,"v1","EMPLOYEE",now,now);}
  @Test void migrationCreatesAllCoreTables(){assertEquals(7,jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'navigator_%'",Integer.class));}
  @Test void coordinateConstraintRejectsInvalidLatitude(){assertThrows(Exception.class,()->point(1,91,80));}
  @Test void uniqueDeviceSessionSequenceIsIdempotencyBoundary(){point(1,26,80);assertThrows(Exception.class,()->point(1,26,80));}
  @Test void latestLocationUsesTenantEmployeePrimaryKey(){var now=Timestamp.from(Instant.now());jdbc.update("INSERT INTO navigator_latest_locations VALUES (?,?,?,?,?,?,?,?,?)",org,employee,device,session,26d,80d,5d,now,now);assertThrows(Exception.class,()->jdbc.update("INSERT INTO navigator_latest_locations VALUES (?,?,?,?,?,?,?,?,?)",org,employee,device,session,27d,81d,5d,now,now));}
  @Test void organizationScopedQueryExcludesOtherTenant(){point(1,26,80);assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM navigator_location_points WHERE organization_id=?",Integer.class,org));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM navigator_location_points WHERE organization_id=?",Integer.class,UUID.randomUUID()));}
  private void point(long sequence,double lat,double lon){var now=Timestamp.from(Instant.now());jdbc.update("INSERT INTO navigator_location_points(id,organization_id,employee_ref,device_id,session_id,sequence_no,latitude,longitude,recorded_at,received_at) VALUES (?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),org,employee,device,session,sequence,lat,lon,now,now);}
}
