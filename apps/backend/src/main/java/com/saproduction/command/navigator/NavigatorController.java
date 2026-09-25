package com.saproduction.command.navigator;

import com.saproduction.command.audit.AuditService;
import com.saproduction.command.employee.*;
import com.saproduction.command.production.*;
import com.saproduction.command.shared.ApiEnvelope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/navigator")
@ConditionalOnProperty(prefix="app.navigator",name="enabled",havingValue="true")
public class NavigatorController {
  private final NavigatorGatewayClient gateway; private final EmployeeRepository employees; private final ProductionRepository productions; private final ProductionMemberRepository members; private final AuditService audit; private final NavigatorConfig config;
  NavigatorController(NavigatorGatewayClient gateway,EmployeeRepository employees,ProductionRepository productions,ProductionMemberRepository members,AuditService audit,NavigatorConfig config){this.gateway=gateway;this.employees=employees;this.productions=productions;this.members=members;this.audit=audit;this.config=config;}
  @GetMapping("/live") ApiEnvelope<Map<String,Object>> live(){var remote=gateway.live();var ids=remote.stream().map(x->UUID.fromString(x.get("employeeRef").toString())).toList();var people=new HashMap<UUID,EmployeeDtos.View>();employees.findAllById(ids).forEach(e->people.put(e.id,EmployeeDtos.view(e)));var items=remote.stream().map(x->{var row=new LinkedHashMap<String,Object>(x);var person=people.get(UUID.fromString(x.get("employeeRef").toString()));if(person!=null){row.put("employeeName",person.displayName());row.put("roleTitle",person.roleTitle());row.put("profilePhotoUrl",person.profilePhotoUrl());row.put("productionTitle","Sharma Wedding");}return row;}).toList();return ApiEnvelope.of(Map.of("items",items,"syncedAt",Instant.now(),"organizationPublicId",config.getOrganizationPublicId()));}
  @PostMapping("/employees/{employeeId}/pairing") ApiEnvelope<Map<String,Object>> pairing(@PathVariable UUID employeeId){syncProjection(employeeId);var result=gateway.pairing(employeeId);audit.record("NAVIGATOR_DEVICE","NAVIGATOR_DEVICE_PAIRED",employeeId.toString(),null,Map.of("pairingId",result.get("id"),"expiresAt",result.get("expiresAt")));return ApiEnvelope.of(result);}
  record MobileMessageInput(@NotBlank @Size(max=160) String title,@NotBlank @Size(max=1600) String body) {}
  @PostMapping("/employees/{employeeId}/mobile-message") ApiEnvelope<Map<String,Object>> message(@PathVariable UUID employeeId,@Valid @RequestBody MobileMessageInput input){employees.findById(employeeId).orElseThrow();syncProjection(employeeId);var sent=gateway.message(employeeId,input.title(),input.body());audit.record("NAVIGATOR_MESSAGE","NAVIGATOR_MOBILE_MESSAGE_SENT",employeeId.toString(),null,Map.of("messageId",sent.get("id")));return ApiEnvelope.of(sent);}
  @PostMapping("/devices/{deviceId}/revoke") ApiEnvelope<Map<String,Object>> revoke(@PathVariable UUID deviceId){gateway.revoke(deviceId);audit.record("NAVIGATOR_DEVICE","NAVIGATOR_DEVICE_REVOKED",deviceId.toString(),null,Map.of("revoked",true));return ApiEnvelope.of(Map.of("revoked",true));}
  @GetMapping("/employees/{employeeId}/history") ApiEnvelope<List<Map<String,Object>>> history(@PathVariable UUID employeeId,@RequestParam Instant from,@RequestParam Instant to){employees.findById(employeeId).orElseThrow();var value=gateway.history(employeeId,from,to);audit.record("NAVIGATOR_HISTORY","NAVIGATOR_HISTORY_VIEWED",employeeId.toString(),null,Map.of("from",from,"to",to,"points",value.size()));return ApiEnvelope.of(value);}
  @PostMapping("/realtime-ticket") ApiEnvelope<Map<String,Object>> ticket(){return ApiEnvelope.of(gateway.ticket());}
  @PostMapping("/simulator/{action}") ApiEnvelope<Map<String,Object>> simulator(@PathVariable String action){if(!config.isSimulatorEnabled()||!(action.equals("start")||action.equals("stop")))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);var demoOrder=List.of("amaan","rehan","farhan","sarah");var refs=employees.findAllByStatusNotOrderByDisplayName(Employee.Status.INACTIVE).stream().sorted(Comparator.comparingInt(e->{var index=demoOrder.indexOf(EmployeeDtos.view(e).firstName().toLowerCase(Locale.ROOT));return index<0?Integer.MAX_VALUE:index;})).limit(4).map(e->EmployeeDtos.view(e).id()).toList();return ApiEnvelope.of(gateway.simulator(action,refs));}
  private void syncProjection(UUID employeeId){var person=EmployeeDtos.view(employees.findById(employeeId).orElseThrow());var today=LocalDate.now(ZoneId.of("Asia/Kolkata"));var assigned=productions.findAll().stream().filter(p->p.eventDate.equals(today)&&p.status!=Production.Status.CANCELLED&&members.existsByProductionIdAndEmployeeId(p.id,employeeId)).findFirst().orElse(null);var body=new LinkedHashMap<String,Object>();body.put("employeeRef",employeeId);body.put("displayName",person.displayName());body.put("productionRef",assigned==null?null:assigned.id);body.put("productionTitle",assigned==null?null:assigned.title);body.put("locationName",assigned==null?null:assigned.venueName);body.put("startsAt",assigned==null?null:ZonedDateTime.of(assigned.eventDate,assigned.startTime,ZoneId.of("Asia/Kolkata")).toInstant());body.put("endsAt",assigned==null?null:ZonedDateTime.of(assigned.eventDate,assigned.endTime,ZoneId.of("Asia/Kolkata")).toInstant());gateway.projection(body);}
}
