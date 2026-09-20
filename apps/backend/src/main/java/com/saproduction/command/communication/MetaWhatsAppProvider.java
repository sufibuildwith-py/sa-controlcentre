package com.saproduction.command.communication;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.provider", havingValue = "meta")
public class MetaWhatsAppProvider implements MessagingProvider {
  private final ObjectMapper json;
  private final HttpClient http;
  private final String token, phoneNumberId, version, language, baseUrl;

  public MetaWhatsAppProvider(
      ObjectMapper json,
      @Value("${app.messaging.meta.access-token:}") String token,
      @Value("${app.messaging.meta.phone-number-id:}") String phoneNumberId,
      @Value("${app.messaging.meta.graph-api-version:v23.0}") String version,
      @Value("${app.messaging.meta.template-language:en_US}") String language) {
    this(json, token, phoneNumberId, version, language, "https://graph.facebook.com");
  }

  MetaWhatsAppProvider(
      ObjectMapper json,
      String token,
      String phoneNumberId,
      String version,
      String language,
      String baseUrl) {
    this.json = json;
    this.token = token;
    this.phoneNumberId = phoneNumberId;
    this.version = version;
    this.language = language;
    this.baseUrl = baseUrl.replaceAll("/+$", "");
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
  }

  public SendResult send(MessageCommand command) {
    if (token.isBlank() || phoneNumberId.isBlank())
      throw new ProviderException("Meta WhatsApp credentials are not configured.", false);
    try {
      List<Map<String, Object>> components = new ArrayList<>();
      Object raw = command.variables().get("parameters");
      if (raw instanceof List<?> values && !values.isEmpty())
        components.add(
            Map.of(
                "type",
                "body",
                "parameters",
                values.stream()
                    .map(v -> Map.of("type", "text", "text", String.valueOf(v)))
                    .toList()));
      List<Map<String, Object>> buttons = new ArrayList<>();
      if (command.variables().get("confirmPayload") != null)
        buttons.add(button(0, command.variables().get("confirmPayload")));
      if (command.variables().get("declinePayload") != null)
        buttons.add(button(1, command.variables().get("declinePayload")));
      components.addAll(buttons);
      Map<String, Object> template = new LinkedHashMap<>();
      template.put("name", command.templateKey());
      template.put("language", Map.of("code", language));
      if (!components.isEmpty()) template.put("components", components);
      String payload =
          json.writeValueAsString(
              Map.of(
                  "messaging_product",
                  "whatsapp",
                  "recipient_type",
                  "individual",
                  "to",
                  digits(command.to()),
                  "type",
                  "template",
                  "template",
                  template));
      HttpRequest request =
          HttpRequest.newBuilder(
                  URI.create(baseUrl + "/" + version + "/" + phoneNumberId + "/messages"))
              .timeout(Duration.ofSeconds(20))
              .header("Authorization", "Bearer " + token)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(payload))
              .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new ProviderException(
            "Meta rejected the message (HTTP " + response.statusCode() + ").",
            response.statusCode() == 429 || response.statusCode() >= 500);
      JsonNode body = json.readTree(response.body());
      String id = body.path("messages").path(0).path("id").asText();
      if (id.isBlank())
        throw new ProviderException("Meta response did not include a message ID.", false);
      return new SendResult(id);
    } catch (ProviderException e) {
      throw e;
    } catch (java.net.http.HttpTimeoutException e) {
      throw new ProviderException("Meta request timed out.", true);
    } catch (java.io.IOException e) {
      throw new ProviderException("Meta transport is unavailable.", true);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProviderException("Meta send was interrupted.", true);
    } catch (Exception e) {
      throw new ProviderException("Meta response could not be processed.", false);
    }
  }

  private static Map<String, Object> button(int index, Object payload) {
    return Map.of(
        "type",
        "button",
        "sub_type",
        "quick_reply",
        "index",
        String.valueOf(index),
        "parameters",
        List.of(Map.of("type", "payload", "payload", String.valueOf(payload))));
  }

  private static String digits(String value) {
    return value == null ? "" : value.replaceAll("\\D", "");
  }
}
