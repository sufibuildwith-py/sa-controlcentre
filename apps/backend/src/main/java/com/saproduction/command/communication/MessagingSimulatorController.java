package com.saproduction.command.communication;
import com.saproduction.command.shared.ApiEnvelope;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/demo") @ConditionalOnProperty(name="app.mode",havingValue="demo",matchIfMissing=true)
public class MessagingSimulatorController {
  private final CommunicationService messages;private final InboundMessagingService inbound;
  public MessagingSimulatorController(CommunicationService messages,InboundMessagingService inbound){this.messages=messages;this.inbound=inbound;}
  @GetMapping("/messages") public ApiEnvelope<CommunicationService.Centre> list(){return ApiEnvelope.of(messages.list(null,null,null,null));}
  @PostMapping("/messages/{id}/{status}") public ApiEnvelope<CommunicationService.MessageView> status(@PathVariable UUID id,@PathVariable String status){inbound.simulateStatus(id,status,"FAILED".equalsIgnoreCase(status)?"Deterministic simulator failure":null);return ApiEnvelope.of(messages.get(id));}
  @PostMapping("/messages/{id}/respond/{action}") public ApiEnvelope<CommunicationService.MessageView> response(@PathVariable UUID id,@PathVariable String action){inbound.simulateResponse(id,action);return ApiEnvelope.of(messages.get(id));}
}
