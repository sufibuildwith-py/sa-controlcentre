package com.saproduction.navigator;

import static com.saproduction.navigator.NavigatorDtos.*;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class NavigatorService {
  private final OrganizationRepository organizations; private final PairingRepository pairings; private final DeviceRepository devices; private final SessionRepository sessions; private final JdbcTemplate jdbc; private final ApplicationEventPublisher events; private final String pepper; private final UUID publicId;
  NavigatorService(OrganizationRepository organizations,PairingRepository pairings,DeviceRepository devices,SessionRepository sessions,JdbcTemplate jdbc,ApplicationEventPublisher events,@Value("${navigator.pairing-pepper}") String pepper,@Value("${navigator.organization-public-id}") UUID publicId){this.organizations=organizations;this.pairings=pairings;this.devices=devices;this.sessions=sessions;this.jdbc=jdbc;this.events=events;this.pepper=pepper;this.publicId=publicId;}

  @Transactional Organization organization(){return organizations.findByPublicId(publicId).orElseGet(()->organizations.save(new Organization(publicId)));}

  @Transactional PairingView createPairing(UUID employeeRef,UUID createdByRef){
    var code=String.format("%06d",new SecureRandom().nextInt(1_000_000)); var now=Instant.now(); var invite=new PairingInvite();
    invite.id=UUID.randomUUID();invite.organizationId=organization().id;invite.employeeRef=employeeRef;invite.codeHash=Hashing.sha256(code+pepper);invite.expiresAt=now.plus(Duration.ofMinutes(10));invite.createdAt=now;invite.createdByRef=createdByRef;pairings.save(invite);
    audit(invite.organizationId,"PAIRING_CREATED",employeeRef,"{\"expiresAt\":\""+invite.expiresAt+"\"}");return new PairingView(invite.id,code,invite.expiresAt);
  }
  @Transactional void cancelPairing(UUID id){var i=pairings.findById(id).orElseThrow(()->notFound("PAIRING_NOT_FOUND"));if(i.redeemedAt!=null)throw conflict("PAIRING_USED");i.cancelledAt=Instant.now();pairings.save(i);}
  @Transactional Paired redeem(Redeem input){
    var now=Instant.now();var invite=pairings.findByCodeHash(Hashing.sha256(input.code()+pepper)).orElseThrow(()->new GatewayException(HttpStatus.BAD_REQUEST,"INVALID_PAIRING","Pairing code is invalid or expired."));
    if(invite.cancelledAt!=null||invite.redeemedAt!=null||!invite.expiresAt.isAfter(now))throw new GatewayException(HttpStatus.BAD_REQUEST,"INVALID_PAIRING","Pairing code is invalid or expired.");
    invite.redeemedAt=now;pairings.saveAndFlush(invite);var raw=Hashing.token();var d=new Device();d.id=UUID.randomUUID();d.organizationId=invite.organizationId;d.employeeRef=invite.employeeRef;d.tokenHash=Hashing.sha256(raw);d.platform=input.platform();d.deviceLabel=input.deviceLabel();d.registeredAt=now;devices.save(d);audit(d.organizationId,"DEVICE_PAIRED",d.employeeRef,"{\"deviceId\":\""+d.id+"\"}");return new Paired(d.id,d.employeeRef,raw);
  }
  @Transactional SessionView start(Device d,StartSession input,String trigger){
    sessions.findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc(d.id).ifPresent(s->{throw conflict("SESSION_ALREADY_ACTIVE");});var now=Instant.now();var s=new LocationSession();s.id=UUID.randomUUID();s.organizationId=d.organizationId;s.employeeRef=d.employeeRef;s.deviceId=d.id;s.consentVersion=input.consentVersion();s.trigger=trigger;s.startedAt=now;s.createdAt=now;sessions.save(s);afterCommit(new Event("TRACKING_STARTED",s.employeeRef,d.id,s.id,null,null,null,now,now));return new SessionView(s.id,now);
  }
  @Transactional SessionView startDuty(Device d, StartSession input) {
    var active = sessions.findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc(d.id);
    if (active.isPresent()) return new SessionView(active.get().id, active.get().startedAt);
    var employeeActive = sessions.findFirstByOrganizationIdAndEmployeeRefAndEndedAtIsNullOrderByStartedAtDesc(d.organizationId, d.employeeRef);
    if (employeeActive.isPresent()) {
      var session = employeeActive.get();
      // A previous handset can be left ON DUTY by an interrupted uninstall or
      // device replacement. Recover only a demonstrably stale session; never
      // terminate a recent employee duty from another device.
      if (session.startedAt.isAfter(Instant.now().minus(Duration.ofMinutes(15)))) {
        throw conflict("EMPLOYEE_DUTY_ACTIVE_ON_OTHER_DEVICE");
      }
      session.endedAt = Instant.now();
      session.endReason = "SYSTEM_RECOVERY";
      sessions.save(session);
    }
    return start(d, input, "EMPLOYEE");
  }
  @Transactional void stop(Device d,UUID id,String reason){var s=sessions.findByIdAndDeviceId(id,d.id).orElseThrow(()->notFound("SESSION_NOT_FOUND"));if(s.endedAt==null){s.endedAt=Instant.now();s.endReason=reason;sessions.save(s);afterCommit(new Event("TRACKING_STOPPED",s.employeeRef,d.id,s.id,null,null,null,s.endedAt,s.endedAt));}}
  @Transactional void endDuty(Device d, UUID id) { stop(d, id, "EMPLOYEE_STOPPED"); }
  MobileHome home(Device d) {
    var projection = jdbc.query(
        "SELECT display_name,production_ref,production_title,location_name,starts_at,ends_at FROM navigator_mobile_projections WHERE organization_id=? AND employee_ref=?",
        rs -> rs.next() ? new Object[]{rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), instant(rs.getTimestamp(5)), instant(rs.getTimestamp(6))} : null,
        d.organizationId, d.employeeRef);
    var session = sessions.findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc(d.id).orElse(null);
    var last = jdbc.query("SELECT recorded_at FROM navigator_latest_locations WHERE organization_id=? AND employee_ref=?", rs -> rs.next() ? instant(rs.getTimestamp(1)) : null, d.organizationId, d.employeeRef);
    var messages = jdbc.query("SELECT id,title,body,sent_at,read_at FROM navigator_mobile_messages WHERE organization_id=? AND employee_ref=? ORDER BY sent_at DESC LIMIT 20", (rs,n) -> new MobileMessage(UUID.fromString(rs.getString(1)),rs.getString(2),rs.getString(3),instant(rs.getTimestamp(4)),rs.getTimestamp(5)!=null), d.organizationId,d.employeeRef);
    Object[] p = projection == null ? new Object[]{"SA Productions",null,null,null,null,null} : (Object[]) projection;
    return new MobileHome((String)p[0],session!=null,session==null?null:session.id,session==null?null:session.startedAt,last,(UUID)p[1],(String)p[2],(String)p[3],(Instant)p[4],(Instant)p[5],messages);
  }
  @Transactional void upsertProjection(UUID org, Projection p) {
    jdbc.update("INSERT INTO navigator_mobile_projections(organization_id,employee_ref,display_name,production_ref,production_title,location_name,starts_at,ends_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(organization_id,employee_ref) DO UPDATE SET display_name=EXCLUDED.display_name,production_ref=EXCLUDED.production_ref,production_title=EXCLUDED.production_title,location_name=EXCLUDED.location_name,starts_at=EXCLUDED.starts_at,ends_at=EXCLUDED.ends_at,updated_at=EXCLUDED.updated_at",org,p.employeeRef(),p.displayName(),p.productionRef(),p.productionTitle(),p.locationName(),p.startsAt()==null?null:Timestamp.from(p.startsAt()),p.endsAt()==null?null:Timestamp.from(p.endsAt()),Timestamp.from(Instant.now()));
  }
  @Transactional MobileMessage sendMobileMessage(UUID org, MobileMessageInput input) {
    var id=UUID.randomUUID();var now=Instant.now();jdbc.update("INSERT INTO navigator_mobile_messages(id,organization_id,employee_ref,title,body,sent_at) VALUES (?,?,?,?,?,?)",id,org,input.employeeRef(),input.title(),input.body(),Timestamp.from(now));audit(org,"MOBILE_MESSAGE_SENT",input.employeeRef(),"{\"messageId\":\""+id+"\"}");return new MobileMessage(id,input.title(),input.body(),now,false);
  }
  @Transactional Acknowledged ingest(Device d,Batch batch){
    var s=sessions.findByIdAndDeviceId(batch.sessionId(),d.id).orElseThrow(()->new GatewayException(HttpStatus.FORBIDDEN,"WRONG_SESSION","Session does not belong to this device."));if(s.endedAt!=null)throw conflict("SESSION_ENDED");if(d.revokedAt!=null)throw new GatewayException(HttpStatus.FORBIDDEN,"DEVICE_REVOKED","Device revoked.");
    var now=Instant.now();var ack=new ArrayList<Long>();Event newest=null;
    for(var p:batch.points()){
      validatePoint(p,now);jdbc.update("INSERT INTO navigator_location_points(id,organization_id,employee_ref,device_id,session_id,sequence_no,latitude,longitude,accuracy_meters,speed_mps,heading_degrees,recorded_at,received_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(device_id,session_id,sequence_no) DO NOTHING",UUID.randomUUID(),d.organizationId,d.employeeRef,d.id,s.id,p.sequenceNo(),p.latitude(),p.longitude(),p.accuracyMeters(),p.speed(),p.heading(),Timestamp.from(p.recordedAt()),Timestamp.from(now));
      ack.add(p.sequenceNo());jdbc.update("INSERT INTO navigator_latest_locations(organization_id,employee_ref,device_id,session_id,latitude,longitude,accuracy_meters,recorded_at,received_at) VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(organization_id,employee_ref) DO UPDATE SET device_id=EXCLUDED.device_id,session_id=EXCLUDED.session_id,latitude=EXCLUDED.latitude,longitude=EXCLUDED.longitude,accuracy_meters=EXCLUDED.accuracy_meters,recorded_at=EXCLUDED.recorded_at,received_at=EXCLUDED.received_at WHERE navigator_latest_locations.recorded_at < EXCLUDED.recorded_at",d.organizationId,d.employeeRef,d.id,s.id,p.latitude(),p.longitude(),p.accuracyMeters(),Timestamp.from(p.recordedAt()),Timestamp.from(now));
      if(newest==null||p.recordedAt().isAfter(newest.recordedAt()))newest=new Event("LOCATION_UPDATED",d.employeeRef,d.id,s.id,p.latitude(),p.longitude(),p.accuracyMeters(),p.recordedAt(),now);
    }
    d.lastSeenAt=now;devices.save(d);if(newest!=null)afterCommit(newest);return new Acknowledged(ack);
  }
  List<Live> live(UUID organizationId){return jdbc.query("SELECT d.employee_ref,d.id,s.id,CASE WHEN s.id IS NULL THEN 'OFF_DUTY' WHEN l.recorded_at IS NULL OR l.recorded_at < now()-interval '3 minutes' THEN 'OFFLINE' WHEN l.recorded_at < now()-interval '60 seconds' THEN 'STALE' ELSE 'LIVE' END,l.latitude,l.longitude,l.accuracy_meters,l.recorded_at,l.received_at,s.started_at,d.registered_at,d.device_label FROM navigator_devices d LEFT JOIN navigator_location_sessions s ON s.device_id=d.id AND s.ended_at IS NULL LEFT JOIN navigator_latest_locations l ON l.organization_id=d.organization_id AND l.employee_ref=d.employee_ref WHERE d.organization_id=? AND d.revoked_at IS NULL ORDER BY d.registered_at DESC",(rs,n)->new Live(UUID.fromString(rs.getString(1)),UUID.fromString(rs.getString(2)),rs.getString(3)==null?null:UUID.fromString(rs.getString(3)),rs.getString(4),(Double)rs.getObject(5),(Double)rs.getObject(6),(Double)rs.getObject(7),instant(rs.getTimestamp(8)),instant(rs.getTimestamp(9)),instant(rs.getTimestamp(10)),instant(rs.getTimestamp(11)),rs.getString(12)),organizationId);}
  List<Map<String,Object>> history(UUID org,UUID employee,Instant from,Instant to){if(from==null||to==null||Duration.between(from,to).abs().compareTo(Duration.ofHours(24))>0)throw new GatewayException(HttpStatus.BAD_REQUEST,"BOUNDED_RANGE_REQUIRED","History requires a range of at most 24 hours.");return jdbc.queryForList("SELECT recorded_at,latitude,longitude,accuracy_meters FROM navigator_location_points WHERE organization_id=? AND employee_ref=? AND recorded_at BETWEEN ? AND ? ORDER BY recorded_at LIMIT 500",org,employee,Timestamp.from(from),Timestamp.from(to));}
  @Transactional void revoke(UUID org,UUID deviceId){var d=devices.findById(deviceId).filter(x->x.organizationId.equals(org)).orElseThrow(()->notFound("DEVICE_NOT_FOUND"));d.revokedAt=Instant.now();devices.save(d);sessions.findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc(d.id).ifPresent(s->{s.endedAt=d.revokedAt;s.endReason="DEVICE_REVOKED";sessions.save(s);});audit(org,"DEVICE_REVOKED",d.employeeRef,"{\"deviceId\":\""+d.id+"\"}");afterCommit(new Event("DEVICE_REVOKED",d.employeeRef,d.id,null,null,null,null,d.revokedAt,d.revokedAt));}
  static void validatePoint(Point p,Instant now){if(!Double.isFinite(p.latitude())||!Double.isFinite(p.longitude())||p.latitude() < -90||p.latitude()>90||p.longitude() < -180||p.longitude()>180||p.accuracyMeters()!=null&&(!Double.isFinite(p.accuracyMeters())||p.accuracyMeters()<0)||p.recordedAt().isBefore(now.minus(Duration.ofHours(24)))||p.recordedAt().isAfter(now.plus(Duration.ofMinutes(5))))throw new GatewayException(HttpStatus.BAD_REQUEST,"INVALID_LOCATION","Location point failed validation.");}
  private void audit(UUID org,String event,UUID subject,String evidence){jdbc.update("INSERT INTO navigator_security_events(id,organization_id,event_type,subject_ref,evidence_json,created_at) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),org,event,subject,evidence,Timestamp.from(Instant.now()));}
  private void afterCommit(Event event){if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){events.publishEvent(event);}});else events.publishEvent(event);}
  private static Instant instant(Timestamp t){return t==null?null:t.toInstant();} private static GatewayException notFound(String c){return new GatewayException(HttpStatus.NOT_FOUND,c,"Navigator record not found.");} private static GatewayException conflict(String c){return new GatewayException(HttpStatus.CONFLICT,c,"Navigator request conflicts with current state.");}
}
