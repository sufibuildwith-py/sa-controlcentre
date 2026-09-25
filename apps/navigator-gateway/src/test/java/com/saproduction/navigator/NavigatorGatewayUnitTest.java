package com.saproduction.navigator;

import static com.saproduction.navigator.NavigatorDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class NavigatorGatewayUnitTest {
  OrganizationRepository organizations=mock(OrganizationRepository.class);PairingRepository pairings=mock(PairingRepository.class);DeviceRepository devices=mock(DeviceRepository.class);SessionRepository sessions=mock(SessionRepository.class);JdbcTemplate jdbc=mock(JdbcTemplate.class);ApplicationEventPublisher events=mock(ApplicationEventPublisher.class);
  NavigatorService service(){return new NavigatorService(organizations,pairings,devices,sessions,jdbc,events,"pepper",UUID.randomUUID());}
  @Test void pairingExpirationRejected(){var p=invite();p.expiresAt=Instant.now().minusSeconds(1);when(pairings.findByCodeHash(any())).thenReturn(Optional.of(p));assertEquals("INVALID_PAIRING",assertThrows(GatewayException.class,()->service().redeem(new Redeem("123456","ANDROID",null))).code);}
  @Test void pairingSingleUseRejected(){var p=invite();p.redeemedAt=Instant.now();when(pairings.findByCodeHash(any())).thenReturn(Optional.of(p));assertThrows(GatewayException.class,()->service().redeem(new Redeem("123456","ANDROID",null)));}
  @Test void deviceTokenHashIsStableAndRawTokenStrong(){assertEquals(Hashing.sha256("x"),Hashing.sha256("x"));assertTrue(Hashing.token().length()>=40);}
  @Test void revokedDeviceTokenRejected(){var d=device();d.revokedAt=Instant.now();when(devices.findByTokenHash(any())).thenReturn(Optional.of(d));var auth=new GatewayAuth("admin",UUID.randomUUID(),devices);assertEquals("DEVICE_REVOKED",assertThrows(GatewayException.class,()->auth.requireDevice("Bearer token")).code);}
  @Test void wrongSessionOwnershipRejected(){var d=device();when(sessions.findByIdAndDeviceId(any(),eq(d.id))).thenReturn(Optional.empty());assertEquals("WRONG_SESSION",assertThrows(GatewayException.class,()->service().ingest(d,new Batch(UUID.randomUUID(),List.of(point(1))))).code);}
  @Test void endedDutyRejectsLaterLocation(){var d=device();var s=new LocationSession();s.id=UUID.randomUUID();s.endedAt=Instant.now();when(sessions.findByIdAndDeviceId(s.id,d.id)).thenReturn(Optional.of(s));assertEquals("SESSION_ENDED",assertThrows(GatewayException.class,()->service().ingest(d,new Batch(s.id,List.of(point(1))))).code);}
  @Test void secondDutyStartReturnsActiveSession(){var d=device();var s=new LocationSession();s.id=UUID.randomUUID();s.startedAt=Instant.now();when(sessions.findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc(d.id)).thenReturn(Optional.of(s));assertEquals(s.id,service().startDuty(d,new StartSession("v1")).sessionId());}
  @Test void staleDutyOnReplacedDeviceIsRecovered(){var d=device();var stale=new LocationSession();stale.id=UUID.randomUUID();stale.startedAt=Instant.now().minus(Duration.ofMinutes(16));when(sessions.findFirstByDeviceIdAndEndedAtIsNullOrderByStartedAtDesc(d.id)).thenReturn(Optional.empty());when(sessions.findFirstByOrganizationIdAndEmployeeRefAndEndedAtIsNullOrderByStartedAtDesc(d.organizationId,d.employeeRef)).thenReturn(Optional.of(stale));var started=service().startDuty(d,new StartSession("v1"));assertNotEquals(stale.id,started.sessionId());assertNotNull(stale.endedAt);assertEquals("SYSTEM_RECOVERY",stale.endReason);verify(sessions).save(stale);}
  @Test void invalidLatitudeRejected(){var p=new Point(1,91,80,4d,null,null,Instant.now());assertEquals("INVALID_LOCATION",assertThrows(GatewayException.class,()->NavigatorService.validatePoint(p,Instant.now())).code);}
  @Test void nonFiniteCoordinateRejected(){var p=new Point(1,Double.NaN,80,4d,null,null,Instant.now());assertThrows(GatewayException.class,()->NavigatorService.validatePoint(p,Instant.now()));}
  @Test void negativeAccuracyRejected(){var p=new Point(1,26,80,-1d,null,null,Instant.now());assertThrows(GatewayException.class,()->NavigatorService.validatePoint(p,Instant.now()));}
  @Test void absurdTimestampRejected(){var p=new Point(1,26,80,4d,null,null,Instant.now().minus(Duration.ofDays(2)));assertThrows(GatewayException.class,()->NavigatorService.validatePoint(p,Instant.now()));}
  @Test void realtimeTicketIsSingleUse(){var value=new RealtimeTickets();var ticket=value.mint(UUID.randomUUID());assertNotNull(value.consume(ticket.ticket()));assertThrows(GatewayException.class,()->value.consume(ticket.ticket()));}
  @Test void rateLimitReturns429AndRetryAfter(){var rates=new RateLimits();for(int i=0;i<2;i++)rates.check("pair","ip",2,Duration.ofMinutes(1));var e=assertThrows(GatewayException.class,()->rates.check("pair","ip",2,Duration.ofMinutes(1)));assertEquals(HttpStatus.TOO_MANY_REQUESTS,e.status);assertTrue(e.retryAfter>0);}
  @Test void batchCannotExceed25(){var many=new ArrayList<Point>();for(int i=0;i<26;i++)many.add(point(i));try(var factory=Validation.buildDefaultValidatorFactory()){assertFalse(factory.getValidator().validate(new Batch(UUID.randomUUID(),many)).isEmpty());}}
  private PairingInvite invite(){var p=new PairingInvite();p.id=UUID.randomUUID();p.organizationId=UUID.randomUUID();p.employeeRef=UUID.randomUUID();p.expiresAt=Instant.now().plusSeconds(60);return p;}
  private Device device(){var d=new Device();d.id=UUID.randomUUID();d.organizationId=UUID.randomUUID();d.employeeRef=UUID.randomUUID();d.tokenHash=Hashing.sha256("token");return d;}
  private Point point(long n){return new Point(n,26.44,80.33,5d,null,null,Instant.now());}
}
