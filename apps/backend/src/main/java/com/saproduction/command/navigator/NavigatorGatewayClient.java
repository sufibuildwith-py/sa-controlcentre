package com.saproduction.command.navigator;

import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix="app.navigator",name="enabled",havingValue="true")
class NavigatorGatewayClient {
  private final RestClient client;
  NavigatorGatewayClient(RestClient.Builder builder,NavigatorConfig config){this.client=builder.baseUrl(config.getGatewayUrl()).defaultHeader("X-Navigator-Service-Key",config.getServiceKey()).build();}
  List<Map<String,Object>> live(){return client.get().uri("/api/v1/admin/locations/live").retrieve().body(new ParameterizedTypeReference<>(){});}
  Map<String,Object> pairing(UUID employee){return client.post().uri("/api/v1/admin/pairings").contentType(MediaType.APPLICATION_JSON).body(Map.of("employeeRef",employee)).retrieve().body(new ParameterizedTypeReference<>(){});}
  void revoke(UUID device){client.post().uri("/api/v1/admin/devices/{id}/revoke",device).retrieve().toBodilessEntity();}
  List<Map<String,Object>> history(UUID employee,Instant from,Instant to){return client.get().uri(b->b.path("/api/v1/admin/employees/{id}/locations").queryParam("from",from).queryParam("to",to).build(employee)).retrieve().body(new ParameterizedTypeReference<>(){});}
  Map<String,Object> ticket(){return client.post().uri("/api/v1/admin/realtime-ticket").retrieve().body(new ParameterizedTypeReference<>(){});}
  void projection(Map<String,Object> body){client.put().uri("/api/v1/admin/mobile-projections").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();}
  Map<String,Object> message(UUID employee,String title,String body){return client.post().uri("/api/v1/admin/mobile-messages").contentType(MediaType.APPLICATION_JSON).body(Map.of("employeeRef",employee,"title",title,"body",body)).retrieve().body(new ParameterizedTypeReference<>(){});}
  Map<String,Object> simulator(String action,List<UUID> employees){if("start".equals(action))return client.post().uri("/api/v1/admin/simulator/start").contentType(MediaType.APPLICATION_JSON).body(Map.of("employeeRefs",employees)).retrieve().body(new ParameterizedTypeReference<>(){});return client.post().uri("/api/v1/admin/simulator/stop").retrieve().body(new ParameterizedTypeReference<>(){});}
}
