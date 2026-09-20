package com.saproduction.command.communication;

import com.fasterxml.jackson.databind.*;
import com.saproduction.command.shared.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integrations/whatsapp/webhook")
public class WhatsAppWebhookController {
  private final InboundMessagingService inbound;
  private final ObjectMapper json;
  private final String verifyToken, appSecret, mode;

  public WhatsAppWebhookController(
      InboundMessagingService inbound,
      ObjectMapper json,
      @Value("${app.messaging.meta.verify-token:}") String verifyToken,
      @Value("${app.messaging.meta.app-secret:}") String appSecret,
      @Value("${app.mode:demo}") String mode) {
    this.inbound = inbound;
    this.json = json;
    this.verifyToken = verifyToken;
    this.appSecret = appSecret;
    this.mode = mode;
  }

  @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> verify(
      @RequestParam(name = "hub.mode", required = false) String hubMode,
      @RequestParam(name = "hub.verify_token", required = false) String token,
      @RequestParam(name = "hub.challenge", required = false) String challenge) {
    if ("subscribe".equals(hubMode)
        && !verifyToken.isBlank()
        && MessageDigest.isEqual(
            verifyToken.getBytes(StandardCharsets.UTF_8),
            Objects.toString(token, "").getBytes(StandardCharsets.UTF_8)))
      return ResponseEntity.ok(challenge);
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
  }

  @PostMapping
  public ResponseEntity<Void> receive(
      @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
      @RequestBody String body) {
    if (!validSignature(signature, body))
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "INVALID_WEBHOOK_SIGNATURE", "Webhook signature is invalid.");
    try {
      JsonNode root = json.readTree(body);
      if (!"whatsapp_business_account".equals(root.path("object").asText()))
        throw ApiException.badRequest("INVALID_WEBHOOK_PAYLOAD", "Webhook object is invalid.");
      for (JsonNode entry : root.path("entry"))
        for (JsonNode change : entry.path("changes")) {
          JsonNode value = change.path("value");
          for (JsonNode status : value.path("statuses")) {
            String id = status.path("id").asText(),
                state = status.path("status").asText(),
                timestamp = status.path("timestamp").asText();
            String error = status.path("errors").path(0).path("title").asText();
            if (!id.isBlank() && !state.isBlank())
              inbound.providerStatus(
                  "status:" + id + ":" + state + ":" + timestamp, id, state, error);
          }
          for (JsonNode message : value.path("messages")) {
            String eventId = message.path("id").asText();
            String contextId = message.path("context").path("id").asText();
            String action = message.path("interactive").path("button_reply").path("id").asText();
            if (action.isBlank()) action = message.path("button").path("payload").asText();
            if (!eventId.isBlank() && !contextId.isBlank() && !action.isBlank())
              inbound.providerResponse("interaction:" + eventId, contextId, terminalAction(action));
          }
        }
      return ResponseEntity.ok().build();
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      throw ApiException.badRequest("MALFORMED_WEBHOOK", "Webhook payload could not be processed.");
    }
  }

  private boolean validSignature(String supplied, String body) {
    if (appSecret.isBlank()) return !"production".equalsIgnoreCase(mode);
    if (supplied == null || !supplied.startsWith("sha256=")) return false;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      String expected =
          "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
      return MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.US_ASCII),
          supplied.getBytes(StandardCharsets.US_ASCII));
    } catch (GeneralSecurityException e) {
      return false;
    }
  }

  private static String terminalAction(String value) {
    int index = value.lastIndexOf(':');
    return index < 0 ? value : value.substring(index + 1);
  }
}
