package com.saproduction.command.communication;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DomainEventService {
  private final OutboxEventRepository events;private final ObjectMapper json;
  public DomainEventService(OutboxEventRepository events,ObjectMapper json){this.events=events;this.json=json;}
  public UUID emit(String eventType,String aggregateType,UUID aggregateId,Map<String,?> payload){OutboxEvent event=new OutboxEvent();event.eventType=eventType;event.aggregateType=aggregateType;event.aggregateId=aggregateId;try{event.payloadJson=json.writeValueAsString(payload);}catch(Exception e){throw new IllegalArgumentException("Domain event payload could not be serialized.",e);}events.save(event);return event.id;}
  public UUID emitAt(String eventType,String aggregateType,UUID aggregateId,Map<String,?> payload,Instant availableAt){OutboxEvent event=new OutboxEvent();event.eventType=eventType;event.aggregateType=aggregateType;event.aggregateId=aggregateId;event.availableAt=availableAt;try{event.payloadJson=json.writeValueAsString(payload);}catch(Exception e){throw new IllegalArgumentException("Domain event payload could not be serialized.",e);}events.save(event);return event.id;}
}
