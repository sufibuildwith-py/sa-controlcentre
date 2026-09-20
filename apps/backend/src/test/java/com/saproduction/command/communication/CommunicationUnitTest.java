package com.saproduction.command.communication;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.shared.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
class CommunicationUnitTest {
  @Test void consoleProviderReturnsStableProviderId(){UUID id=UUID.randomUUID();var result=new ConsoleMessagingProvider().send(new MessagingProvider.MessageCommand(id,"+919000000000","notice",Map.of(),"Hello",null,null));assertThat(result.providerMessageId()).isEqualTo("console-"+id);}
  @Test void metaProviderRejectsMissingBackendCredentialsWithoutNetworkCall(){var provider=new MetaWhatsAppProvider(new ObjectMapper(),"","","v23.0","en_US","https://graph.facebook.com");assertThatThrownBy(()->provider.send(new MessagingProvider.MessageCommand(UUID.randomUUID(),"+919000000000","notice",Map.of(),"Hello",null,null))).isInstanceOfSatisfying(MessagingProvider.ProviderException.class,e->{assertThat(e.transientFailure()).isFalse();assertThat(e.getMessage()).contains("not configured");});}
  @Test void metaProviderSendsCurrentTemplateContractAndParsesWamid() throws Exception {var authorization=new AtomicReference<String>();var requestBody=new AtomicReference<String>();HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/v23.0/phone-id/messages",exchange->{authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));requestBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));byte[] response="{\"messages\":[{\"id\":\"wamid.contract-1\"}]}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();});server.start();try{var provider=new MetaWhatsAppProvider(new ObjectMapper(),"secret-token","phone-id","v23.0","en_US","http://127.0.0.1:"+server.getAddress().getPort());var result=provider.send(new MessagingProvider.MessageCommand(UUID.randomUUID(),"+91 90000-00000","sa_production_assignment",Map.of("parameters",List.of("Amaan","Sharma Wedding"),"confirmPayload","production:confirm","declinePayload","production:decline"),"Assignment","PRODUCTION",UUID.randomUUID()));assertThat(result.providerMessageId()).isEqualTo("wamid.contract-1");assertThat(authorization.get()).isEqualTo("Bearer secret-token");assertThat(requestBody.get()).contains("\"to\":\"919000000000\"","\"type\":\"template\"","sa_production_assignment","production:confirm","production:decline");}finally{server.stop(0);}}
  @Test void webhookChallengeRequiresExactToken(){var controller=new WhatsAppWebhookController(mock(InboundMessagingService.class),new ObjectMapper(),"verify-me","secret","production");assertThat(controller.verify("subscribe","verify-me","challenge").getBody()).isEqualTo("challenge");assertThat(controller.verify("subscribe","wrong","challenge").getStatusCode().value()).isEqualTo(403);}
  @Test void productionWebhookRejectsMissingSignature(){var controller=new WhatsAppWebhookController(mock(InboundMessagingService.class),new ObjectMapper(),"verify-me","secret","production");assertThatThrownBy(()->controller.receive(null,"{}" )).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code).isEqualTo("INVALID_WEBHOOK_SIGNATURE"));}
}
