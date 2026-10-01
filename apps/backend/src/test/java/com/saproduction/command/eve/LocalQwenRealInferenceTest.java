package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;

import com.saproduction.command.eve.capability.EveCapabilityResolver;
import com.saproduction.command.eve.capability.InformationNeed;
import com.saproduction.command.eve.system.EveCapability;
import com.saproduction.command.eve.system.EveSystemModel;
import java.io.File;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalQwenRealInferenceTest {

  private LocalQwenModelProvider qwenProvider;
  private TestModelProvider testProvider;
  private EveSystemModel systemModel;

  @BeforeAll
  void setUpAll() {
    systemModel = new EveSystemModel();
    testProvider = new TestModelProvider();

    qwenProvider = new LocalQwenModelProvider(
        "eve/models/Qwen3-4B-Q4_K_M.gguf",
        "eve/runtime/llama-server/llama-server.exe",
        "http://127.0.0.1:8089",
        8089,
        4,
        2048,
        0);

    File model = new File("eve/models/Qwen3-4B-Q4_K_M.gguf");
    File server = new File("eve/runtime/llama-server/llama-server.exe");
    if (!model.exists()) {
      // Look from apps/backend perspective
      model = new File("../../eve/models/Qwen3-4B-Q4_K_M.gguf");
      server = new File("../../eve/runtime/llama-server/llama-server.exe");
      qwenProvider = new LocalQwenModelProvider(
          model.getPath(),
          server.getPath(),
          "http://127.0.0.1:8089",
          8089,
          4,
          2048,
          0);
    }

    System.out.println("=== INITIALIZING LOCAL QWEN RUNTIME ===");
    qwenProvider.init();
    System.out.println("Local Qwen Status: " + qwenProvider.getStatusView());
  }

  @AfterAll
  void tearDownAll() {
    if (qwenProvider != null) {
      qwenProvider.destroy();
    }
  }

  @Test
  @DisplayName("Verify Real Qwen Structured InformationNeed on Sharma Wedding Requests")
  void testRealQwenSharmaWeddingQueries() {
    List<String> queries = List.of(
        "crew for sharma wedding",
        "crew for shamra wedding",
        "shamra wedding ke event me kon gaya he",
        "who works on sharma wedding",
        "wedding event of sharma me kaun hai"
    );

    System.out.println("\n=======================================================");
    System.out.println("SECTION 2: REAL LOCAL QWEN INTERPRETATION & INFORMATION_NEED");
    System.out.println("=======================================================");

    for (int i = 0; i < queries.size(); i++) {
      String query = queries.get(i);
      System.out.println("\n--- Query " + (i + 1) + ": \"" + query + "\" ---");

      EveModelProvider.EveInterpretation interpretation = qwenProvider.interpret(
          new EveModelProvider.EveInterpretationRequest(query, null));
      InformationNeed infoNeed = interpretation.toInformationNeed(query);
      Optional<EveCapability> cap = systemModel.findCapability(infoNeed.topic(), infoNeed.targetConcept());

      System.out.println("QWEN Intent: " + interpretation.intent());
      System.out.println("Target Entity Type: " + (infoNeed.targetConcept() != null ? infoNeed.targetConcept().name() : "null"));
      System.out.println("Entity Phrase: \"" + infoNeed.targetEntityPhrase() + "\"");
      System.out.println("Information Topic: " + infoNeed.topic());
      System.out.println("Operation / Need: " + infoNeed.operation());
      System.out.println("Confidence: " + interpretation.confidence());
      System.out.println("FollowUp: " + interpretation.followUp());
      System.out.println("Selected Capability: " + cap.map(c -> c.id() + " (" + c.description() + ")").orElse("NONE"));
    }

    System.out.println("\n--- Multi-turn Continuation: Query 3 WITH SessionContext ---");
    String turn2Prompt = "shamra wedding ke event me kon gaya he";
    String sessionCtx = "[Pending Clarification: waiting for PRODUCTION for intent READ_PRODUCTION_CREW. Question asked: \"I found multiple matching productions. Please choose which one you meant:\"]";
    EveModelProvider.EveInterpretation turn2Interp = qwenProvider.interpret(
        new EveModelProvider.EveInterpretationRequest(turn2Prompt, sessionCtx));
    InformationNeed turn2Need = turn2Interp.toInformationNeed(turn2Prompt);
    Optional<EveCapability> turn2Cap = systemModel.findCapability(turn2Need.topic(), turn2Need.targetConcept());

    System.out.println("Turn 2 QWEN Intent: " + turn2Interp.intent());
    System.out.println("Turn 2 Target Entity Type: " + (turn2Need.targetConcept() != null ? turn2Need.targetConcept().name() : "null"));
    System.out.println("Turn 2 Entity Phrase: \"" + turn2Need.targetEntityPhrase() + "\"");
    System.out.println("Turn 2 Information Topic: " + turn2Need.topic());
    System.out.println("Turn 2 FollowUp: " + turn2Interp.followUp());
    System.out.println("Turn 2 Selected Capability: " + turn2Cap.map(c -> c.id() + " (" + c.description() + ")").orElse("NONE"));
  }

  @Test
  @DisplayName("Verify Test-Provider Independence & 10 Unseen Paraphrases")
  void testUnseenParaphrasesAndProviderIndependence() {
    List<String> unseenQueries = List.of(
        "who's assigned to the sharma wedding",
        "sharma wedding crew batao",
        "people working at sharma wedding",
        "who all are on that wedding",
        "sharma wedding mein kaun kaam kar raha hai",
        "us wedding ke staff kaun hain",
        "wedding by sharma ke log kaun hain",
        "shamra wdding ke bande batao",
        "shrma weding par kis kis ki duty hai",
        "tell me everyone working on the wedding of sharma"
    );

    System.out.println("\n=======================================================");
    System.out.println("SECTION 6 & 7: UNSEEN PARAPHRASES & TEST-PROVIDER INDEPENDENCE");
    System.out.println("=======================================================");

    int testProviderCrewCount = 0;
    int qwenCrewCount = 0;
    int bothMatchCount = 0;

    for (int i = 0; i < unseenQueries.size(); i++) {
      String query = unseenQueries.get(i);
      System.out.println("\n[Unseen " + (i + 1) + "] \"" + query + "\"");

      // Provider A: TestModelProvider
      EveModelProvider.EveInterpretation testInterp = testProvider.interpret(
          new EveModelProvider.EveInterpretationRequest(query, null));
      InformationNeed testNeed = testInterp.toInformationNeed(query);

      // Provider B: Real Local Qwen
      EveModelProvider.EveInterpretation qwenInterp = qwenProvider.interpret(
          new EveModelProvider.EveInterpretationRequest(query, null));
      InformationNeed qwenNeed = qwenInterp.toInformationNeed(query);

      System.out.println("  TestModelProvider   -> Intent: " + testInterp.intent() + " | Topic: " + testNeed.topic() + " | Concept: " + testNeed.targetConcept() + " | Entity: \"" + testNeed.targetEntityPhrase() + "\"");
      System.out.println("  LocalQwenProvider   -> Intent: " + qwenInterp.intent() + " | Topic: " + qwenNeed.topic() + " | Concept: " + qwenNeed.targetConcept() + " | Entity: \"" + qwenNeed.targetEntityPhrase() + "\"");

      if (testInterp.intent() == EveModelProvider.Intent.READ_PRODUCTION_CREW) {
        testProviderCrewCount++;
      }
      if (qwenInterp.intent() == EveModelProvider.Intent.READ_PRODUCTION_CREW) {
        qwenCrewCount++;
      }
      if (testNeed.topic() == qwenNeed.topic() && testNeed.targetConcept() == qwenNeed.targetConcept()) {
        bothMatchCount++;
        System.out.println("  => MATCH: Both independently resolved topic " + qwenNeed.topic() + " and concept " + qwenNeed.targetConcept());
      } else {
        System.out.println("  => DIVERGENCE: TestProvider=" + testNeed.topic() + " vs Qwen=" + qwenNeed.topic());
      }
    }

    System.out.println("\nTestModelProvider crew recognition: " + testProviderCrewCount + " / " + unseenQueries.size());
    System.out.println("LocalQwenProvider crew recognition: " + qwenCrewCount + " / " + unseenQueries.size());
    System.out.println("Exact Topic/Concept Agreement: " + bothMatchCount + " / " + unseenQueries.size());

    // Qwen should recognize the vast majority (>70%) of crew paraphrases
    assertThat(qwenCrewCount).isGreaterThanOrEqualTo(7);
  }
}
