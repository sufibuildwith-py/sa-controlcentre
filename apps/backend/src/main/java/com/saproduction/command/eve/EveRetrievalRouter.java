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
    private EveRetrievalService.Candidate lastReferencedEmployee;
    private EveRetrievalService.Candidate lastReferencedProduction;
    private List<EveRetrievalService.Candidate> pendingCandidates = new ArrayList<>();
    private EveModelProvider.Intent lastIntent;

    public EveRetrievalService.Candidate getLastReferencedEmployee() {
      return lastReferencedEmployee;
    }

    public void setLastReferencedEmployee(EveRetrievalService.Candidate c) {
      this.lastReferencedEmployee = c;
    }

    public EveRetrievalService.Candidate getLastReferencedProduction() {
      return lastReferencedProduction;
    }

    public void setLastReferencedProduction(EveRetrievalService.Candidate c) {
      this.lastReferencedProduction = c;
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
  }

  public record RouterResult(
      String answer,
      String status,
      List<EveDtos.EntityReference> referencedEntities,
      List<EveDtos.EvidenceItem> evidence,
      List<EveDtos.CandidateView> candidates) {}

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

    // 1. Check for Disambiguation resolution on follow-up turn
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
      if (sessionContext != null && isPronoun(interpretation.spokenEntity()) && sessionContext.getLastReferencedEmployee() != null) {
        return handleEmployeeFinance(sessionContext.getLastReferencedEmployee());
      }
      String spoken = resolveSpokenWithContext(interpretation.spokenEntity(), sessionContext != null ? sessionContext.getLastReferencedEmployee() : null);
      if (spoken == null) {
        return notFoundResponse("employee");
      }

      EveRetrievalService.ResolutionResult res = retrievalService.resolveEmployee(spoken);
      if (res.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, "team members", spoken, res.candidates());
      }
      if (res.status() == EveRetrievalService.ResolutionStatus.NOT_FOUND) {
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
      if (sessionContext != null && isPronoun(interpretation.spokenEntity()) && sessionContext.getLastReferencedProduction() != null) {
        return handleProductionClient(sessionContext.getLastReferencedProduction());
      }
      String spoken = resolveSpokenWithContext(interpretation.spokenEntity(), sessionContext != null ? sessionContext.getLastReferencedProduction() : null);
      if (spoken == null) {
        return notFoundResponse("production");
      }

      EveRetrievalService.ResolutionResult res = retrievalService.resolveProduction(spoken);
      if (res.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, "productions", spoken, res.candidates());
      }
      if (res.status() == EveRetrievalService.ResolutionStatus.NOT_FOUND) {
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
      if (sessionContext != null && isPronoun(interpretation.spokenEntity()) && sessionContext.getLastReferencedProduction() != null) {
        return handleProductionCrew(sessionContext.getLastReferencedProduction());
      }
      String spoken = resolveSpokenWithContext(interpretation.spokenEntity(), sessionContext != null ? sessionContext.getLastReferencedProduction() : null);
      if (spoken == null) {
        return notFoundResponse("production");
      }

      EveRetrievalService.ResolutionResult res = retrievalService.resolveProduction(spoken);
      if (res.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, "productions", spoken, res.candidates());
      }
      if (res.status() == EveRetrievalService.ResolutionStatus.NOT_FOUND) {
        return notFoundResponse(spoken);
      }

      EveRetrievalService.Candidate prod = res.resolved();
      if (sessionContext != null) {
        sessionContext.setLastReferencedProduction(prod);
      }
      return handleProductionCrew(prod);
    }

    // 5. Cross-domain: Check Production Member ("Usme Sharma bhi tha?")
    if (intent == EveModelProvider.Intent.CHECK_PRODUCTION_MEMBER) {
      EveRetrievalService.Candidate prod = sessionContext != null ? sessionContext.getLastReferencedProduction() : null;
      if (interpretation.spokenEntity() != null && !isPronoun(interpretation.spokenEntity())) {
        EveRetrievalService.ResolutionResult pRes = retrievalService.resolveProduction(interpretation.spokenEntity());
        if (pRes.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
          prod = pRes.resolved();
        }
      }

      String empName = interpretation.secondaryEntity() != null ? interpretation.secondaryEntity() : interpretation.spokenEntity();
      EveRetrievalService.ResolutionResult eRes = retrievalService.resolveEmployee(empName);

      if (prod != null && eRes.status() == EveRetrievalService.ResolutionStatus.RESOLVED && productionService != null) {
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
      if (sessionContext != null && isPronoun(interpretation.spokenEntity()) && sessionContext.getLastReferencedProduction() != null) {
        return handleProductionEquipment(sessionContext.getLastReferencedProduction());
      }
      String spoken = resolveSpokenWithContext(interpretation.spokenEntity(), sessionContext != null ? sessionContext.getLastReferencedProduction() : null);
      if (spoken == null) {
        return notFoundResponse("production");
      }

      EveRetrievalService.ResolutionResult res = retrievalService.resolveProduction(spoken);
      if (res.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
        EveRetrievalService.Candidate prod = res.resolved();
        if (sessionContext != null) {
          sessionContext.setLastReferencedProduction(prod);
        }
        return handleProductionEquipment(prod);
      }
      if (res.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, "productions", spoken, res.candidates());
      }
      return notFoundResponse(spoken);
    }

    // 7. Employee Assignments Domain ("Sharma ka kaam kis production pe tha?" / "Which production?")
    if (intent == EveModelProvider.Intent.READ_EMPLOYEE_ASSIGNMENTS) {
      String spoken = resolveSpokenWithContext(interpretation.spokenEntity(), sessionContext != null ? sessionContext.getLastReferencedEmployee() : null);
      if (spoken == null) {
        return notFoundResponse("employee");
      }

      EveRetrievalService.ResolutionResult res = retrievalService.resolveEmployee(spoken);
      if (res.status() == EveRetrievalService.ResolutionStatus.RESOLVED && productionService != null) {
        EveRetrievalService.Candidate emp = res.resolved();
        if (sessionContext != null) {
          sessionContext.setLastReferencedEmployee(emp);
        }

        List<ProductionService.View> allProds = productionService.list(null, null, null, null, null);
        List<ProductionService.View> assigned = allProds.stream()
            .filter(p -> p.members().stream().anyMatch(m -> m.employeeId().equals(emp.id())))
            .toList();

        if (!assigned.isEmpty()) {
          ProductionService.View first = assigned.get(0);
          if (sessionContext != null) {
            sessionContext.setLastReferencedProduction(new EveRetrievalService.Candidate(first.id(), "PRODUCTION", first.title(), first.id().toString().substring(0, 8), first.venueName()));
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
    }

    // 9. Production Tasks Domain ("Usme kaunsa task open hai?")
    if (intent == EveModelProvider.Intent.READ_PRODUCTION_TASKS) {
      EveRetrievalService.Candidate prod = sessionContext != null ? sessionContext.getLastReferencedProduction() : null;
      if (interpretation.spokenEntity() != null && !isPronoun(interpretation.spokenEntity())) {
        EveRetrievalService.ResolutionResult pRes = retrievalService.resolveProduction(interpretation.spokenEntity());
        if (pRes.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
          prod = pRes.resolved();
        } else if (pRes.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
          return ambiguityResponse(sessionContext, intent, "productions", interpretation.spokenEntity(), pRes.candidates());
        } else {
          return new RouterResult(
              String.format("I couldn't find a production/event matching \"%s\" in SA Command.", interpretation.spokenEntity()),
              "NOT_FOUND",
              List.of(),
              List.of(),
              List.of());
        }
      }

      if (prod != null) {
        if (sessionContext != null) {
          sessionContext.setLastReferencedProduction(prod);
        }
        return handleProductionTasks(prod);
      } else {
        return notFoundResponse("production");
      }
    }

    // 10. Work / Task Domain ("Kaunsa task abhi open hai?")
    if (intent == EveModelProvider.Intent.READ_TASKS_SUMMARY && taskService != null) {
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

    // 9. Headquarters / Equipment Availability ("Stand kitna available hai?")
    if ((intent == EveModelProvider.Intent.READ_EQUIPMENT_AVAILABILITY || intent == EveModelProvider.Intent.READ_EQUIPMENT) && headquartersService != null) {
      String query = interpretation.spokenEntity() != null ? interpretation.spokenEntity() : "";
      var eqResult = headquartersService.equipment(0, 5, query, null, null);
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> items = (List<Map<String, Object>>) eqResult.getOrDefault("items", List.of());

      if (!items.isEmpty()) {
        Map<String, Object> item = items.get(0);
        String name = (String) item.get("name");
        String code = (String) item.get("internalCode");
        BigDecimal usable = (BigDecimal) item.getOrDefault("usable", BigDecimal.ZERO);
        BigDecimal reserved = (BigDecimal) item.getOrDefault("reserved", BigDecimal.ZERO);
        BigDecimal available = usable.subtract(reserved).max(BigDecimal.ZERO);

        String answer = String.format("%s (%s) has %s usable units, with %s reserved, leaving %s currently available.",
            name, code != null ? code : "HQ", usable, reserved, available);

        return new RouterResult(
            answer,
            "COMPLETED",
            List.of(),
            List.of(new EveDtos.EvidenceItem("HEADQUARTERS", "Usable", usable.toString()),
                    new EveDtos.EvidenceItem("HEADQUARTERS", "Reserved", reserved.toString()),
                    new EveDtos.EvidenceItem("HEADQUARTERS", "Available", available.toString())),
            List.of());
      }
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
    if (intent == EveModelProvider.Intent.READ_PRODUCTION && interpretation.spokenEntity() != null) {
      if (sessionContext != null && isPronoun(interpretation.spokenEntity()) && sessionContext.getLastReferencedProduction() != null) {
        return handleProductionDetail(sessionContext.getLastReferencedProduction());
      }
      EveRetrievalService.ResolutionResult pRes = retrievalService.resolveProduction(interpretation.spokenEntity());
      if (pRes.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
        if (sessionContext != null) {
          sessionContext.setLastReferencedProduction(pRes.resolved());
        }
        return handleProductionDetail(pRes.resolved());
      }
      if (pRes.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
        return ambiguityResponse(sessionContext, intent, "productions", interpretation.spokenEntity(), pRes.candidates());
      }
      return new RouterResult(
          String.format("I couldn't find a production/event matching \"%s\" in SA Command.", interpretation.spokenEntity()),
          "NOT_FOUND",
          List.of(),
          List.of(),
          List.of());
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
    String answer = String.format("%s for client %s is scheduled on %s at %s. Status is %s with %d crew members and %d equipment reservations.",
        view.title(), view.clientName(), view.eventDate(), view.venueName(), view.status(), view.members().size(), view.equipment().size());

    return new RouterResult(
        answer,
        "COMPLETED",
        List.of(new EveDtos.EntityReference(view.id(), "PRODUCTION", view.title(), view.id().toString().substring(0, 8))),
        List.of(new EveDtos.EvidenceItem("PRODUCTION", "Status", view.status().name()),
                new EveDtos.EvidenceItem("PRODUCTION", "Venue", view.venueName())),
        List.of());
  }

  private String resolveSpokenWithContext(String spoken, EveRetrievalService.Candidate contextCandidate) {
    if (spoken != null && !spoken.isBlank()) {
      String clean = spoken.trim().toLowerCase(Locale.ROOT);
      if (clean.equals("uska") || clean.equals("woh") || clean.equals("him") || clean.equals("he") || clean.equals("us production") || clean.equals("that event") || clean.equals("wahan")) {
        if (contextCandidate != null) {
          return contextCandidate.displayName();
        }
      }
      return spoken;
    }
    return contextCandidate != null ? contextCandidate.displayName() : null;
  }

  private boolean isPronoun(String s) {
    if (s == null || s.isBlank()) return true;
    String clean = s.trim().toLowerCase(Locale.ROOT);
    return clean.equals("uska") || clean.equals("uske") || clean.equals("uski") || clean.equals("usme")
        || clean.equals("woh") || clean.equals("unka") || clean.equals("him") || clean.equals("her")
        || clean.equals("he") || clean.equals("it") || clean.equals("that") || clean.equals("this")
        || clean.equals("us production") || clean.equals("that event") || clean.equals("wahan");
  }

  private RouterResult ambiguityResponse(
      SessionContext sessionContext,
      EveModelProvider.Intent intent,
      String domainPlural,
      String spoken,
      List<EveRetrievalService.Candidate> candidates) {
    if (sessionContext != null) {
      sessionContext.setPendingCandidates(candidates);
      sessionContext.setLastIntent(intent);
    }
    List<EveDtos.CandidateView> views = candidates.stream()
        .map(c -> new EveDtos.CandidateView(c.id(), c.type(), c.displayName(), c.code(), c.detail()))
        .toList();

    String msg = String.format("I found multiple matching %s for \"%s\". Please choose which one you meant:", domainPlural, spoken);
    return new RouterResult(msg, "CLARIFICATION_REQUIRED", List.of(), List.of(), views);
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
}
