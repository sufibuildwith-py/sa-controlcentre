package com.saproduction.command.audit;
import com.saproduction.command.shared.ApiEnvelope;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/audit")
public class AuditController {
  public record View(UUID id,String actorId,String entityType,String entityId,String action,String beforeJson,String afterJson,Instant createdAt){}
  private final AuditRepository repository;public AuditController(AuditRepository repository){this.repository=repository;}
  @GetMapping public ApiEnvelope<List<View>> list(@RequestParam String entityType,@RequestParam String entityId){return ApiEnvelope.of(repository.findAllByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType,entityId).stream().map(x->new View(x.id,x.actorId,x.entityType,x.entityId,x.action,x.beforeJson,x.afterJson,x.createdAt)).toList());}
}
