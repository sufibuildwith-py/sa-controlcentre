package com.saproduction.command.eve;

import com.saproduction.command.employee.Employee360Service;
import com.saproduction.command.employee.EmployeeDtos;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.headquarters.HeadquartersService;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionMember;
import com.saproduction.command.production.ProductionMemberRepository;
import com.saproduction.command.production.ProductionRepository;
import com.saproduction.command.production.ProductionService;
import com.saproduction.command.work.WorkTask;
import com.saproduction.command.work.WorkTaskService;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bounded Retrieval Router for EVE Phase 2.
 * EVE is the intelligence layer; canonical SA Command services are the system of record.
 * Routes interpreted requests across Employee, Production, Work/Tasks, Headquarters/Equipment,
 * and Finance/Billing domains without creating a shadow database or executing business writes.
 */
@Component
public class EveRetrievalRouter {

  private final EveRetrievalService retrievalService;
  private final FinanceReadService financeReads;
  private final ProductionService productionService;
  private final ProductionRepository productionRepo;
  private final ProductionMemberRepository memberRepo;
  private final EmployeeService employeeService;
  private final WorkTaskService taskService;
  private final HeadquartersService headquartersService;
  private final EveMemoryService memoryService;
  private final EveDateTimeParser dateTimeParser;

  public static class SessionContext {
    private ActiveFocus activeEmployee;
    private ActiveFocus activeProduction;
    private ActiveFocus lastActiveEntity;
    private PendingClarification pendingClarification;
    private List<EveRetrievalService.Candidate> pendingCandidates = new ArrayList<>();
    private EveModelProvider.Intent lastIntent;
    private final List<TurnContext> recentTurns = new ArrayList<>();
    private final List<EveDtos.EvidenceItem> recentEvidence = new ArrayList<>();

    public record ActiveFocus(
        EveRetrievalService.Candidate candidate,
        String entityType,
        Instant focusedAt,
        double confidence,
        String resolutionReason) {}

    public record PendingClarification(
        EveModelProvider.Intent originalIntent,
        String targetEntityType,
        String originalInformationNeed,
        String originalEntityPhrase,
        String clarificationQuestion,
        String originalUserPrompt,
        List<UUID> candidateProductionIds,
        List<String> candidateDisplayNames,
        Instant requestedAt) {

      public PendingClarification(
          EveModelProvider.Intent originalIntent,
          String targetEntityType,
          String clarificationQuestion,
          String originalUserPrompt,
          Instant requestedAt) {
        this(
            originalIntent,
            targetEntityType,
            originalIntent != null ? originalIntent.name() : null,
            null,
            clarificationQuestion,
            originalUserPrompt,
            List.of(),
            List.of(),
            requestedAt != null ? requestedAt : Instant.now());
      }
    }

    public record TurnContext(
        String userPrompt,
        String assistantResponse,
        EveModelProvider.Intent intent,
        Instant timestamp) {}

    public EveRetrievalService.Candidate getLastReferencedEmployee() {
      return activeEmployee != null ? activeEmployee.candidate() : null;
    }

    public void setLastReferencedEmployee(EveRetrievalService.Candidate c) {
      if (c == null) {
        this.activeEmployee = null;
      } else {
        this.activeEmployee = new ActiveFocus(c, "EMPLOYEE", Instant.now(), 1.0, "EXPLICIT");
        this.lastActiveEntity = this.activeEmployee;
      }
    }

    public EveRetrievalService.Candidate getLastReferencedProduction() {
      return activeProduction != null ? activeProduction.candidate() : null;
    }

    public void setLastReferencedProduction(EveRetrievalService.Candidate c) {
      if (c == null) {
        this.activeProduction = null;
      } else {
        this.activeProduction = new ActiveFocus(c, "PRODUCTION", Instant.now(), 1.0, "EXPLICIT");
        this.lastActiveEntity = this.activeProduction;
      }
    }

    public ActiveFocus getActiveProduction() {
      return activeProduction;
    }

    public ActiveFocus getActiveEmployee() {
      return activeEmployee;
    }

    public ActiveFocus getLastActiveEntity() {
      return lastActiveEntity;
    }

    public boolean hasPendingClarification() {
      return pendingClarification != null;
    }

    public PendingClarification getPendingClarification() {
      return pendingClarification;
    }

    public void setPendingClarification(PendingClarification pc) {
      this.pendingClarification = pc;
    }

    public void clearPendingClarification() {
      this.pendingClarification = null;
    }

    public List<EveRetrievalService.Candidate> getPendingCandidates() {
      return pendingCandidates;
    }

    public void setPendingCandidates(List<EveRetrievalService.Candidate> candidates) {
      this.pendingCandidates = candidates != null ? new ArrayList<>(candidates) : new ArrayList<>();
    }

    public boolean hasPendingCandidates() {
      return pendingCandidates != null && !pendingCandidates.isEmpty();
    }

    public void clearPendingCandidates() {
      this.pendingCandidates.clear();
    }

    public EveModelProvider.Intent getLastIntent() {
      return lastIntent;
    }

    public void setLastIntent(EveModelProvider.Intent intent) {
      this.lastIntent = intent;
    }

    public void addTurn(String userPrompt, String assistantResponse, EveModelProvider.Intent intent) {
      if (recentTurns.size() >= 5) {
        recentTurns.remove(0);
      }
      recentTurns.add(new TurnContext(userPrompt, assistantResponse, intent, Instant.now()));
    }

    public List<TurnContext> getRecentTurns() {
      return Collections.unmodifiableList(recentTurns);
    }

    public void setRecentEvidence(List<EveDtos.EvidenceItem> evidence) {
      this.recentEvidence.clear();
      if (evidence != null) {
        this.recentEvidence.addAll(evidence);
      }
    }

    public List<EveDtos.EvidenceItem> getRecentEvidence() {
      return Collections.unmodifiableList(recentEvidence);
    }

    public String toContextSummary() {
      StringBuilder sb = new StringBuilder();
      if (activeProduction != null) {
        sb.append("[Active Production: ").append(activeProduction.candidate().displayName()).append("] ");
      }
      if (activeEmployee != null) {
        sb.append("[Active Employee: ").append(activeEmployee.candidate().displayName()).append("] ");
      }
      if (pendingClarification != null) {
        sb.append("[Pending Clarification: waiting for ").append(pendingClarification.targetEntityType())
            .append(" for intent ").append(pendingClarification.originalIntent())
            .append(". Question asked: \"").append(pendingClarification.clarificationQuestion()).append("\"] ");
      }
      if (hasPendingCandidates()) {
        sb.append("[Pending Candidates: ").append(pendingCandidates.size()).append(" options awaiting selection] ");
      }
      return sb.toString().trim();
    }

    public String toActiveFocusDescription() {
      List<String> foci = new ArrayList<>();
      if (activeProduction != null) foci.add("PRODUCTION: " + activeProduction.candidate().displayName());
      if (activeEmployee != null) foci.add("EMPLOYEE: " + activeEmployee.candidate().displayName());
      return foci.isEmpty() ? "None" : String.join(", ", foci);
    }
  }

  public record RouterResult(
      String answer,
      String status,
      List<EveDtos.EntityReference> referencedEntities,
      List<EveDtos.EvidenceItem> evidence,
      List<EveDtos.CandidateView> candidates) {}

  public enum AntecedentStatus {
    RESOLVED,
    AMBIGUOUS,
    MISSING_ANTECEDENT,
    NOT_PRONOUN
  }

  public record AntecedentResult(
      AntecedentStatus status,
      EveRetrievalService.Candidate resolved,
      String clarificationPrompt,
      List<EveRetrievalService.Candidate> candidates) {

    public static AntecedentResult resolved(EveRetrievalService.Candidate c) {
      return new AntecedentResult(AntecedentStatus.RESOLVED, c, null, List.of(c));
    }

    public static AntecedentResult ambiguous(String prompt, List<EveRetrievalService.Candidate> candidates) {
      return new AntecedentResult(AntecedentStatus.AMBIGUOUS, null, prompt, candidates != null ? candidates : List.of());
    }

    public static AntecedentResult missing(String prompt) {
      return new AntecedentResult(AntecedentStatus.MISSING_ANTECEDENT, null, prompt, List.of());
    }

    public static AntecedentResult notPronoun() {
      return new AntecedentResult(AntecedentStatus.NOT_PRONOUN, null, null, List.of());
    }
  }

  @Autowired
  public EveRetrievalRouter(
      EveRetrievalService retrievalService,
      FinanceReadService financeReads,
      @Autowired(required = false) ProductionService productionService,
      @Autowired(required = false) ProductionRepository productionRepo,
      @Autowired(required = false) ProductionMemberRepository memberRepo,
      EmployeeService employeeService,
      @Autowired(required = false) WorkTaskService taskService,
      @Autowired(required = false) HeadquartersService headquartersService,
      @Autowired(required = false) EveMemoryService memoryService,
      @Value("${app.time-zone:Asia/Kolkata}") String timeZone) {
    this.retrievalService = retrievalService;
    this.financeReads = financeReads;
    this.productionService = productionService;
    this.productionRepo = productionRepo;
    this.memberRepo = memberRepo;
    this.employeeService = employeeService;
    this.taskService = taskService;
    this.headquartersService = headquartersService;
    this.memoryService = memoryService;
    this.dateTimeParser = new EveDateTimeParser(timeZone);
  }

  public RouterResult routeAndRetrieve(
      EveModelProvider.EveInterpretation interpretation,
      SessionContext sessionContext,
      String userPrompt) {

    EveModelProvider.Intent intent = interpretation.intent();

    // 1a. General Greetings & Pleasantries ("hey", "hi", "hey eve", "good morning", "thanks", "hello", "kya haal hai")
    if (intent == EveModelProvider.Intent.GREETING || intent == EveModelProvider.Intent.GENERAL_QUERY || isConversationalGreeting(userPrompt)) {
      if (sessionContext != null) {
        sessionContext.clearPendingClarification();
      }
      String answer = greetingResponse(userPrompt);
      return new RouterResult(
          answer,
          "COMPLETED",
          List.of(),
          List.of(new EveDtos.EvidenceItem("SYSTEM", "Assistant Role", "EVE Local Intelligence")),
          List.of());
    }

    // 1b. Check for Pending Clarification resolution on follow-up turn ("Mips wala event", "2nd wala", etc.)
    if (sessionContext != null && sessionContext.hasPendingClarification()) {
      SessionContext.PendingClarification pending = sessionContext.getPendingClarification();

      // Check candidate selection if pending candidates exist
      if (sessionContext.hasPendingCandidates()) {
        String selectionHint = (interpretation != null && interpretation.spokenEntity() != null && !isPronoun(interpretation.spokenEntity()))
            ? interpretation.spokenEntity()
            : userPrompt;
        Optional<EveRetrievalService.Candidate> selected = retrievalService.selectFromCandidates(
            sessionContext.getPendingCandidates(), selectionHint);
        if (selected.isPresent()) {
          EveRetrievalService.Candidate chosen = selected.get();
          sessionContext.clearPendingCandidates();
          sessionContext.clearPendingClarification();
          return dispatchToOriginalIntent(pending.originalIntent(), chosen, sessionContext);
        }
      }

      // Check if user has switched to a different, unrelated domain (e.g. employee finance when production was pending)
      boolean isDomainSwitch = false;
      if (intent != null && intent != EveModelProvider.Intent.UNKNOWN && intent != EveModelProvider.Intent.RESOLVE_DISAMBIGUATION) {
        if ("PRODUCTION".equalsIgnoreCase(pending.targetEntityType())) {
          if (intent == EveModelProvider.Intent.READ_EMPLOYEE_FINANCE
              || intent == EveModelProvider.Intent.READ_EMPLOYEE_360
              || intent == EveModelProvider.Intent.PROPOSE_EMPLOYEE_PAYMENT
              || intent == EveModelProvider.Intent.READ_EMPLOYEE_ASSIGNMENTS
              || intent == EveModelProvider.Intent.READ_EQUIPMENT_AVAILABILITY
              || intent == EveModelProvider.Intent.READ_SYSTEM_SUMMARY
              || intent == EveModelProvider.Intent.GREETING) {
            isDomainSwitch = true;
          }
        } else if ("EMPLOYEE".equalsIgnoreCase(pending.targetEntityType())) {
          if (intent == EveModelProvider.Intent.READ_PRODUCTION
              || intent == EveModelProvider.Intent.READ_PRODUCTION_CREW
              || intent == EveModelProvider.Intent.READ_PRODUCTION_CLIENT
              || intent == EveModelProvider.Intent.READ_PRODUCTION_FINANCE
              || intent == EveModelProvider.Intent.READ_PRODUCTION_EQUIPMENT
              || intent == EveModelProvider.Intent.READ_PRODUCTION_TASKS
              || intent == EveModelProvider.Intent.READ_EQUIPMENT_AVAILABILITY
              || intent == EveModelProvider.Intent.READ_SYSTEM_SUMMARY
              || intent == EveModelProvider.Intent.GREETING) {
            isDomainSwitch = true;
          }
        }
      }

      if (isDomainSwitch) {
        sessionContext.clearPendingClarification();
        sessionContext.clearPendingCandidates();
        // Fall through to normal domain routing below
      } else {
        // Attempt entity resolution for the pending domain
        String entityPhrase = null;
        if (interpretation != null && interpretation.spokenEntity() != null && !interpretation.spokenEntity().isBlank() && !isPronoun(interpretation.spokenEntity())) {
          entityPhrase = interpretation.spokenEntity();
        } else {
          String cleanedPhrase = cleanEntitySearchPhrase(userPrompt);
          if (!cleanedPhrase.isBlank() && !isPronoun(cleanedPhrase)) {
            entityPhrase = cleanedPhrase;
          }
        }

        if (entityPhrase != null && !entityPhrase.isBlank() && !isPronoun(entityPhrase)) {
          if ("PRODUCTION".equalsIgnoreCase(pending.targetEntityType())) {
            EveRetrievalService.ResolutionResult pRes = resolveProductionSafe(entityPhrase, sessionContext);
            if (pRes != null && pRes.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
              EveRetrievalService.Candidate prod = pRes.resolved();
              sessionContext.setLastReferencedProduction(prod);
              sessionContext.clearPendingClarification();
              sessionContext.clearPendingCandidates();
              EveModelProvider.Intent targetIntent = (intent != null && intent != EveModelProvider.Intent.UNKNOWN && intent != EveModelProvider.Intent.RESOLVE_DISAMBIGUATION)
                  ? intent : pending.originalIntent();
              return dispatchToOriginalIntent(targetIntent, prod, sessionContext);
            } else if (pRes != null && pRes.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
              sessionContext.setPendingCandidates(pRes.candidates());
              return ambiguityResponse(sessionContext, pending.originalIntent(), "productions", entityPhrase, pRes.candidates());
            }
          } else if ("EMPLOYEE".equalsIgnoreCase(pending.targetEntityType())) {
            EveRetrievalService.ResolutionResult eRes = resolveEmployeeSafe(entityPhrase, sessionContext);
            if (eRes != null && eRes.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
              EveRetrievalService.Candidate emp = eRes.resolved();
              sessionContext.setLastReferencedEmployee(emp);
              sessionContext.clearPendingClarification();
              sessionContext.clearPendingCandidates();
              EveModelProvider.Intent targetIntent = (intent != null && intent != EveModelProvider.Intent.UNKNOWN && intent != EveModelProvider.Intent.RESOLVE_DISAMBIGUATION)
                  ? intent : pending.originalIntent();
              return dispatchToOriginalIntent(targetIntent, emp, sessionContext);
            } else if (eRes != null && eRes.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
              sessionContext.setPendingCandidates(eRes.candidates());
              return ambiguityResponse(sessionContext, pending.originalIntent(), "team members", entityPhrase, eRes.candidates());
            }
          }
        }
      }
    }

    // 1c. Disambiguation selection without active pending clarification
    if (intent == EveModelProvider.Intent.RESOLVE_DISAMBIGUATION || (sessionContext != null && sessionContext.hasPendingCandidates())) {
      if (sessionContext != null && sessionContext.hasPendingCandidates()) {
        String selectionHint = interpretation.spokenEntity() != null ? interpretation.spokenEntity() : userPrompt;
        Optional<EveRetrievalService.Candidate> selected = retrievalService.selectFromCandidates(
            sessionContext.getPendingCandidates(), selectionHint);

        if (selected.isPresent()) {
          EveRetrievalService.Candidate chosen = selected.get();
          sessionContext.clearPendingCandidates();

          if ("PRODUCTION".equalsIgnoreCase(chosen.type())) {
            sessionContext.setLastReferencedProduction(chosen);
            return handleProductionDetail(chosen);
          } else if ("EMPLOYEE".equalsIgnoreCase(chosen.type())) {
            sessionContext.setLastReferencedEmployee(chosen);
            return handleEmployeeFinance(chosen);
          }
        }
      }
    }

    // 2. Owner Vocabulary Learning
    if (intent == EveModelProvider.Intent.REMEMBER_VOCABULARY && memoryService != null) {
      String term = interpretation.spokenEntity();
      String canonicalName = interpretation.secondaryEntity();
      String canonicalType = interpretation.entityType() != null ? interpretation.entityType() : "VOCABULARY";
      if (term != null && canonicalName != null) {
        memoryService.remember(
            new EveDtos.MemoryRequest(canonicalType, term, canonicalType, null, canonicalName),
            "OPERATOR_EXPLICIT");
        return new RouterResult(
            String.format("Understood. I will remember that '%s' refers to %s.", term, canonicalName),
            "COMPLETED",
            List.of(),
            List.of(new EveDtos.EvidenceItem("MEMORY", "Learned Term", term + " -> " + canonicalName)),
            List.of());
      }
    }

    // 3. Employee Finance Domain
    if (intent == EveModelProvider.Intent.READ_EMPLOYEE_FINANCE) {
      AntecedentResult ant = resolveEmployeeAntecedent(interpretation.spokenEntity(), sessionContext, "ke finance");
      if (ant.status() == AntecedentStatus.RESOLVED) {
        if (sessionContext != null) sessionContext.setLastReferencedEmployee(ant.resolved());
        return handleEmployeeFinance(ant.resolved());
      }
      if (ant.status() == AntecedentStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, ant.clarificationPrompt(), ant.candidates());
      }
      if (ant.status() == AntecedentStatus.MISSING_ANTECEDENT) {
        return clarificationRequiredResponse(sessionContext, intent, "EMPLOYEE", ant.clarificationPrompt(), userPrompt);
      }

      String spoken = resolveSpokenWithContext(interpretation.spokenEntity(), sessionContext != null ? sessionContext.getLastReferencedEmployee() : null);
      if (spoken == null) {
        return clarificationRequiredResponse(sessionContext, intent, "EMPLOYEE", "Kis employee ya team member ke finance ki baat kar rahe ho? Please specify which person you'd like to check.", userPrompt);
      }

      EveRetrievalService.ResolutionResult res = resolveEmployeeSafe(spoken, sessionContext);
      if (res != null && res.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, "team members", spoken, res.candidates());
      }
      if (res == null || res.status() == EveRetrievalService.ResolutionStatus.NOT_FOUND) {
        return notFoundResponse(spoken);
      }

      EveRetrievalService.Candidate emp = res.resolved();
      if (sessionContext != null) {
        sessionContext.setLastReferencedEmployee(emp);
      }
      return handleEmployeeFinance(emp);
    }

    // 4. Production Client Domain ("cultural event MIPS ka client kon hai?")
    if (intent == EveModelProvider.Intent.READ_PRODUCTION_CLIENT) {
      AntecedentResult ant = resolveProductionAntecedent(interpretation.spokenEntity(), sessionContext, "ke client");
      if (ant.status() == AntecedentStatus.RESOLVED) {
        if (sessionContext != null) sessionContext.setLastReferencedProduction(ant.resolved());
        return handleProductionClient(ant.resolved());
      }
      if (ant.status() == AntecedentStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, ant.clarificationPrompt(), ant.candidates());
      }
      if (ant.status() == AntecedentStatus.MISSING_ANTECEDENT) {
        return clarificationRequiredResponse(sessionContext, intent, "PRODUCTION", ant.clarificationPrompt(), userPrompt);
      }

      String spoken = resolveSpokenWithContext(interpretation.spokenEntity(), sessionContext != null ? sessionContext.getLastReferencedProduction() : null);
      if (spoken == null) {
        return clarificationRequiredResponse(sessionContext, intent, "PRODUCTION", "Kis production ya event ke client ki baat kar rahe ho? Please specify which production you'd like to check.", userPrompt);
      }

      EveRetrievalService.ResolutionResult res = resolveProductionSafe(spoken, sessionContext);
      if (res != null && res.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, "productions", spoken, res.candidates());
      }
      if (res == null || res.status() == EveRetrievalService.ResolutionStatus.NOT_FOUND) {
        return new RouterResult(
            String.format("I couldn't find a production/event matching \"%s\" in SA Command.", spoken),
            "NOT_FOUND",
            List.of(),
            List.of(),
            List.of());
      }

      EveRetrievalService.Candidate prod = res.resolved();
      if (sessionContext != null) {
        sessionContext.setLastReferencedProduction(prod);
      }
      return handleProductionClient(prod);
    }

    // 5. Production Crew Domain ("Royal mein kaun gaya tha?")
    if (intent == EveModelProvider.Intent.READ_PRODUCTION_CREW) {
      AntecedentResult ant = resolveProductionAntecedent(interpretation.spokenEntity(), sessionContext, "ke crew");
      if (ant.status() == AntecedentStatus.RESOLVED) {
        if (sessionContext != null) sessionContext.setLastReferencedProduction(ant.resolved());
        return handleProductionCrew(ant.resolved());
      }
      if (ant.status() == AntecedentStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, ant.clarificationPrompt(), ant.candidates());
      }
      if (ant.status() == AntecedentStatus.MISSING_ANTECEDENT) {
        return clarificationRequiredResponse(sessionContext, intent, "PRODUCTION", ant.clarificationPrompt(), userPrompt);
      }

      String spoken = resolveSpokenWithContext(interpretation.spokenEntity(), sessionContext != null ? sessionContext.getLastReferencedProduction() : null);
      if (spoken == null) {
        return clarificationRequiredResponse(sessionContext, intent, "PRODUCTION", "Kis production ya event ke crew ki baat kar rahe ho? Please specify which production you'd like to check.", userPrompt);
      }

      EveRetrievalService.ResolutionResult res = resolveProductionSafe(spoken, sessionContext);
      if (res != null && res.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, "productions", spoken, res.candidates());
      }
      if (res == null || res.status() == EveRetrievalService.ResolutionStatus.NOT_FOUND) {
        return notFoundResponse(spoken);
      }

      EveRetrievalService.Candidate prod = res.resolved();
      if (sessionContext != null) {
        sessionContext.setLastReferencedProduction(prod);
      }
      return handleProductionCrew(prod);
    }

    // 5b. Cross-domain: Check Production Member ("Usme Sharma bhi tha?")
    if (intent == EveModelProvider.Intent.CHECK_PRODUCTION_MEMBER) {
      AntecedentResult ant = resolveProductionAntecedent(interpretation.spokenEntity(), sessionContext, "");
      EveRetrievalService.Candidate prod = null;
      if (ant.status() == AntecedentStatus.RESOLVED) {
        prod = ant.resolved();
      } else if (ant.status() == AntecedentStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, ant.clarificationPrompt(), ant.candidates());
      } else if (ant.status() == AntecedentStatus.NOT_PRONOUN && interpretation.spokenEntity() != null) {
        EveRetrievalService.ResolutionResult pRes = resolveProductionSafe(interpretation.spokenEntity(), sessionContext);
        if (pRes != null && pRes.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
          prod = pRes.resolved();
        }
      }

      if (prod == null) {
        return clarificationRequiredResponse(sessionContext, intent, "PRODUCTION", "Kis production ya event ki baat kar rahe ho? Please specify which event you'd like to check.", userPrompt);
      }

      String empName = interpretation.secondaryEntity() != null ? interpretation.secondaryEntity() : interpretation.spokenEntity();
      EveRetrievalService.ResolutionResult eRes = resolveEmployeeSafe(empName, sessionContext);

      if (eRes != null && eRes.status() == EveRetrievalService.ResolutionStatus.RESOLVED && productionService != null) {
        EveRetrievalService.Candidate emp = eRes.resolved();
        if (sessionContext != null) {
          sessionContext.setLastReferencedEmployee(emp);
          sessionContext.setLastReferencedProduction(prod);
        }
        ProductionService.View view = productionService.get(prod.id());
        Optional<ProductionService.MemberView> member = view.members().stream()
            .filter(m -> m.employeeId().equals(emp.id()) || m.employeeName().equalsIgnoreCase(emp.displayName()))
            .findFirst();

        if (member.isPresent()) {
          String answer = String.format("Yes, %s was assigned to %s as %s.", emp.displayName(), prod.displayName(), member.get().productionRole());
          return new RouterResult(
              answer,
              "COMPLETED",
              List.of(new EveDtos.EntityReference(prod.id(), "PRODUCTION", prod.displayName(), prod.code()),
                      new EveDtos.EntityReference(emp.id(), "EMPLOYEE", emp.displayName(), emp.code())),
              List.of(new EveDtos.EvidenceItem("PRODUCTION", "Crew Member", emp.displayName() + " (" + member.get().productionRole() + ")")),
              List.of());
        } else {
          String answer = String.format("No, %s is not assigned to %s.", emp.displayName(), prod.displayName());
          return new RouterResult(
              answer,
              "COMPLETED",
              List.of(new EveDtos.EntityReference(prod.id(), "PRODUCTION", prod.displayName(), prod.code())),
              List.of(new EveDtos.EvidenceItem("PRODUCTION", "Crew Check", "Not assigned")),
              List.of());
        }
      }
    }

    // 6. Production Equipment Domain ("Royal ka equipment kya tha?" / "Aur uska equipment?")
    if (intent == EveModelProvider.Intent.READ_PRODUCTION_EQUIPMENT) {
      AntecedentResult ant = resolveProductionAntecedent(interpretation.spokenEntity(), sessionContext, "ke equipment");
      if (ant.status() == AntecedentStatus.RESOLVED) {
        if (sessionContext != null) sessionContext.setLastReferencedProduction(ant.resolved());
        return handleProductionEquipment(ant.resolved());
      }
      if (ant.status() == AntecedentStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, ant.clarificationPrompt(), ant.candidates());
      }
      if (ant.status() == AntecedentStatus.MISSING_ANTECEDENT) {
        return clarificationRequiredResponse(sessionContext, intent, "PRODUCTION", ant.clarificationPrompt(), userPrompt);
      }

      String spoken = resolveSpokenWithContext(interpretation.spokenEntity(), sessionContext != null ? sessionContext.getLastReferencedProduction() : null);
      if (spoken == null) {
        return clarificationRequiredResponse(sessionContext, intent, "PRODUCTION", "Kis production ya event ke equipment ki baat kar rahe ho? Please specify which production you'd like to check.", userPrompt);
      }

      EveRetrievalService.ResolutionResult res = resolveProductionSafe(spoken, sessionContext);
      if (res != null && res.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
        EveRetrievalService.Candidate prod = res.resolved();
        if (sessionContext != null) {
          sessionContext.setLastReferencedProduction(prod);
        }
        return handleProductionEquipment(prod);
      }
      if (res != null && res.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, "productions", spoken, res.candidates());
      }
      return notFoundResponse(spoken);
    }

    // 6b. Production Finance Domain ("What is the contract for Sharma Wedding?" / "How much advance did we receive for Royal?")
    if (intent == EveModelProvider.Intent.READ_PRODUCTION_FINANCE) {
      AntecedentResult ant = resolveProductionAntecedent(interpretation.spokenEntity(), sessionContext, "ke finance");
      if (ant.status() == AntecedentStatus.RESOLVED) {
        if (sessionContext != null) sessionContext.setLastReferencedProduction(ant.resolved());
        return handleProductionFinance(ant.resolved());
      }
      if (ant.status() == AntecedentStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, ant.clarificationPrompt(), ant.candidates());
      }
      if (ant.status() == AntecedentStatus.MISSING_ANTECEDENT) {
        return clarificationRequiredResponse(sessionContext, intent, "PRODUCTION", ant.clarificationPrompt(), userPrompt);
      }

      String spoken = resolveSpokenWithContext(interpretation.spokenEntity(), sessionContext != null ? sessionContext.getLastReferencedProduction() : null);
      if (spoken == null) {
        return clarificationRequiredResponse(sessionContext, intent, "PRODUCTION", "Kis production ya event ke finance ki baat kar rahe ho? Please specify which production you'd like to check.", userPrompt);
      }

      EveRetrievalService.ResolutionResult res = resolveProductionSafe(spoken, sessionContext);
      if (res != null && res.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
        EveRetrievalService.Candidate prod = res.resolved();
        if (sessionContext != null) {
          sessionContext.setLastReferencedProduction(prod);
        }
        return handleProductionFinance(prod);
      }
      if (res != null && res.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, "productions", spoken, res.candidates());
      }
      return notFoundResponse(spoken);
    }

    // 7. Employee Assignments Domain ("Sharma ka kaam kis production pe tha?" / "Which production?")
    if (intent == EveModelProvider.Intent.READ_EMPLOYEE_ASSIGNMENTS) {
      AntecedentResult ant = resolveEmployeeAntecedent(interpretation.spokenEntity(), sessionContext, "ke assignments");
      EveRetrievalService.Candidate emp = null;
      if (ant.status() == AntecedentStatus.RESOLVED) {
        emp = ant.resolved();
      } else if (ant.status() == AntecedentStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, ant.clarificationPrompt(), ant.candidates());
      } else if (ant.status() == AntecedentStatus.MISSING_ANTECEDENT) {
        return clarificationRequiredResponse(sessionContext, intent, "EMPLOYEE", ant.clarificationPrompt(), userPrompt);
      } else {
        String spoken = resolveSpokenWithContext(interpretation.spokenEntity(), sessionContext != null ? sessionContext.getLastReferencedEmployee() : null);
        if (spoken == null) {
          return clarificationRequiredResponse(sessionContext, intent, "EMPLOYEE", "Kis employee ki baat kar rahe ho? Please specify which team member you'd like to check.", userPrompt);
        }
        EveRetrievalService.ResolutionResult res = resolveEmployeeSafe(spoken, sessionContext);
        if (res != null && res.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
          emp = res.resolved();
        } else if (res != null && res.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
          return ambiguityResponse(sessionContext, intent, "team members", spoken, res.candidates());
        } else {
          return notFoundResponse(spoken);
        }
      }

      if (emp != null) {
        return handleEmployeeAssignments(emp, sessionContext);
      }
    }

    // 8. Employee 360 / Profile Domain ("Who is Aarav Mehta?" / "What is his role?")
    if (intent == EveModelProvider.Intent.READ_EMPLOYEE_360) {
      AntecedentResult ant = resolveEmployeeAntecedent(interpretation.spokenEntity(), sessionContext, "ki profile");
      if (ant.status() == AntecedentStatus.RESOLVED) {
        if (sessionContext != null) sessionContext.setLastReferencedEmployee(ant.resolved());
        return handleEmployeeProfile(ant.resolved());
      }
      if (ant.status() == AntecedentStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, ant.clarificationPrompt(), ant.candidates());
      }
      if (ant.status() == AntecedentStatus.MISSING_ANTECEDENT) {
        return clarificationRequiredResponse(sessionContext, intent, "EMPLOYEE", ant.clarificationPrompt(), userPrompt);
      }

      String spoken = resolveSpokenWithContext(interpretation.spokenEntity(), sessionContext != null ? sessionContext.getLastReferencedEmployee() : null);
      if (spoken == null) {
        return clarificationRequiredResponse(sessionContext, intent, "EMPLOYEE", "Kis employee ya team member ki profile ki baat kar rahe ho? Please specify which person you'd like to check.", userPrompt);
      }

      EveRetrievalService.ResolutionResult res = resolveEmployeeSafe(spoken, sessionContext);
      if (res != null && res.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
        EveRetrievalService.Candidate emp = res.resolved();
        if (sessionContext != null) {
          sessionContext.setLastReferencedEmployee(emp);
        }
        return handleEmployeeProfile(emp);
      }
      if (res != null && res.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, "team members", spoken, res.candidates());
      }
      return notFoundResponse(spoken);
    }

    // 9. Production Tasks Domain ("Usme kaunsa task open hai?")
    if (intent == EveModelProvider.Intent.READ_PRODUCTION_TASKS) {
      AntecedentResult ant = resolveProductionAntecedent(interpretation.spokenEntity(), sessionContext, "ke tasks");
      if (ant.status() == AntecedentStatus.RESOLVED) {
        if (sessionContext != null) sessionContext.setLastReferencedProduction(ant.resolved());
        return handleProductionTasks(ant.resolved());
      }
      if (ant.status() == AntecedentStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, ant.clarificationPrompt(), ant.candidates());
      }
      if (ant.status() == AntecedentStatus.MISSING_ANTECEDENT) {
        return clarificationRequiredResponse(sessionContext, intent, "PRODUCTION", ant.clarificationPrompt(), userPrompt);
      }

      String spoken = interpretation.spokenEntity();
      EveRetrievalService.ResolutionResult pRes = resolveProductionSafe(spoken, sessionContext);
      if (pRes != null && pRes.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
        EveRetrievalService.Candidate prod = pRes.resolved();
        if (sessionContext != null) sessionContext.setLastReferencedProduction(prod);
        return handleProductionTasks(prod);
      } else if (pRes != null && pRes.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, "productions", spoken, pRes.candidates());
      } else {
        return new RouterResult(
            String.format("I couldn't find a production/event matching \"%s\" in SA Command.", spoken),
            "NOT_FOUND",
            List.of(),
            List.of(),
            List.of());
      }
    }

    // 10. Work / Task Domain ("Kaunsa task abhi open hai?")
    if (intent == EveModelProvider.Intent.READ_TASKS_SUMMARY && taskService != null) {
      // Invariant: A pronoun or follow-up with a valid production antecedent must not silently fall back to system-wide task summary.
      if (containsPronoun(userPrompt) || isPronoun(interpretation.spokenEntity()) || interpretation.followUp()) {
        AntecedentResult ant = resolveProductionAntecedent(interpretation.spokenEntity(), sessionContext, "ke tasks");
        if (ant.status() == AntecedentStatus.RESOLVED) {
          if (sessionContext != null) sessionContext.setLastReferencedProduction(ant.resolved());
          return handleProductionTasks(ant.resolved());
        }
        if (ant.status() == AntecedentStatus.AMBIGUOUS) {
          return ambiguityResponse(sessionContext, intent, ant.clarificationPrompt(), ant.candidates());
        }
        if (ant.status() == AntecedentStatus.MISSING_ANTECEDENT) {
          return clarificationRequiredResponse(sessionContext, intent, "PRODUCTION", ant.clarificationPrompt(), userPrompt);
        }
      }

      List<WorkTaskService.View> openTasks = taskService.list(null, null, null, null, null, null, null).stream()
          .filter(t -> t.status() != WorkTask.Status.DONE && t.status() != WorkTask.Status.CANCELLED)
          .toList();

      String answer = String.format("There are currently %d open tasks in the system.", openTasks.size());
      if (!openTasks.isEmpty()) {
        StringBuilder sb = new StringBuilder(answer).append(" Recent: ");
        for (int i = 0; i < Math.min(openTasks.size(), 3); i++) {
          if (i > 0) sb.append(", ");
          WorkTaskService.View t = openTasks.get(i);
          sb.append(t.title());
          if (t.assigneeName() != null) sb.append(" (").append(t.assigneeName()).append(")");
        }
        answer = sb.toString();
      }

      return new RouterResult(
          answer,
          "COMPLETED",
          List.of(),
          List.of(new EveDtos.EvidenceItem("WORK", "Open Task Count", String.valueOf(openTasks.size()))),
          List.of());
    }

    // 9. Headquarters / Equipment Availability ("Stand kitna available hai?", "hamare paas kitna Gaffer Tape hai?")
    if (intent == EveModelProvider.Intent.READ_EQUIPMENT_AVAILABILITY || intent == EveModelProvider.Intent.READ_EQUIPMENT || intent == EveModelProvider.Intent.READ_EQUIPMENT_INVENTORY) {
      if (headquartersService == null) {
        return new RouterResult(
            "Headquarters equipment service is currently unavailable.",
            "SYSTEM_UNAVAILABLE",
            List.of(),
            List.of(),
            List.of());
      }

      String query = interpretation.spokenEntity() != null ? interpretation.spokenEntity().trim() : "";
      // Strip common conversational artifacts
      String clean = query.replaceAll("(?i)\\b(hamare paas|hamare pass|kitna|kitne|kitni|hai|hain|available|stock|ka|ke|ki|batao|check|karo|bhi|kya|show me|how much|how many|do we have|in stock)\\b", "")
          .replaceAll("[^a-zA-Z0-9\\s-]", " ")
          .trim()
          .replaceAll("\\s+", " ");

      if (clean.isBlank() || "overview".equalsIgnoreCase(clean) || "stock".equalsIgnoreCase(clean) || "all".equalsIgnoreCase(clean)
          || "inventory".equalsIgnoreCase(clean) || "equipment".equalsIgnoreCase(clean)) {
        Map<String, Object> ov = headquartersService.overview();
        BigDecimal controlled = (BigDecimal) ov.getOrDefault("controlled", BigDecimal.ZERO);
        BigDecimal available = (BigDecimal) ov.getOrDefault("available", BigDecimal.ZERO);
        BigDecimal reserved = (BigDecimal) ov.getOrDefault("reserved", BigDecimal.ZERO);
        BigDecimal deployed = (BigDecimal) ov.getOrDefault("deployed", BigDecimal.ZERO);

        String answer = String.format("Headquarters currently controls %s physical items (%s available, %s reserved, %s deployed to active venues).",
            controlled, available, reserved, deployed);

        return new RouterResult(
            answer,
            "COMPLETED",
            List.of(),
            List.of(new EveDtos.EvidenceItem("HEADQUARTERS", "Total Controlled", controlled.toString()),
                    new EveDtos.EvidenceItem("HEADQUARTERS", "Total Available", available.toString()),
                    new EveDtos.EvidenceItem("HEADQUARTERS", "Total Reserved", reserved.toString()),
                    new EveDtos.EvidenceItem("HEADQUARTERS", "Total Deployed", deployed.toString())),
            List.of());
      }

      var eqResult = headquartersService.equipment(0, 5, clean, null, null);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> items = (List<Map<String, Object>>) eqResult.getOrDefault("items", List.of());

      if (items.isEmpty()) {
        return new RouterResult(
            String.format("I couldn't find an equipment record matching \"%s\" in SA Command Headquarters.", clean),
            "NOT_FOUND",
            List.of(),
            List.of(new EveDtos.EvidenceItem("HEADQUARTERS", "Query", clean)),
            List.of());
      }

      Map<String, Object> item = items.get(0);
      UUID id = (UUID) item.get("id");
      String name = (String) item.get("name");
      String code = (String) item.get("internalCode");
      String symbol = (String) item.getOrDefault("symbol", "units");
      String trackingMode = (String) item.getOrDefault("trackingMode", "QUANTITY");
      BigDecimal usable = (BigDecimal) item.getOrDefault("usable", item.getOrDefault("controlled", BigDecimal.ZERO));
      BigDecimal controlled = (BigDecimal) item.getOrDefault("controlled", usable);
      BigDecimal reserved = (BigDecimal) item.getOrDefault("reserved", BigDecimal.ZERO);
      BigDecimal available = (BigDecimal) item.getOrDefault("available", usable.subtract(reserved).max(BigDecimal.ZERO));

      String answer = String.format("%s (%s) has %s usable units, with %s reserved, leaving %s currently available.",
          name, code != null ? code : "HQ", usable, reserved, available);

      List<EveDtos.EntityReference> refs = new ArrayList<>();
      if (id != null) {
        refs.add(new EveDtos.EntityReference(id, "EQUIPMENT", name, code));
      }

      List<EveDtos.EvidenceItem> evidence = List.of(
          new EveDtos.EvidenceItem("HEADQUARTERS", "Equipment Name", name),
          new EveDtos.EvidenceItem("HEADQUARTERS", "Internal Code", code != null ? code : "HQ"),
          new EveDtos.EvidenceItem("HEADQUARTERS", "Tracking Mode", trackingMode),
          new EveDtos.EvidenceItem("HEADQUARTERS", "Controlled Stock", controlled + " " + symbol),
          new EveDtos.EvidenceItem("HEADQUARTERS", "Reserved Stock", reserved + " " + symbol),
          new EveDtos.EvidenceItem("HEADQUARTERS", "Available Stock", available + " " + symbol));

      return new RouterResult(answer, "COMPLETED", refs, evidence, List.of());
    }

    // 10. Temporal Schedule Read ("Kal kaunsa event hai?" / "Aaj ka schedule")
    if (intent == EveModelProvider.Intent.READ_SCHEDULE_BY_DATE && productionService != null) {
      String dateExpr = interpretation.relativeDate() != null ? interpretation.relativeDate() : userPrompt;
      Optional<LocalDate> targetDate = dateTimeParser.resolveRelativeDate(dateExpr);

      if (targetDate.isPresent()) {
        LocalDate date = targetDate.get();
        List<ProductionService.View> events = productionService.list(null, null, date, date, null);

        if (!events.isEmpty()) {
          ProductionService.View first = events.get(0);
          String answer = String.format("On %s, %d event is scheduled: %s at %s.", date, events.size(), first.title(), first.venueName());
          return new RouterResult(
              answer,
              "COMPLETED",
              List.of(new EveDtos.EntityReference(first.id(), "PRODUCTION", first.title(), first.id().toString().substring(0, 8))),
              List.of(new EveDtos.EvidenceItem("CALENDAR", "Scheduled Event", first.title())),
              List.of());
        } else {
          return new RouterResult(
              String.format("No events are currently scheduled for %s.", date),
              "COMPLETED",
              List.of(),
              List.of(new EveDtos.EvidenceItem("CALENDAR", "Events Count", "0")),
              List.of());
        }
      }
    }

    // 11. General Production Info
    if (intent == EveModelProvider.Intent.READ_PRODUCTION) {
      AntecedentResult ant = resolveProductionAntecedent(interpretation.spokenEntity(), sessionContext, "");
      if (ant.status() == AntecedentStatus.RESOLVED) {
        if (sessionContext != null) sessionContext.setLastReferencedProduction(ant.resolved());
        return handleProductionDetail(ant.resolved());
      }
      if (ant.status() == AntecedentStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, ant.clarificationPrompt(), ant.candidates());
      }
      if (ant.status() == AntecedentStatus.MISSING_ANTECEDENT && (interpretation.spokenEntity() == null || isPronoun(interpretation.spokenEntity()))) {
        return clarificationRequiredResponse(sessionContext, intent, "PRODUCTION", ant.clarificationPrompt(), userPrompt);
      }

      String spoken = interpretation.spokenEntity();
      if (spoken != null) {
        EveRetrievalService.ResolutionResult pRes = resolveProductionSafe(spoken, sessionContext);
        if (pRes != null && pRes.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
          if (sessionContext != null) {
            sessionContext.setLastReferencedProduction(pRes.resolved());
          }
          return handleProductionDetail(pRes.resolved());
        }
        if (pRes != null && pRes.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
          return ambiguityResponse(sessionContext, intent, "productions", spoken, pRes.candidates());
        }
        return new RouterResult(
            String.format("I couldn't find a production/event matching \"%s\" in SA Command.", spoken),
            "NOT_FOUND",
            List.of(),
            List.of(),
            List.of());
      }
    }

    // 14. Fallback / Unrecognized
    return new RouterResult(
        "I couldn't find relevant records matching your request in SA Command. Please verify the entity name or ask about employee finance, productions, tasks, or equipment.",
        "NOT_FOUND",
        List.of(),
        List.of(),
        List.of());
  }

  private RouterResult handleEmployeeFinance(EveRetrievalService.Candidate emp) {
    Map<String, Object> empFinance = financeReads.employee(emp.id());
    BigDecimal earned = (BigDecimal) empFinance.getOrDefault("earned", BigDecimal.ZERO);
    BigDecimal paid = (BigDecimal) empFinance.getOrDefault("paid", BigDecimal.ZERO);
    BigDecimal outstanding = (BigDecimal) empFinance.getOrDefault("outstanding", BigDecimal.ZERO);

    NumberFormat inr = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    String earnedStr = inr.format(earned);
    String paidStr = inr.format(paid);
    String outstandingStr = inr.format(outstanding);

    String answer = String.format(
        "%s (%s) currently has %s outstanding. Total earned to date is %s with %s already disbursed.",
        emp.displayName(), emp.code(), outstandingStr, earnedStr, paidStr);

    List<EveDtos.EntityReference> entities = List.of(
        new EveDtos.EntityReference(emp.id(), "EMPLOYEE", emp.displayName(), emp.code()));
    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("FINANCE", "Total Earned", earnedStr),
        new EveDtos.EvidenceItem("FINANCE", "Total Paid", paidStr),
        new EveDtos.EvidenceItem("FINANCE", "Outstanding Balance", outstandingStr));

    return new RouterResult(answer, "COMPLETED", entities, evidence, List.of());
  }

  private RouterResult handleProductionCrew(EveRetrievalService.Candidate prod) {
    if (productionService == null) {
      return new RouterResult("Production service is currently unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }
    ProductionService.View view = productionService.get(prod.id());
    List<ProductionService.MemberView> members = view.members();

    String answer;
    if (members.isEmpty()) {
      answer = String.format("%s currently has no assigned crew members.", view.title());
    } else {
      StringBuilder sb = new StringBuilder(String.format("%s has %d assigned crew member%s: ",
          view.title(), members.size(), members.size() == 1 ? "" : "s"));
      for (int i = 0; i < members.size(); i++) {
        if (i > 0) sb.append(", ");
        ProductionService.MemberView m = members.get(i);
        sb.append(m.employeeName()).append(" (").append(m.productionRole()).append(")");
      }
      answer = sb.toString();
    }

    List<EveDtos.EntityReference> entities = List.of(
        new EveDtos.EntityReference(view.id(), "PRODUCTION", view.title(), view.id().toString().substring(0, 8)));
    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("PRODUCTION", "Client", view.clientName()),
        new EveDtos.EvidenceItem("PRODUCTION", "Event Date", view.eventDate().toString()),
        new EveDtos.EvidenceItem("PRODUCTION", "Venue", view.venueName()),
        new EveDtos.EvidenceItem("PRODUCTION", "Crew Count", String.valueOf(members.size())));

    return new RouterResult(answer, "COMPLETED", entities, evidence, List.of());
  }

  private RouterResult handleProductionEquipment(EveRetrievalService.Candidate prod) {
    if (productionService == null) {
      return new RouterResult("Production service is currently unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }
    ProductionService.View view = productionService.get(prod.id());
    List<ProductionService.EquipmentView> eq = view.equipment();

    String answer;
    if (eq.isEmpty()) {
      answer = String.format("%s currently has no equipment reserved from Headquarters.", view.title());
    } else {
      StringBuilder sb = new StringBuilder(String.format("%s has %d reserved equipment item%s: ",
          view.title(), eq.size(), eq.size() == 1 ? "" : "s"));
      for (int i = 0; i < eq.size(); i++) {
        if (i > 0) sb.append(", ");
        ProductionService.EquipmentView e = eq.get(i);
        sb.append(e.quantity().stripTrailingZeros().toPlainString()).append("x ").append(e.equipmentName());
        if (e.internalCode() != null) sb.append(" (").append(e.internalCode()).append(")");
      }
      answer = sb.toString();
    }

    List<EveDtos.EntityReference> entities = List.of(
        new EveDtos.EntityReference(view.id(), "PRODUCTION", view.title(), view.id().toString().substring(0, 8)));
    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("HEADQUARTERS", "Equipment Items Count", String.valueOf(eq.size())));

    return new RouterResult(answer, "COMPLETED", entities, evidence, List.of());
  }

  private RouterResult handleProductionDetail(EveRetrievalService.Candidate prod) {
    if (productionService == null) {
      return new RouterResult("Production service unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }
    ProductionService.View view = productionService.get(prod.id());
    List<ProductionService.MemberView> members = view.members() != null ? view.members() : List.of();
    List<ProductionService.EquipmentView> equipment = view.equipment() != null ? view.equipment() : List.of();

    List<WorkTaskService.View> openTasks = List.of();
    if (taskService != null) {
      try {
        openTasks = taskService.list(null, view.id(), null, null, null, null, null).stream()
            .filter(t -> t.status() != WorkTask.Status.DONE && t.status() != WorkTask.Status.CANCELLED)
            .toList();
      } catch (Exception ignored) {}
    }

    String answer = String.format("%s for client %s is scheduled on %s at %s. Status is %s with %d crew member(s), %d equipment reservation(s), and %d open task(s).",
        view.title(),
        view.clientName() != null ? view.clientName() : "N/A",
        view.eventDate() != null ? view.eventDate().toString() : "TBD",
        view.venueName() != null ? view.venueName() : "TBD",
        view.status() != null ? view.status().name() : "DRAFT",
        members.size(),
        equipment.size(),
        openTasks.size());

    List<EveDtos.EvidenceItem> evidence = new ArrayList<>();
    evidence.add(new EveDtos.EvidenceItem("PRODUCTION", "Title", view.title()));
    if (view.clientName() != null) evidence.add(new EveDtos.EvidenceItem("PRODUCTION", "Client", view.clientName()));
    if (view.eventDate() != null) evidence.add(new EveDtos.EvidenceItem("PRODUCTION", "Event Date", view.eventDate().toString()));
    if (view.venueName() != null) evidence.add(new EveDtos.EvidenceItem("PRODUCTION", "Venue", view.venueName()));
    if (view.status() != null) evidence.add(new EveDtos.EvidenceItem("PRODUCTION", "Status", view.status().name()));
    if (view.priority() != null) evidence.add(new EveDtos.EvidenceItem("PRODUCTION", "Priority", view.priority().name()));
    if (view.startTime() != null) evidence.add(new EveDtos.EvidenceItem("PRODUCTION", "Start Time", view.startTime().toString()));
    if (view.endTime() != null) evidence.add(new EveDtos.EvidenceItem("PRODUCTION", "End Time", view.endTime().toString()));
    if (view.description() != null && !view.description().isBlank()) evidence.add(new EveDtos.EvidenceItem("PRODUCTION", "Notes", view.description()));
    evidence.add(new EveDtos.EvidenceItem("PRODUCTION", "Crew Count", String.valueOf(members.size())));
    for (int i = 0; i < Math.min(members.size(), 3); i++) {
      var m = members.get(i);
      evidence.add(new EveDtos.EvidenceItem("PRODUCTION", "Crew Member", m.employeeName() + " (" + m.productionRole() + ")"));
    }
    evidence.add(new EveDtos.EvidenceItem("HEADQUARTERS", "Equipment Reservations Count", String.valueOf(equipment.size())));
    for (int i = 0; i < Math.min(equipment.size(), 3); i++) {
      var eq = equipment.get(i);
      evidence.add(new EveDtos.EvidenceItem("HEADQUARTERS", "Equipment", eq.quantity() + "x " + eq.equipmentName()));
    }
    evidence.add(new EveDtos.EvidenceItem("WORK", "Open Tasks Count", String.valueOf(openTasks.size())));
    for (int i = 0; i < Math.min(openTasks.size(), 3); i++) {
      var t = openTasks.get(i);
      evidence.add(new EveDtos.EvidenceItem("WORK", "Open Task", t.title() + (t.assigneeName() != null ? " (" + t.assigneeName() + ")" : "")));
    }

    return new RouterResult(
        answer,
        "COMPLETED",
        List.of(new EveDtos.EntityReference(view.id(), "PRODUCTION", view.title(), view.id().toString().substring(0, 8))),
        evidence,
        List.of());
  }

  private String resolveSpokenWithContext(String spoken, EveRetrievalService.Candidate contextCandidate) {
    if (spoken != null && !spoken.isBlank()) {
      if (isPronoun(spoken)) {
        return contextCandidate != null ? contextCandidate.displayName() : null;
      }
      return spoken;
    }
    return contextCandidate != null ? contextCandidate.displayName() : null;
  }

  public static boolean isPronoun(String s) {
    if (s == null || s.isBlank()) return true;
    String clean = s.trim().toLowerCase(Locale.ROOT)
        .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "");
    clean = clean.replaceAll("^(?:aur|and|the|ye|yeh|wo|woh|is|us)\\s+", "");
    return clean.equals("uska") || clean.equals("uske") || clean.equals("uski") || clean.equals("usme")
        || clean.equals("isme") || clean.equals("iska") || clean.equals("iske") || clean.equals("iski")
        || clean.equals("woh") || clean.equals("wo") || clean.equals("unka") || clean.equals("unke") || clean.equals("unki")
        || clean.equals("inka") || clean.equals("inke") || clean.equals("inki") || clean.equals("inhe") || clean.equals("unhe")
        || clean.equals("use") || clean.equals("isse") || clean.equals("usse")
        || clean.equals("him") || clean.equals("her") || clean.equals("he") || clean.equals("she")
        || clean.equals("it") || clean.equals("its") || clean.equals("that") || clean.equals("this") || clean.equals("they") || clean.equals("them")
        || clean.equals("event") || clean.equals("production") || clean.equals("that event") || clean.equals("this event")
        || clean.equals("us production") || clean.equals("is production") || clean.equals("that production") || clean.equals("this production")
        || clean.equals("wahan") || clean.equals("there");
  }

  public static boolean containsPronoun(String s) {
    if (s == null || s.isBlank()) return false;
    String[] words = s.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+");
    for (String w : words) {
      if (isPronoun(w)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isConversationalGreeting(String text) {
    if (text == null || text.isBlank()) return false;
    String clean = text.trim().toLowerCase(Locale.ROOT)
        .replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "");

    Set<String> greetings = Set.of(
        "hey", "hi", "hello", "heyy", "heyyy", "hii", "hiii",
        "hey eve", "hi eve", "hello eve", "yo", "greetings",
        "good morning", "good afternoon", "good evening", "good day",
        "thanks", "thank you", "thank you so much", "thanks eve", "thank you eve",
        "thx", "ty", "shukriya", "dhanyawad", "bahut shukriya",
        "kya haal hai", "kya haal", "kaise ho", "kese ho", "kaise hain", "kese hain",
        "sab theek", "aur bhai", "how are you", "how are you doing", "how's it going", "how are things"
    );

    if (greetings.contains(clean)) {
      return true;
    }

    if (clean.startsWith("hey ") || clean.startsWith("hello ") || clean.startsWith("hi ") || clean.startsWith("good morning ")) {
      String remainder = clean.replaceFirst("^(?:hey|hello|hi|good morning)\\s+", "").trim();
      return remainder.equals("there") || remainder.equals("eve") || remainder.equals("team") || remainder.equals("sir") || remainder.isEmpty();
    }

    return false;
  }

  public static String greetingResponse(String text) {
    if (text != null) {
      String clean = text.trim().toLowerCase(Locale.ROOT);
      if (clean.contains("thank") || clean.contains("shukriya") || clean.contains("dhanyawad") || clean.contains("thx") || clean.contains("ty")) {
        return "You're welcome! Let me know if you need anything else with productions, crew, tasks, or equipment.";
      }
      if (clean.contains("kya haal") || clean.contains("kaise ho") || clean.contains("how are you")) {
        return "I am doing well and ready to assist you! What would you like to check in SA Command today?";
      }
    }
    return "Hello! I am EVE, the local operational intelligence layer of SA Command. I can help you with productions, team members, tasks, equipment, and financial tracking.";
  }

  public AntecedentResult resolveProductionAntecedent(
      String spokenEntity,
      SessionContext sessionContext,
      String contextDescription) {

    if (spokenEntity != null && !spokenEntity.isBlank() && !isPronoun(spokenEntity)) {
      return AntecedentResult.notPronoun();
    }

    if (sessionContext != null) {
      if (sessionContext.getLastReferencedProduction() != null) {
        return AntecedentResult.resolved(sessionContext.getLastReferencedProduction());
      }

      if (sessionContext.getLastReferencedEmployee() != null && productionService != null) {
        EveRetrievalService.Candidate emp = sessionContext.getLastReferencedEmployee();
        List<ProductionService.View> allProds = productionService.list(null, null, null, null, null);
        List<ProductionService.View> assigned = allProds.stream()
            .filter(p -> p.members() != null && p.members().stream().anyMatch(m -> m.employeeId().equals(emp.id())))
            .toList();

        if (assigned.size() == 1) {
          ProductionService.View first = assigned.get(0);
          EveRetrievalService.Candidate prodCandidate = new EveRetrievalService.Candidate(
              first.id(), "PRODUCTION", first.title(), first.id().toString().substring(0, 8), first.venueName());
          sessionContext.setLastReferencedProduction(prodCandidate);
          return AntecedentResult.resolved(prodCandidate);
        } else if (assigned.size() > 1) {
          List<EveRetrievalService.Candidate> cands = assigned.stream()
              .map(p -> new EveRetrievalService.Candidate(
                  p.id(), "PRODUCTION", p.title(), p.id().toString().substring(0, 8), p.venueName()))
              .toList();
          return AntecedentResult.ambiguous(
              String.format("%s is assigned to %d productions. Which production's %s would you like to check?",
                  emp.displayName(), assigned.size(), contextDescription),
              cands);
        } else {
          return AntecedentResult.missing(
              String.format("%s currently has no active production assignments to check %s for.",
                  emp.displayName(), contextDescription));
        }
      }
    }

    String prompt = String.format("Kis production ya event %s ki baat kar rahe ho? Please specify which production you'd like to check.", contextDescription);
    return AntecedentResult.missing(prompt);
  }

  public AntecedentResult resolveEmployeeAntecedent(
      String spokenEntity,
      SessionContext sessionContext,
      String contextDescription) {

    if (spokenEntity != null && !spokenEntity.isBlank() && !isPronoun(spokenEntity)) {
      return AntecedentResult.notPronoun();
    }

    if (sessionContext != null) {
      if (sessionContext.getLastReferencedEmployee() != null) {
        return AntecedentResult.resolved(sessionContext.getLastReferencedEmployee());
      }

      if (sessionContext.getLastReferencedProduction() != null && productionService != null) {
        ProductionService.View prodView = productionService.get(sessionContext.getLastReferencedProduction().id());
        List<ProductionService.MemberView> members = prodView != null && prodView.members() != null ? prodView.members() : List.of();
        if (members.size() == 1) {
          EveRetrievalService.ResolutionResult empRes = resolveEmployeeSafe(members.get(0).employeeName(), sessionContext);
          if (empRes != null && empRes.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
            sessionContext.setLastReferencedEmployee(empRes.resolved());
            return AntecedentResult.resolved(empRes.resolved());
          }
        } else if (members.size() > 1) {
          List<EveRetrievalService.Candidate> candidates = new ArrayList<>();
          for (ProductionService.MemberView m : members) {
            EveRetrievalService.ResolutionResult empRes = resolveEmployeeSafe(m.employeeName(), sessionContext);
            if (empRes != null && empRes.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
              candidates.add(empRes.resolved());
            } else {
              candidates.add(new EveRetrievalService.Candidate(m.employeeId(), "EMPLOYEE", m.employeeName(), "", m.productionRole()));
            }
          }
          return AntecedentResult.ambiguous(
              String.format("I found multiple team members on %s. Which person's %s would you like to check?",
                  sessionContext.getLastReferencedProduction().displayName(), contextDescription),
              candidates);
        }
      }
    }

    String prompt = String.format("Kis employee ya team member %s ki baat kar rahe ho? Please specify which person you'd like to check.", contextDescription);
    return AntecedentResult.missing(prompt);
  }

  private RouterResult ambiguityResponse(
      SessionContext sessionContext,
      EveModelProvider.Intent intent,
      String customPrompt,
      List<EveRetrievalService.Candidate> candidates) {
    return ambiguityResponseWithEntity(sessionContext, intent, customPrompt, null, candidates);
  }

  private RouterResult ambiguityResponseWithEntity(
      SessionContext sessionContext,
      EveModelProvider.Intent intent,
      String customPrompt,
      String originalEntityPhrase,
      List<EveRetrievalService.Candidate> candidates) {
    if (sessionContext != null) {
      sessionContext.setPendingCandidates(candidates);
      sessionContext.setLastIntent(intent);
      String targetType = (candidates != null && !candidates.isEmpty()) ? candidates.get(0).type() : "ENTITY";
      sessionContext.setPendingClarification(new SessionContext.PendingClarification(
          intent,
          targetType,
          intent != null ? intent.name() : null,
          originalEntityPhrase,
          customPrompt,
          customPrompt,
          candidates != null ? candidates.stream().map(EveRetrievalService.Candidate::id).toList() : List.of(),
          candidates != null ? candidates.stream().map(EveRetrievalService.Candidate::displayName).toList() : List.of(),
          Instant.now()));
    }
    List<EveDtos.CandidateView> views = candidates != null
        ? candidates.stream().map(c -> new EveDtos.CandidateView(c.id(), c.type(), c.displayName(), c.code(), c.detail())).toList()
        : List.of();

    return new RouterResult(customPrompt, "CLARIFICATION_REQUIRED", List.of(), List.of(), views);
  }

  private RouterResult ambiguityResponse(
      SessionContext sessionContext,
      EveModelProvider.Intent intent,
      String domainPlural,
      String spoken,
      List<EveRetrievalService.Candidate> candidates) {
    String msg;
    if (isPronoun(spoken)) {
      msg = String.format("I found multiple matching %s. Please choose which one you meant:", domainPlural);
    } else {
      msg = String.format("I found multiple matching %s for \"%s\". Please choose which one you meant:", domainPlural, spoken);
    }
    return ambiguityResponseWithEntity(sessionContext, intent, msg, spoken, candidates);
  }

  private RouterResult handleProductionClient(EveRetrievalService.Candidate prod) {
    if (productionService == null) {
      return new RouterResult("Production service is currently unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }
    ProductionService.View view = productionService.get(prod.id());
    String clientName = view.clientName();

    List<EveDtos.EntityReference> entities = List.of(
        new EveDtos.EntityReference(view.id(), "PRODUCTION", view.title(), view.id().toString().substring(0, 8)));

    if (clientName != null && !clientName.isBlank()) {
      String answer = String.format("The client for %s is %s.", view.title(), clientName);
      List<EveDtos.EvidenceItem> evidence = List.of(
          new EveDtos.EvidenceItem("PRODUCTION", "Client", clientName),
          new EveDtos.EvidenceItem("PRODUCTION", "Event Date", view.eventDate().toString()),
          new EveDtos.EvidenceItem("PRODUCTION", "Venue", view.venueName()),
          new EveDtos.EvidenceItem("PRODUCTION", "Status", view.status().name()));
      return new RouterResult(answer, "COMPLETED", entities, evidence, List.of());
    } else {
      String answer = String.format("I found %s, but I don't have a client recorded for it.", view.title());
      List<EveDtos.EvidenceItem> evidence = List.of(
          new EveDtos.EvidenceItem("PRODUCTION", "Client", "Not recorded"),
          new EveDtos.EvidenceItem("PRODUCTION", "Event Date", view.eventDate().toString()),
          new EveDtos.EvidenceItem("PRODUCTION", "Venue", view.venueName()));
      return new RouterResult(answer, "COMPLETED", entities, evidence, List.of());
    }
  }

  private RouterResult handleProductionTasks(EveRetrievalService.Candidate prod) {
    if (taskService == null) {
      return new RouterResult("Task service is currently unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }
    List<WorkTaskService.View> allTasks = taskService.list(null, prod.id(), null, null, null, null, null);
    List<WorkTaskService.View> openTasks = allTasks.stream()
        .filter(t -> t.status() != WorkTask.Status.DONE && t.status() != WorkTask.Status.CANCELLED)
        .toList();

    List<EveDtos.EntityReference> entities = List.of(
        new EveDtos.EntityReference(prod.id(), "PRODUCTION", prod.displayName(), prod.code()));

    if (openTasks.isEmpty()) {
      String answer = String.format("There are currently no open tasks for %s.", prod.displayName());
      List<EveDtos.EvidenceItem> evidence = List.of(
          new EveDtos.EvidenceItem("WORK", "Production Tasks Count", String.valueOf(allTasks.size())),
          new EveDtos.EvidenceItem("WORK", "Open Tasks", "0"));
      return new RouterResult(answer, "COMPLETED", entities, evidence, List.of());
    } else {
      StringBuilder sb = new StringBuilder(String.format("There %s %d open task%s for %s: ",
          openTasks.size() == 1 ? "is" : "are",
          openTasks.size(),
          openTasks.size() == 1 ? "" : "s",
          prod.displayName()));
      for (int i = 0; i < Math.min(openTasks.size(), 3); i++) {
        if (i > 0) sb.append(", ");
        WorkTaskService.View t = openTasks.get(i);
        sb.append(t.title());
        if (t.assigneeName() != null) {
          sb.append(" (").append(t.assigneeName()).append(")");
        }
      }
      if (openTasks.size() > 3) {
        sb.append(String.format(" and %d more.", openTasks.size() - 3));
      }
      List<EveDtos.EvidenceItem> evidence = List.of(
          new EveDtos.EvidenceItem("WORK", "Production Open Tasks", String.valueOf(openTasks.size())),
          new EveDtos.EvidenceItem("WORK", "Next Task", openTasks.get(0).title()));
      return new RouterResult(sb.toString(), "COMPLETED", entities, evidence, List.of());
    }
  }

  private RouterResult notFoundResponse(String term) {
    return new RouterResult(
        String.format("I could not find any active records matching \"%s\" in the system.", term),
        "NOT_FOUND",
        List.of(),
        List.of(),
        List.of());
  }

  public static String cleanEntitySearchPhrase(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    String s = text.trim();
    s = s.replaceAll("^[\"']+|[\"']+$", "").trim();
    s = s.replaceAll("(?i)^(?:haan|ha|yes|yep|aur|woh|wo|the|jo|mera|meri|apna|apni|accha|achha|ok|okay)\\s+", "");
    s = s.replaceAll("(?i)\\s+(?:mein|me|pe|par)?\\s*(?:kon|kaun|who|crew|kitne log|kaam|assign|assigned|gaya|gya)\\s+(?:gaya|gya|hai|he|tha|thi|the|h|aaya|aya).*$", "");
    s = s.replaceAll("(?i)\\s+(?:ki\\s+baat\\s+kar\\s+raha\\s+hu|ki\\s+baat\\s+kar\\s+rahe\\s+the|ki\\s+baat\\s+hai|hai|tha|thi|the|he)$", "");
    s = s.replaceAll("(?i)\\s+(?:ke\\s+)?(?:event|production|show)(?:\\s+(?:mein|me|par|pe))?$", "");
    s = s.replaceAll("(?i)\\s+(?:wala|wali|wale)(?:\\s+(?:event|production|show))?$", "");
    s = s.replaceAll("(?i)\\s+(?:event|production|show)$", "");
    s = s.replaceAll("(?i)\\s+(?:wala|wali|wale)$", "");
    s = s.replaceAll("(?i)^(?:event|production|show)\\s+", "");
    s = s.replaceAll("(?i)\\s+(?:finance|contract|advance|outstanding|tasks|task|equipment|crew|client|venue|date)\\??$", "");
    s = s.replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "").trim();
    return s.isBlank() ? text.trim() : s;
  }

  private RouterResult clarificationRequiredResponse(
      SessionContext sessionContext,
      EveModelProvider.Intent intent,
      String targetEntityType,
      String clarificationPrompt,
      String originalUserPrompt) {
    return clarificationRequiredResponse(sessionContext, intent, targetEntityType, clarificationPrompt, originalUserPrompt, List.of());
  }

  private RouterResult clarificationRequiredResponse(
      SessionContext sessionContext,
      EveModelProvider.Intent intent,
      String targetEntityType,
      String clarificationPrompt,
      String originalUserPrompt,
      List<EveRetrievalService.Candidate> candidates) {
    if (sessionContext != null) {
      sessionContext.setPendingClarification(new SessionContext.PendingClarification(
          intent,
          targetEntityType,
          intent != null ? intent.name() : null,
          null,
          clarificationPrompt,
          originalUserPrompt,
          candidates != null ? candidates.stream().map(EveRetrievalService.Candidate::id).toList() : List.of(),
          candidates != null ? candidates.stream().map(EveRetrievalService.Candidate::displayName).toList() : List.of(),
          Instant.now()));
      if (candidates != null && !candidates.isEmpty()) {
        sessionContext.setPendingCandidates(candidates);
      }
      sessionContext.setLastIntent(intent);
    }
    List<EveDtos.CandidateView> views = (candidates != null)
        ? candidates.stream().map(c -> new EveDtos.CandidateView(c.id(), c.type(), c.displayName(), c.code(), c.detail())).toList()
        : List.of();
    return new RouterResult(clarificationPrompt, "CLARIFICATION_REQUIRED", List.of(), List.of(), views);
  }

  private RouterResult dispatchToOriginalIntent(
      EveModelProvider.Intent intent,
      EveRetrievalService.Candidate candidate,
      SessionContext sessionContext) {
    if (candidate == null) {
      return notFoundResponse("specified entity");
    }

    if ("PRODUCTION".equalsIgnoreCase(candidate.type())) {
      if (sessionContext != null) {
        sessionContext.setLastReferencedProduction(candidate);
      }
      if (intent == EveModelProvider.Intent.READ_PRODUCTION_EQUIPMENT) {
        return handleProductionEquipment(candidate);
      }
      if (intent == EveModelProvider.Intent.READ_PRODUCTION_CLIENT) {
        return handleProductionClient(candidate);
      }
      if (intent == EveModelProvider.Intent.READ_PRODUCTION_CREW) {
        return handleProductionCrew(candidate);
      }
      if (intent == EveModelProvider.Intent.READ_PRODUCTION_FINANCE) {
        return handleProductionFinance(candidate);
      }
      if (intent == EveModelProvider.Intent.READ_PRODUCTION_TASKS) {
        return handleProductionTasks(candidate);
      }
      return handleProductionDetail(candidate);
    } else if ("EMPLOYEE".equalsIgnoreCase(candidate.type())) {
      if (sessionContext != null) {
        sessionContext.setLastReferencedEmployee(candidate);
      }
      if (intent == EveModelProvider.Intent.READ_EMPLOYEE_ASSIGNMENTS) {
        return handleEmployeeAssignments(candidate, sessionContext);
      }
      if (intent == EveModelProvider.Intent.READ_EMPLOYEE_360) {
        return handleEmployeeProfile(candidate);
      }
      return handleEmployeeFinance(candidate);
    }

    return notFoundResponse(candidate.displayName());
  }

  private RouterResult handleProductionFinance(EveRetrievalService.Candidate prod) {
    if (financeReads == null) {
      return new RouterResult("Finance service is currently unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }
    Map<String, Object> prodFinance = financeReads.production(prod.id());
    BigDecimal contracted = (BigDecimal) prodFinance.getOrDefault("contracted", BigDecimal.ZERO);
    BigDecimal received = (BigDecimal) prodFinance.getOrDefault("received", BigDecimal.ZERO);
    BigDecimal outstanding = (BigDecimal) prodFinance.getOrDefault("outstanding", BigDecimal.ZERO);
    BigDecimal expense = (BigDecimal) prodFinance.getOrDefault("incurredExpense", BigDecimal.ZERO);

    NumberFormat inr = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    String contractedStr = inr.format(contracted);
    String receivedStr = inr.format(received);
    String outstandingStr = inr.format(outstanding);
    String expenseStr = inr.format(expense);

    String answer = String.format(
        "%s has a contract value of %s with %s advance received, leaving an outstanding balance of %s (incurred expense: %s).",
        prod.displayName(), contractedStr, receivedStr, outstandingStr, expenseStr);

    List<EveDtos.EntityReference> entities = List.of(
        new EveDtos.EntityReference(prod.id(), "PRODUCTION", prod.displayName(), prod.code()));
    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("FINANCE", "Production", prod.displayName()),
        new EveDtos.EvidenceItem("FINANCE", "Contract Value", contractedStr),
        new EveDtos.EvidenceItem("FINANCE", "Advance Received", receivedStr),
        new EveDtos.EvidenceItem("FINANCE", "Outstanding Balance", outstandingStr),
        new EveDtos.EvidenceItem("FINANCE", "Incurred Expenses", expenseStr));

    return new RouterResult(answer, "COMPLETED", entities, evidence, List.of());
  }

  private RouterResult handleEmployeeProfile(EveRetrievalService.Candidate emp) {
    if (employeeService == null) {
      return new RouterResult("Employee service is currently unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }
    EmployeeDtos.View view = employeeService.get(emp.id());
    NumberFormat inr = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    String salaryStr = view.baseSalaryMinor() > 0
        ? inr.format(new BigDecimal(view.baseSalaryMinor()).movePointLeft(2))
        : "Not specified";

    StringBuilder sb = new StringBuilder();
    sb.append(String.format("%s (%s) is a %s in %s.",
        view.displayName(),
        view.employeeCode() != null ? view.employeeCode() : "EMP",
        view.roleTitle() != null ? view.roleTitle() : "Team Member",
        view.department() != null ? view.department() : "Operations"));

    sb.append(String.format(" Status: %s, Employment Type: %s.",
        view.status() != null ? view.status().name() : "ACTIVE",
        view.employmentType() != null ? view.employmentType() : "FULL_TIME"));

    if (view.joiningDate() != null) {
      sb.append(String.format(" Joined on: %s.", view.joiningDate()));
    }
    if (view.phone() != null && !view.phone().isBlank()) {
      sb.append(String.format(" Phone: %s.", view.phone()));
    }
    if (view.email() != null && !view.email().isBlank()) {
      sb.append(String.format(" Email: %s.", view.email()));
    }
    if (view.baseSalaryMinor() > 0) {
      sb.append(String.format(" Base salary: %s.", salaryStr));
    }

    List<EveDtos.EntityReference> entities = List.of(
        new EveDtos.EntityReference(view.id(), "EMPLOYEE", view.displayName(), view.employeeCode()));

    List<EveDtos.EvidenceItem> evidence = new ArrayList<>();
    evidence.add(new EveDtos.EvidenceItem("EMPLOYEE", "Name", view.displayName()));
    evidence.add(new EveDtos.EvidenceItem("EMPLOYEE", "Code", view.employeeCode() != null ? view.employeeCode() : ""));
    evidence.add(new EveDtos.EvidenceItem("EMPLOYEE", "Role", view.roleTitle() != null ? view.roleTitle() : ""));
    evidence.add(new EveDtos.EvidenceItem("EMPLOYEE", "Department", view.department() != null ? view.department() : ""));
    evidence.add(new EveDtos.EvidenceItem("EMPLOYEE", "Status", view.status() != null ? view.status().name() : "ACTIVE"));
    evidence.add(new EveDtos.EvidenceItem("EMPLOYEE", "Employment Type", view.employmentType() != null ? view.employmentType() : ""));
    if (view.joiningDate() != null) {
      evidence.add(new EveDtos.EvidenceItem("EMPLOYEE", "Joining Date", view.joiningDate().toString()));
    }
    if (view.phone() != null) {
      evidence.add(new EveDtos.EvidenceItem("EMPLOYEE", "Phone", view.phone()));
    }
    if (view.email() != null) {
      evidence.add(new EveDtos.EvidenceItem("EMPLOYEE", "Email", view.email()));
    }
    if (view.baseSalaryMinor() > 0) {
      evidence.add(new EveDtos.EvidenceItem("EMPLOYEE", "Base Salary", salaryStr));
    }

    return new RouterResult(sb.toString(), "COMPLETED", entities, evidence, List.of());
  }

  private RouterResult handleEmployeeAssignments(EveRetrievalService.Candidate emp, SessionContext sessionContext) {
    if (productionService == null) {
      return new RouterResult("Production service is currently unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }
    if (sessionContext != null) {
      sessionContext.setLastReferencedEmployee(emp);
    }
    final UUID targetEmpId = emp.id();
    List<ProductionService.View> allProds = productionService.list(null, null, null, null, null);
    List<ProductionService.View> assigned = allProds.stream()
        .filter(p -> p.members() != null && p.members().stream().anyMatch(m -> m.employeeId().equals(targetEmpId)))
        .toList();

    if (!assigned.isEmpty()) {
      ProductionService.View first = assigned.get(0);
      if (sessionContext != null) {
        sessionContext.setLastReferencedProduction(new EveRetrievalService.Candidate(
            first.id(), "PRODUCTION", first.title(), first.id().toString().substring(0, 8), first.venueName()));
      }
      String answer = String.format("%s is currently assigned to %s on %s.", emp.displayName(), first.title(), first.eventDate());
      return new RouterResult(
          answer,
          "COMPLETED",
          List.of(new EveDtos.EntityReference(emp.id(), "EMPLOYEE", emp.displayName(), emp.code()),
                  new EveDtos.EntityReference(first.id(), "PRODUCTION", first.title(), first.id().toString().substring(0, 8))),
          List.of(new EveDtos.EvidenceItem("PRODUCTION", "Assigned Production", first.title() + " (" + first.eventDate() + ")")),
          List.of());
    } else {
      return new RouterResult(
          String.format("%s currently has no active production assignments recorded.", emp.displayName()),
          "COMPLETED",
          List.of(new EveDtos.EntityReference(emp.id(), "EMPLOYEE", emp.displayName(), emp.code())),
          List.of(new EveDtos.EvidenceItem("PRODUCTION", "Assignments", "None")),
          List.of());
    }
  }

  private EveRetrievalService.ResolutionResult resolveEmployeeSafe(String spoken, SessionContext sessionContext) {
    if (retrievalService == null) return null;
    EveRetrievalService.ResolutionResult res = retrievalService.resolveEmployee(spoken, sessionContext);
    if (res == null) {
      res = retrievalService.resolveEmployee(spoken);
    }
    return res;
  }

  private EveRetrievalService.ResolutionResult resolveProductionSafe(String spoken, SessionContext sessionContext) {
    if (retrievalService == null) return null;
    EveRetrievalService.ResolutionResult res = retrievalService.resolveProduction(spoken, sessionContext);
    if (res == null) {
      res = retrievalService.resolveProduction(spoken);
    }
    return res;
  }
}
