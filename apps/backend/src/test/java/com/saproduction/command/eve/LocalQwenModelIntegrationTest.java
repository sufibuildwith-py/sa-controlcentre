package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saproduction.command.shared.ApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalQwenModelIntegrationTest {

  private LocalQwenModelProvider provider;

  @BeforeEach
  void setUp() {
    // Instantiate provider directly with mock/offline coordinates for isolated testing
    provider = new LocalQwenModelProvider(
        "eve/models/Qwen3-4B-Q4_K_M.gguf",
        "eve/runtime/llama-server/llama-server.exe",
        "http://127.0.0.1:8089",
        8089,
        4,
        2048,
        0);
  }

  @Test
  @DisplayName("Provider Status: Reports correct initial status and diagnostics")
  void testProviderStatus() {
    EveDtos.EveStatusView status = provider.getStatus();
    assertThat(status).isNotNull();
    assertThat(status.modelName()).isEqualTo("Qwen3-4B-Q4_K_M.gguf");
    assertThat(status.status()).isIn("INITIALIZING", "UNAVAILABLE", "READY");
  }

  @Test
  @DisplayName("Robustness: Gracefully rejects null or blank prompts")
  void testBlankPromptHandling() {
    EveModelProvider.EveInterpretation result = provider.interpret(
        new EveModelProvider.EveInterpretationRequest("", ""));
    assertThat(result).isNotNull();
    assertThat(result.intent()).isEqualTo(EveModelProvider.Intent.UNKNOWN);
  }

  @Test
  @DisplayName("Failure Safety: Throws ApiException with clear code when server is unreachable")
  void testUnreachableServerHandling() {
    // When the server is not ready or unreachable, interpret must throw EVE_MODEL_UNAVAILABLE
    assertThatThrownBy(() -> provider.interpret(
        new EveModelProvider.EveInterpretationRequest("details on Cultural Event MIPS", null)))
        .isInstanceOf(ApiException.class)
        .satisfies(ex -> {
          ApiException apiEx = (ApiException) ex;
          assertThat(apiEx.code).isIn("EVE_MODEL_UNAVAILABLE", "EVE_MODEL_TIMEOUT");
        });
  }

  @Test
  @DisplayName("Fallback Safety: composeResponse safely falls back to deterministic answer if server not ready")
  void testComposeResponseFallbackWhenOffline() {
    EveResponseComposer.EveResponseCompositionRequest req = new EveResponseComposer.EveResponseCompositionRequest(
        "how much do we owe him?",
        "Active Employee: Raj Sharma",
        EveModelProvider.Intent.READ_EMPLOYEE_FINANCE,
        "Total outstanding obligation for Raj Sharma is Rs 12,000.",
        List.of(),
        List.of(new EveDtos.EvidenceItem("FINANCE", "Outstanding Balance", "Rs 12,000")),
        List.of());

    String response = provider.composeResponse(req);
    // When offline, must return deterministicAnswer without throwing or crashing
    assertThat(response).isEqualTo("Total outstanding obligation for Raj Sharma is Rs 12,000.");
  }

  @Test
  @DisplayName("Null Safety: composeResponse handles null request gracefully")
  void testComposeResponseNullHandling() {
    String response = provider.composeResponse(null);
    assertThat(response).isNull();
  }

  @Test
  @DisplayName("Anti-Hallucination: Verifies that questions requesting unrecorded facts return explicit unrecorded notification")
  void testUnrecordedFactsHandling() {
    TestModelProvider testProvider = new TestModelProvider();

    // 1. Phone number not in evidence
    EveResponseComposer.EveResponseCompositionRequest phoneReq = new EveResponseComposer.EveResponseCompositionRequest(
        "Sunil ka phone number kya hai?",
        null,
        EveModelProvider.Intent.READ_EMPLOYEE_FINANCE,
        "Total outstanding obligation for Sunil is Rs 3,000.",
        List.of(),
        List.of(new EveDtos.EvidenceItem("FINANCE", "Outstanding Balance", "Rs 3,000")),
        List.of());
    String phoneResp = testProvider.composeResponse(phoneReq);
    assertThat(phoneResp).contains("not recorded");

    // 2. Profitability not in evidence
    EveResponseComposer.EveResponseCompositionRequest profitReq = new EveResponseComposer.EveResponseCompositionRequest(
        "Royal wedding ka profit kitna hua?",
        null,
        EveModelProvider.Intent.READ_PRODUCTION,
        "Royal Wedding is scheduled on 2026-10-15.",
        List.of(),
        List.of(new EveDtos.EvidenceItem("PRODUCTION", "Title", "Royal Wedding")),
        List.of());
    String profitResp = testProvider.composeResponse(profitReq);
    assertThat(profitResp).contains("not recorded");

    // 3. Cancellation reason not in evidence
    EveResponseComposer.EveResponseCompositionRequest cancelReq = new EveResponseComposer.EveResponseCompositionRequest(
        "Why was the event cancelled?",
        null,
        EveModelProvider.Intent.READ_PRODUCTION,
        "Royal Wedding status is CANCELLED.",
        List.of(),
        List.of(new EveDtos.EvidenceItem("PRODUCTION", "Status", "CANCELLED")),
        List.of());
    String cancelResp = testProvider.composeResponse(cancelReq);
    assertThat(cancelResp).contains("not recorded");

    // 4. Client quote/pricing not in evidence
    EveResponseComposer.EveResponseCompositionRequest quoteReq = new EveResponseComposer.EveResponseCompositionRequest(
        "What was the client quote?",
        null,
        EveModelProvider.Intent.READ_PRODUCTION,
        "Royal Wedding for client Sharma.",
        List.of(),
        List.of(new EveDtos.EvidenceItem("PRODUCTION", "Client", "Sharma")),
        List.of());
    String quoteResp = testProvider.composeResponse(quoteReq);
    assertThat(quoteResp).contains("not recorded");
  }
}
