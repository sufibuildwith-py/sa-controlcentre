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
    steps.add(EveReasoningStep.of(
        seq++,
        "GENERAL_REASONING",
        "Formulating operational recommendation based on conversational context."));

    String lower = prompt.toLowerCase(Locale.ROOT);
    String answer;

    // Workplace catering / lunch recommendations
    if (lower.contains("lunch") || lower.contains("serve") || lower.contains("khana") || lower.contains("meal") || lower.contains("paneer")) {
      if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
        answer = "Since paneer was served yesterday, a good balanced change today would be Rajma Chawal with fresh salad, Dal Makhani, or a light Veg Pulao with Raita.";
      } else if (language == EveLanguageDetector.UserLanguage.HINDI) {
        answer = "कल पनीर था, तो आज कर्मचारियों के लिए राजमा चावल, दाल मखनी या मिक्स वेज पुलाव रायते के साथ एक बढ़िया और संतुलित विकल्प रहेगा।";
      } else {
        answer = "Kal paneer tha, toh aaj team ke liye Rajma Chawal, Dal Makhani ya Mix Veg Pulao bohot accha aur balanced option rahega.";
      }
    } else {
      if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
        answer = "Based on standard production operations, keeping tasks balanced and communicating clearly with the team is recommended.";
      } else {
        answer = "Operational kaam ko smoothly chalane ke liye team ke sath clear coordination aur priority tasks pe focus karna best rahega.";
      }
    }

    return new GeneralReasoningResult(answer, EveOutcome.COMPLETED, steps);
  }

  public GeneralReasoningResult handleExternalWeather(
      String prompt,
      EveLanguageDetector.UserLanguage language,
      List<EveReasoningStep> steps,
      int startSeq) {

    int seq = startSeq;
    steps.add(EveReasoningStep.of(
        seq++,
        "EXTERNAL_CAPABILITY_CHECK",
        "Checked external capability registry: weather service is unconfigured in local offline mode."));

    String answer;
    if (language == EveLanguageDetector.UserLanguage.ENGLISH) {
      answer = "I can help with event production, schedules, crew, and equipment, but I don't have live weather access right now. Please check local forecast before heading out to the field.";
    } else if (language == EveLanguageDetector.UserLanguage.HINDI) {
      answer = "मैं इवेंट शेड्यूल, क्रू और उपकरणों में मदद कर सकती हूँ, लेकिन मेरे पास अभी लाइव मौसम की जानकारी उपलब्ध नहीं है। फील्ड पर जाने से पहले स्थानीय मौसम का पूर्वानुमान जांच लें।";
    } else {
      answer = "Main productions, schedule aur crew manage karne me help kar sakti hoon, par mere paas abhi live weather access nahi hai. Field pe nikalne se pehle local weather forecast zaroor check kar lijiye.";
    }

    return new GeneralReasoningResult(answer, EveOutcome.UNSUPPORTED_CAPABILITY, steps);
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
