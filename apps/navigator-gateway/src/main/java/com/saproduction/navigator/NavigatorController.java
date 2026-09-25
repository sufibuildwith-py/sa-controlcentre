package com.saproduction.navigator;

import static com.saproduction.navigator.NavigatorDtos.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class NavigatorController {
  private final NavigatorService service; private final GatewayAuth auth; private final RateLimits rates; private final RealtimeTickets tickets;
  NavigatorController(NavigatorService service,GatewayAuth auth,RateLimits rates,RealtimeTickets tickets){this.service=service;this.auth=auth;this.rates=rates;this.tickets=tickets;}
  private UUID admin(String key){auth.requireAdmin(key);return service.organization().id;}
  @PostMapping("/admin/pairings") PairingView pairing(@RequestHeader("X-Navigator-Service-Key") String key,@Valid @RequestBody CreatePairing body){admin(key);return service.createPairing(body.employeeRef(),body.createdByRef());}
  @DeleteMapping("/admin/pairings/{id}") ResponseEntity<Void> cancel(@RequestHeader("X-Navigator-Service-Key") String key,@PathVariable UUID id){admin(key);service.cancelPairing(id);return ResponseEntity.noContent().build();}
  @PostMapping("/mobile/pairings/redeem") Paired redeem(@Valid @RequestBody Redeem body,HttpServletRequest request){rates.check("pair",request.getRemoteAddr(),5,Duration.ofMinutes(10));rates.check("code",Hashing.sha256(body.code()),5,Duration.ofMinutes(10));return service.redeem(body);}
  @PostMapping({"/mobile/sessions","/mobile/duty/start"}) SessionView start(@RequestHeader("Authorization") String bearer,@Valid @RequestBody StartSession body){var d=auth.requireDevice(bearer);rates.check("session",d.id.toString(),10,Duration.ofMinutes(1));return service.startDuty(d,body);}
  @PostMapping("/mobile/sessions/{id}/stop") ResponseEntity<Void> stop(@RequestHeader("Authorization") String bearer,@PathVariable UUID id){var d=auth.requireDevice(bearer);rates.check("session",d.id.toString(),10,Duration.ofMinutes(1));service.stop(d,id,"EMPLOYEE_STOPPED");return ResponseEntity.noContent().build();}
  @PostMapping("/mobile/duty/end") ResponseEntity<Void> endDuty(@RequestHeader("Authorization") String bearer,@Valid @RequestBody EndDuty body){var d=auth.requireDevice(bearer);rates.check("session",d.id.toString(),10,Duration.ofMinutes(1));service.endDuty(d,body.sessionId());return ResponseEntity.noContent().build();}
  @PostMapping("/mobile/locations/batch") Acknowledged batch(@RequestHeader("Authorization") String bearer,@Valid @RequestBody Batch body){var d=auth.requireDevice(bearer);rates.check("batch",d.id.toString(),16,Duration.ofMinutes(1));return service.ingest(d,body);}
  @GetMapping("/mobile/state") Map<String,Object> state(@RequestHeader("Authorization") String bearer){var d=auth.requireDevice(bearer);return Map.of("deviceId",d.id,"employeeRef",d.employeeRef,"revoked",false);}
  @GetMapping("/mobile/home") MobileHome home(@RequestHeader("Authorization") String bearer){return service.home(auth.requireDevice(bearer));}
  @GetMapping("/admin/locations/live") List<Live> live(@RequestHeader("X-Navigator-Service-Key") String key){return service.live(admin(key));}
  @GetMapping("/admin/employees/{employee}/locations") List<Map<String,Object>> history(@RequestHeader("X-Navigator-Service-Key") String key,@PathVariable UUID employee,@RequestParam Instant from,@RequestParam Instant to){var org=admin(key);rates.check("history",org.toString(),30,Duration.ofMinutes(1));return service.history(org,employee,from,to);}
  @PostMapping("/admin/devices/{id}/revoke") ResponseEntity<Void> revoke(@RequestHeader("X-Navigator-Service-Key") String key,@PathVariable UUID id){service.revoke(admin(key),id);return ResponseEntity.noContent().build();}
  @PostMapping("/admin/realtime-ticket") Ticket ticket(@RequestHeader("X-Navigator-Service-Key") String key){admin(key);return tickets.mint(auth.publicId());}
  @PutMapping("/admin/mobile-projections") ResponseEntity<Void> projection(@RequestHeader("X-Navigator-Service-Key") String key,@Valid @RequestBody Projection body){service.upsertProjection(admin(key),body);return ResponseEntity.noContent().build();}
  @PostMapping("/admin/mobile-messages") MobileMessage message(@RequestHeader("X-Navigator-Service-Key") String key,@Valid @RequestBody MobileMessageInput body){return service.sendMobileMessage(admin(key),body);}
}

@RestControllerAdvice
class GatewayErrors {
  @ExceptionHandler(GatewayException.class) ResponseEntity<Map<String,Object>> gateway(GatewayException e){var body=Map.<String,Object>of("error",Map.of("code",e.code,"message",e.getMessage()));var result=ResponseEntity.status(e.status);if(e.retryAfter>0)result.header("Retry-After",Long.toString(e.retryAfter));return result.body(body);}
  @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class) ResponseEntity<Map<String,Object>> invalid(){return ResponseEntity.badRequest().body(Map.of("error",Map.of("code","VALIDATION_FAILED","message","Request validation failed.")));}
}
