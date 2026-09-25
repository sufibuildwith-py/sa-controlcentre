package com.saproduction.navigator;

import static com.saproduction.navigator.NavigatorDtos.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Component
class RetentionJob {
  private final JdbcTemplate jdbc; private final int rawDays,latestHours,pairingHours,auditDays;
  RetentionJob(JdbcTemplate jdbc,@Value("${navigator.retention.raw-days}") int rawDays,@Value("${navigator.retention.latest-hours}") int latestHours,@Value("${navigator.retention.pairing-hours}") int pairingHours,@Value("${navigator.retention.audit-days}") int auditDays){this.jdbc=jdbc;this.rawDays=rawDays;this.latestHours=latestHours;this.pairingHours=pairingHours;this.auditDays=auditDays;}
  @Scheduled(cron="0 17 3 * * *") @Transactional void clean(){var now=Instant.now();jdbc.update("DELETE FROM navigator_location_points WHERE recorded_at < ?",Timestamp.from(now.minus(Duration.ofDays(rawDays))));jdbc.update("DELETE FROM navigator_latest_locations WHERE recorded_at < ?",Timestamp.from(now.minus(Duration.ofHours(latestHours))));jdbc.update("DELETE FROM navigator_pairing_invites WHERE (expires_at < ? OR redeemed_at IS NOT NULL OR cancelled_at IS NOT NULL) AND created_at < ?",Timestamp.from(now),Timestamp.from(now.minus(Duration.ofHours(pairingHours))));jdbc.update("DELETE FROM navigator_security_events WHERE created_at < ?",Timestamp.from(now.minus(Duration.ofDays(auditDays))));}
}

@RestController
@RequestMapping("/api/v1/admin/simulator")
class SimulatorController {
  private final SimulatorService simulator;private final GatewayAuth auth;private final boolean enabled;
  SimulatorController(SimulatorService simulator,GatewayAuth auth,@Value("${navigator.simulator-enabled:false}") boolean enabled){this.simulator=simulator;this.auth=auth;this.enabled=enabled;}
  @PostMapping("/start") Map<String,Object> start(@RequestHeader("X-Navigator-Service-Key") String key,@RequestBody @jakarta.validation.Valid SimulatorStart body){auth.requireAdmin(key);if(!enabled)throw new GatewayException(org.springframework.http.HttpStatus.NOT_FOUND,"SIMULATOR_DISABLED","Simulator unavailable.");simulator.start(body.employeeRefs());return Map.of("running",true);}
  @PostMapping("/stop") Map<String,Object> stop(@RequestHeader("X-Navigator-Service-Key") String key){auth.requireAdmin(key);simulator.stop();return Map.of("running",false);}
}

@Component
class SimulatorService {
  private final NavigatorService service;private final DeviceRepository devices;private final SessionRepository sessions;private final AtomicInteger step=new AtomicInteger();private volatile List<Device> active=List.of();
  SimulatorService(NavigatorService service,DeviceRepository devices,SessionRepository sessions){this.service=service;this.devices=devices;this.sessions=sessions;}
  @Transactional void start(List<UUID> refs){stop();var org=service.organization();var made=new ArrayList<Device>();for(int i=0;i<refs.size();i++){var d=new Device();d.id=UUID.randomUUID();d.organizationId=org.id;d.employeeRef=refs.get(i);d.tokenHash=Hashing.sha256("simulator:"+d.id);d.platform="SIMULATOR";d.deviceLabel="Navigator Simulator";d.registeredAt=Instant.now();devices.save(d);made.add(d);if(i<3){var s=service.start(d,new StartSession("demo-v1"),"SIMULATOR");var age=i==2?Duration.ofMinutes(3):Duration.ZERO;service.ingest(d,new Batch(s.sessionId(),List.of(new Point(1,26.4490+i*.004,80.3310+i*.004,8.0,3.0,45.0,Instant.now().minus(age)))));}}active=List.copyOf(made);}
  @Transactional void stop(){for(var d:active)sessions.findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc(d.id).ifPresent(s->service.stop(d,s.id,"EMPLOYEE_STOPPED"));active=List.of();}
  @Scheduled(fixedDelay=5000) void move(){if(active.isEmpty())return;var d=active.getFirst();sessions.findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc(d.id).ifPresent(s->{var n=step.incrementAndGet();service.ingest(d,new Batch(s.id,List.of(new Point(n+1,26.4490+n*.00018,80.3310+n*.00022,7.0,4.0,45.0,Instant.now()))));});}
}
