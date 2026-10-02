package com.saproduction.command.eve.cognitive;

import com.saproduction.command.eve.EveDtos;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * General Reasoning & External Capability Handler for EVE Cognitive Runtime 2.0.
 *
 * Capabilities:
 * - General workplace recommendations (e.g. lunch menu variations without food dictionaries).
 * - External capability boundaries (e.g. weather inquiries gracefully handled without closed-world ERP errors).
 * - Conversational pleasantries, gratitude, and natural conversational state.
 */
@Service
public class EveGeneralReasoningService {

  public record GeneralReasoningResult(
      String answer,
      EveOutcome outcome,
      List<EveReasoningStep> reasoningSteps) {}

  public GeneralReasoningResult handleGeneralRecommendation(
      String prompt,
      EveLanguageDetector.UserLanguage language,
      List<EveReasoningStep> steps,
      int startSeq) {

    int seq = startSeq;
    String lower = prompt.toLowerCase(Locale.ROOT).trim();
    String priorItem = extractPriorMentionedItem(lower);

    steps.add(EveReasoningStep.of(
        seq++,
        "GENERAL_REASONING",
        String.format("Formulating operational recommendation (priorConstraint=%s).",
            priorItem != null ? priorItem : "none")));

    String answer;
    boolean isCatering = lower.contains("lunch") || lower.contains("serve") || lower.contains("khana")
        || lower.contains("meal") || lower.contains("dinner") || lower.contains("food")
        || lower.contains("breakfast") || lower.contains("menu") || lower.contains("catering");

    if (isCatering) {
      if (priorItem != null) {
        if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
          answer = String.format("Since %s was served previously, a good balanced change for the team today would be Dal Makhani or Rajma Chawal with fresh salad, or a light Veg Pulao with Raita.", priorItem);
        } else if (language == EveLanguageDetector.UserLanguage.HINDI) {
          answer = String.format("कल %s था, तो आज टीम के लिए संतुलित और पौष्टिक बदलाव के रूप में राजमा चावल, दाल मखनी या मिक्स वेज पुलाव रायते के साथ एक बढ़िया विकल्प रहेगा।", priorItem);
        } else {
          answer = String.format("Kal %s tha, toh aaj team ke liye Rajma Chawal, Dal Makhani ya fresh Mix Veg Pulao raite ke sath bohot accha aur balanced option rahega.", priorItem);
        }
      } else {
        if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
          answer = "For production catering, a balanced, light meal—such as Dal with seasonal vegetables, Jeera Rice, and hot rotis—keeps the crew energized without post-lunch fatigue.";
        } else if (language == EveLanguageDetector.UserLanguage.HINDI) {
          answer = "क्रू के भोजन के लिए हल्का और संतुलित आहार—जैसे दाल, मौसमी सब्ज़ी, जीरा राइस और रोटियां—उपयुक्त रहेगा ताकि काम के दौरान ऊर्जा बनी रहे।";
        } else {
          answer = "Team ke lunch ke liye ek balanced aur light meal—jaise Dal, seasonal mix veg, Jeera Rice aur rotis—best rahega jisse shoot ke dauran crew energized rahe.";
        }
      }
    } else {
      if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
        answer = "Based on standard production operations, prioritizing critical-path setup tasks, staggering crew rotations, and maintaining clear communication is recommended.";
      } else if (language == EveLanguageDetector.UserLanguage.HINDI) {
        answer = "सुचारू संचालन के लिए पहले महत्वपूर्ण कार्यों को प्राथमिकता दें, क्रू के ब्रेक को रोटेट करें और टीम के साथ स्पष्ट समन्वय बनाए रखें।";
      } else {
        answer = "Operational kaam ko smoothly chalane ke liye pehle critical setup tasks complete karein, team rotations plan karein aur clear coordination banaye rakhein.";
      }
    }

    return new GeneralReasoningResult(answer, EveOutcome.COMPLETED, steps);
  }

  private String extractPriorMentionedItem(String lower) {
    var p1 = java.util.regex.Pattern.compile("(?i)(?:kal|yesterday|previously|last time|pehle)\\s+([a-zA-Z\\u0900-\\u097F]+?)(?:\\s+(?:tha|thi|the|served|khaya|order kiya|mangwaya|meal|food|lunch|dinner|tha lunch)\\b)");
    var m1 = p1.matcher(lower);
    if (m1.find()) {
      return m1.group(1).trim();
    }
    var p2 = java.util.regex.Pattern.compile("(?i)(?:had|served|ordered|ate)\\s+([a-zA-Z\\u0900-\\u097F]+?)(?:\\s+(?:yesterday|last night|earlier)\\b)");
    var m2 = p2.matcher(lower);
    if (m2.find()) {
      return m2.group(1).trim();
    }
    if (lower.contains("kal ") && lower.contains(" tha")) {
      int idxKal = lower.indexOf("kal ");
      int idxTha = lower.indexOf(" tha", idxKal);
      if (idxTha > idxKal + 4) {
        String sub = lower.substring(idxKal + 4, idxTha).trim();
        if (!sub.isBlank() && sub.length() < 30) {
          return sub;
        }
      }
    }
    return null;
  }

  public GeneralReasoningResult handleExternalCapability(
      String prompt,
      String serviceCategory,
      EveLanguageDetector.UserLanguage language,
      List<EveReasoningStep> steps,
      int startSeq) {

    int seq = startSeq;
    steps.add(EveReasoningStep.of(
        seq++,
        "EXTERNAL_CAPABILITY_CHECK",
        String.format("Identified request for external service '%s'. Checked capability registry: external live integrations are unconfigured in offline SA Command mode.", serviceCategory)));

    String serviceLabelEn;
    String serviceLabelHinglish;
    String serviceLabelHi;

    switch (serviceCategory.toUpperCase(Locale.ROOT)) {
      case "WEATHER" -> {
        serviceLabelEn = "live weather forecasts";
        serviceLabelHinglish = "live weather forecast";
        serviceLabelHi = "लाइव मौसम पूर्वानुमान";
      }
      case "FINANCE_MARKET" -> {
        serviceLabelEn = "live stock market or exchange rate";
        serviceLabelHinglish = "live stock market ya share price";
        serviceLabelHi = "शेयर बाज़ार या मुद्रा दर";
      }
      case "TRAVEL_TRANSIT" -> {
        serviceLabelEn = "live flight or transit tracking";
        serviceLabelHinglish = "live flight ya travel tracking";
        serviceLabelHi = "लाइव फ्लाइट या यात्रा ट्रैकिंग";
      }
      case "LIVE_SPORTS" -> {
        serviceLabelEn = "live sports scores";
        serviceLabelHinglish = "live sports score";
        serviceLabelHi = "लाइव खेल स्कोर";
      }
      default -> {
        serviceLabelEn = "external live third-party";
        serviceLabelHinglish = "external live third-party";
        serviceLabelHi = "बाहरी लाइव";
      }
    }

    String answer;
    if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
      answer = String.format("I can help with event production, schedules, crew, and equipment, but I don't have access to %s right now. Please check a dedicated service for real-time information.", serviceLabelEn);
    } else if (language == EveLanguageDetector.UserLanguage.HINDI) {
      answer = String.format("मैं इवेंट शेड्यूल, क्रू और उपकरणों में मदद कर सकती हूँ, लेकिन मेरे पास अभी %s की जानकारी उपलब्ध नहीं है। कृपया संबंधित सेवा देखें।", serviceLabelHi);
    } else {
      answer = String.format("Main productions, schedule aur crew manage karne me help kar sakti hoon, par mere paas abhi %s ka live access nahi hai. Field pe nikalne se pehle dedicated service zaroor check kar lijiye.", serviceLabelHinglish);
    }

    return new GeneralReasoningResult(answer, EveOutcome.UNSUPPORTED_CAPABILITY, steps);
  }

  public GeneralReasoningResult handleExternalWeather(
      String prompt,
      EveLanguageDetector.UserLanguage language,
      List<EveReasoningStep> steps,
      int startSeq) {
    return handleExternalCapability(prompt, "WEATHER", language, steps, startSeq);
  }

  public GeneralReasoningResult handleConversationalPleasantry(
      String prompt,
      EveLanguageDetector.UserLanguage language,
      List<EveReasoningStep> steps,
      int startSeq) {

    int seq = startSeq;
    steps.add(EveReasoningStep.of(
        seq++,
        "CONVERSATION",
        "Processing natural conversational exchange."));

    String lower = prompt.toLowerCase(Locale.ROOT).trim();
    String answer;

    if (lower.contains("thank") || lower.contains("shukriya") || lower.contains("dhanyawad")) {
      if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
        answer = "You're welcome! Let me know if you need anything else.";
      } else {
        answer = "Koi baat nahi! Kuch aur check karna ho toh bataiye.";
      }
    } else if (lower.equals("haan") || lower.equals("ha") || lower.equals("yes") || lower.equals("okay") || lower.equals("ok") || lower.equals("theek hai")) {
      if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
        answer = "Got it. How can I assist you further?";
      } else {
        answer = "Theek hai. Aage kya help kar sakti hoon?";
      }
    } else {
      if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
        answer = "Hello! I am EVE, your SA Command operational assistant. How can I help you today?";
      } else {
        answer = "Namaste! Main EVE hoon, SA Command operational assistant. Aaj main aapki kya madad kar sakti hoon?";
      }
    }

    return new GeneralReasoningResult(answer, EveOutcome.COMPLETED, steps);
  }
}
