package com.saproduction.command.communication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.audit.AuditService;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.shared.ApiException;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunicationService {
  public record ManualInput(@NotEmpty List<UUID> employeeIds,@NotBlank @Size(max=1600) String message){ }
  public record RuleInput(boolean enabled,@PositiveOrZero int delayMinutes){ }
  public record RuleView(UUID id,String eventType,String channel,boolean enabled,int delayMinutes,String templateKey){ }
  public record EventView(String eventType,String detail,Instant createdAt){ }
  public record MessageView(UUID id,UUID employeeId,String employeeName,String phone,String category,String templateKey,String bodyPreview,String relatedType,UUID relatedId,boolean requiresResponse,String response,OutboundMessage.Status status,String providerMessageId,int attemptCount,String lastError,Instant queuedAt,Instant sentAt,Instant deliveredAt,Instant readAt,Instant failedAt,List<EventView> activity){ }
  public record Summary(long delivered,long read,long awaitingResponse,long failed,long queued,long sentToday){ }
  public record Centre(Summary summary,List<MessageView> messages){ }
  private final OutboundMessageRepository messages;private final OutboundMessageEventRepository history;private final NotificationRuleRepository rules;private final EmployeeRepository employees;private final DomainEventService events;private final AuditService audit;
  public CommunicationService(OutboundMessageRepository messages,OutboundMessageEventRepository history,NotificationRuleRepository rules,EmployeeRepository employees,DomainEventService events,AuditService audit){this.messages=messages;this.history=history;this.rules=rules;this.employees=employees;this.events=events;this.audit=audit;}
  @Transactional(readOnly=true) public Centre list(String category,OutboundMessage.Status status,Boolean needsAttention,UUID employeeId){var values=messages.findAll((root,q,cb)->{List<Predicate> p=new ArrayList<>();if(category!=null&&!category.isBlank())p.add(cb.equal(root.get("category"),category));if(status!=null)p.add(cb.equal(root.get("status"),status));if(employeeId!=null)p.add(cb.equal(root.get("employeeId"),employeeId));if(Boolean.TRUE.equals(needsAttention))p.add(cb.or(cb.equal(root.get("status"),OutboundMessage.Status.FAILED),cb.and(cb.isTrue(root.get("requiresResponse")),cb.isNull(root.get("response")))));return cb.and(p.toArray(Predicate[]::new));},Sort.by(Sort.Direction.DESC,"queuedAt")).stream().map(m->view(m,false)).toList();List<OutboundMessage> all=messages.findAll();long delivered=all.stream().filter(m->m.status==OutboundMessage.Status.DELIVERED).count(),read=all.stream().filter(m->m.status==OutboundMessage.Status.READ).count(),waiting=all.stream().filter(m->m.requiresResponse&&m.response==null).count(),failed=all.stream().filter(m->m.status==OutboundMessage.Status.FAILED).count(),queued=all.stream().filter(m->m.status==OutboundMessage.Status.QUEUED||m.status==OutboundMessage.Status.SENDING).count(),today=all.stream().filter(m->m.sentAt!=null&&m.sentAt.isAfter(Instant.now().minusSeconds(86400))).count();return new Centre(new Summary(delivered,read,waiting,failed,queued,today),values);}
  @Transactional(readOnly=true) public MessageView get(UUID id){return view(entity(id),true);}
  @Transactional public Map<String,Object> manual(ManualInput input){LinkedHashSet<UUID> ids=new LinkedHashSet<>(input.employeeIds());if(ids.isEmpty())throw ApiException.badRequest("MESSAGE_RECIPIENTS_REQUIRED","Choose at least one employee.");for(UUID id:ids)employees.findById(id).orElseThrow(()->ApiException.notFound("EMPLOYEE_NOT_FOUND","Employee was not found."));Map<String,Object> payload=new LinkedHashMap<>();payload.put("employeeIds",ids);payload.put("category","MANUAL");payload.put("bodyPreview",input.message().trim());payload.put("relatedType","MANUAL");payload.put("variables",Map.of("parameters",List.of(input.message().trim())));UUID eventId=events.emit("MANUAL_NOTICE","MANUAL_MESSAGE",null,payload);audit.record("COMMUNICATION","MANUAL_MESSAGE_QUEUED",eventId.toString(),null,Map.of("recipientCount",ids.size()));return Map.of("eventId",eventId,"recipientCount",ids.size());}
  @Transactional public MessageView retry(UUID id){OutboundMessage message=entity(id);if(message.status!=OutboundMessage.Status.FAILED)throw ApiException.conflict("MESSAGE_NOT_FAILED","Only failed messages can be retried.");var before=view(message,false);message.status=OutboundMessage.Status.QUEUED;message.nextAttemptAt=Instant.now();message.lastError=null;message.failedAt=null;messages.save(message);record(message,"MANUAL_RETRY","Owner requested retry");var after=view(message,true);audit.record("COMMUNICATION","MESSAGE_RETRIED",id.toString(),before,after);return after;}
  @Transactional(readOnly=true) public List<RuleView> rules(){return rules.findAllByOrderByEventType().stream().map(this::rule).toList();}
  @Transactional public RuleView updateRule(UUID id,RuleInput input){NotificationRule value=rules.findById(id).orElseThrow(()->ApiException.notFound("NOTIFICATION_RULE_NOT_FOUND","Notification rule was not found."));RuleView before=rule(value);value.enabled=input.enabled();value.delayMinutes=input.delayMinutes();rules.save(value);RuleView after=rule(value);audit.record("NOTIFICATION_RULE","NOTIFICATION_RULE_UPDATED",id.toString(),before,after);return after;}
  private MessageView view(OutboundMessage m,boolean includeHistory){var employee=employees.findById(m.employeeId).orElse(null);var activity=includeHistory?history.findAllByOutboundMessageIdOrderByCreatedAt(m.id).stream().map(e->new EventView(e.eventType,e.detail,e.createdAt)).toList():List.<EventView>of();return new MessageView(m.id,m.employeeId,employee==null?"Former employee":employee.displayName,employee==null?"":employee.whatsappPhone,m.category,m.templateKey,m.bodyPreview,m.relatedType,m.relatedId,m.requiresResponse,m.response,m.status,m.providerMessageId,m.attemptCount,m.lastError,m.queuedAt,m.sentAt,m.deliveredAt,m.readAt,m.failedAt,activity);}
  private RuleView rule(NotificationRule r){return new RuleView(r.id,r.eventType,r.channel,r.enabled,r.delayMinutes,r.templateKey);}
  private OutboundMessage entity(UUID id){return messages.findById(id).orElseThrow(()->ApiException.notFound("MESSAGE_NOT_FOUND","Message was not found."));}
  private void record(OutboundMessage message,String type,String detail){OutboundMessageEvent e=new OutboundMessageEvent();e.outboundMessageId=message.id;e.eventType=type;e.detail=detail;history.save(e);}
}
