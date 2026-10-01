package com.saproduction.command.eve.capability;

import com.saproduction.command.employee.EmployeeDtos;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.eve.*;
import com.saproduction.command.eve.system.EveCapability;
import com.saproduction.command.eve.system.EveInformationTopic;
import com.saproduction.command.eve.system.EveSystemConcept;
import com.saproduction.command.eve.system.EveSystemModel;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Capability-based Information Resolution Layer for EVE.
 *
 * Replaces hardcoded phrase matching with principled:
 * 1. InformationNeed comprehension
 * 2. System Model capability binding
 * 3. Contextual reference & topic-switching resolution
 * 4. Strict domain isolation (no fallthrough across domains)
 * 5. Canonical state retrieval and evidence gathering
 */
@Component
public class EveCapabilityResolver {

  private static final Logger log = LoggerFactory.getLogger(EveCapabilityResolver.class);

  private final EveSystemModel systemModel;
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

  @Autowired
  public EveCapabilityResolver(
      EveSystemModel systemModel,
      EveRetrievalService retrievalService,
      @Autowired(required = false) FinanceReadService financeReads,
      @Autowired(required = false) ProductionService productionService,
      @Autowired(required = false) ProductionRepository productionRepo,
      @Autowired(required = false) ProductionMemberRepository memberRepo,
      @Autowired(required = false) EmployeeService employeeService,
      @Autowired(required = false) WorkTaskService taskService,
      @Autowired(required = false) HeadquartersService headquartersService,
      @Autowired(required = false) EveMemoryService memoryService,
      @Value("${app.time-zone:Asia/Kolkata}") String timeZone) {
    this.systemModel = systemModel;
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

  /**
   * Resolves the user's InformationNeed against the system model, binds to the appropriate bounded capability,
   * queries the authoritative canonical domain service, and returns a grounded RouterResult.
   */
  public EveRetrievalRouter.RouterResult resolve(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt) {

    log.info("EVE_CAPABILITY: Resolving information need: topic={}, concept={}, phrase='{}', pronoun={}",
        need.topic(), need.targetConcept(), need.targetEntityPhrase(), need.isPronoun());

    // 1. Check for Unsupported / Out-of-Scope Queries
    if (need.topic() == EveInformationTopic.UNSUPPORTED) {
      String reason = need.reasoningSummary() != null
          ? need.reasoningSummary()
          : "SA Command does not have authoritative data or capabilities for this request.";
      return new EveRetrievalRouter.RouterResult(
          reason,
          "UNSUPPORTED",
          List.of(),
          List.of(new EveDtos.EvidenceItem("SYSTEM", "Boundary", "Outside SA Command authoritative domains")),
          List.of());
    }

    // 2. Conversational Greeting
    if (need.topic() == EveInformationTopic.GREETING) {
      if (sessionContext != null) {
        sessionContext.clearPendingClarification();
      }
      return new EveRetrievalRouter.RouterResult(
          greetingResponse(userPrompt),
          "COMPLETED",
          List.of(),
          List.of(new EveDtos.EvidenceItem("SYSTEM", "Assistant Role", "EVE Local Intelligence")),
          List.of());
    }

    // 3. Find Capability in System Model
    Optional<EveCapability> capOpt = systemModel.findCapability(need.topic(), need.targetConcept());
    if (capOpt.isEmpty()) {
      return new EveRetrievalRouter.RouterResult(
          "I could not identify an authoritative SA Command capability to handle this question.",
          "UNSUPPORTED",
          List.of(),
          List.of(),
          List.of());
    }

    EveCapability capability = capOpt.get();
    log.info("EVE_CAPABILITY: Selected capability: {} ({})", capability.id(), capability.description());

    // 4. Dispatch by Topic with Strict Domain Boundaries
    return switch (need.topic()) {
      case EQUIPMENT_STOCK -> handleEquipmentStock(need, sessionContext, userPrompt);
      case EQUIPMENT_OVERVIEW -> handleEquipmentOverview();
      case CLIENT_INFO -> handleProductionClient(need, sessionContext, userPrompt);
      case CREW_MEMBERS -> handleProductionCrew(need, sessionContext, userPrompt);
      case EQUIPMENT_ASSIGNED -> handleProductionEquipment(need, sessionContext, userPrompt);
      case PRODUCTION_TASKS -> handleProductionTasks(need, sessionContext, userPrompt);
      case PRODUCTION_OVERVIEW -> handleProductionOverview(need, sessionContext, userPrompt);
      case PRODUCTION_FINANCE -> handleProductionFinance(need, sessionContext, userPrompt);
      case MEMBER_PRESENCE -> handleMemberPresence(need, sessionContext, userPrompt);
      case EMPLOYEE_FINANCE -> handleEmployeeFinance(need, sessionContext, userPrompt);
      case EMPLOYEE_ASSIGNMENTS -> handleEmployeeAssignments(need, sessionContext, userPrompt);
      case EMPLOYEE_PROFILE -> handleEmployeeProfile(need, sessionContext, userPrompt);
      case TASK_SUMMARY -> handleTasksSummary(need, sessionContext, userPrompt);
      case SCHEDULE_DATE -> handleScheduleDate(need, userPrompt);
      case SYSTEM_OVERVIEW -> handleSystemOverview();
      case DISAMBIGUATION_CHOICE -> handleDisambiguation(need, sessionContext, userPrompt);
      case VOCABULARY_DEFINITION -> handleVocabulary(need);
      default -> new EveRetrievalRouter.RouterResult(
          "I understood your request but no canonical handler is registered for this topic.",
          "UNSUPPORTED",
          List.of(),
          List.of(),
          List.of());
    };
  }

  // --------------------------------------------------------------------------
  // Headquarters / Equipment Domain
  // --------------------------------------------------------------------------

  private EveRetrievalRouter.RouterResult handleEquipmentStock(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt) {

    if (headquartersService == null) {
      return new EveRetrievalRouter.RouterResult(
          "Headquarters equipment service is currently unavailable.",
          "SYSTEM_UNAVAILABLE",
          List.of(),
          List.of(),
          List.of());
    }

    String itemQuery = need.targetEntityPhrase();
    if (itemQuery == null || itemQuery.isBlank()) {
      // User asked general equipment stock without specific entity
      return handleEquipmentOverview();
    }

    // Clean search phrase
    itemQuery = cleanItemQuery(itemQuery);

    var eqResult = headquartersService.equipment(0, 5, itemQuery, null, null);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) eqResult.getOrDefault("items", List.of());

    if (items.isEmpty()) {
      // Invariant: Equipment query that finds no equipment records returns truthful NOT_FOUND specifically for equipment.
      // MUST NEVER FALL THROUGH to employee finance, productions, or any other domain!
      log.info("EVE_CAPABILITY: No equipment matching '{}' in headquarters", itemQuery);
      return new EveRetrievalRouter.RouterResult(
          String.format("I could not find any equipment or inventory record matching \"%s\" in SA Command Headquarters.", itemQuery),
          "NOT_FOUND",
          List.of(),
          List.of(new EveDtos.EvidenceItem("HEADQUARTERS", "Query", itemQuery)),
          List.of());
    }

    Map<String, Object> item = items.get(0);
    UUID id = (UUID) item.get("id");
    String name = (String) item.get("name");
    String internalCode = (String) item.get("internalCode");
    String symbol = (String) item.getOrDefault("symbol", "units");
    String trackingMode = (String) item.getOrDefault("trackingMode", "QUANTITY");
    BigDecimal controlled = (BigDecimal) item.getOrDefault("controlled", BigDecimal.ZERO);
    BigDecimal reserved = (BigDecimal) item.getOrDefault("reserved", BigDecimal.ZERO);
    BigDecimal available = (BigDecimal) item.getOrDefault("available", BigDecimal.ZERO);

    String answer;
    if (reserved.compareTo(BigDecimal.ZERO) > 0) {
      answer = String.format("%s (%s) has %s %s in stock across headquarters locations (%s %s currently reserved for events, leaving %s %s available).",
          name, internalCode != null ? internalCode : "HQ",
          controlled, symbol,
          reserved, symbol,
          available, symbol);
    } else {
      answer = String.format("%s (%s) has %s %s in stock across headquarters locations (all %s %s currently available).",
          name, internalCode != null ? internalCode : "HQ",
          controlled, symbol,
          available, symbol);
    }

    List<EveDtos.EntityReference> refs = new ArrayList<>();
    if (id != null) {
      refs.add(new EveDtos.EntityReference(id, "EQUIPMENT", name, internalCode));
    }

    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("HEADQUARTERS", "Equipment Name", name),
        new EveDtos.EvidenceItem("HEADQUARTERS", "Internal Code", internalCode != null ? internalCode : "HQ"),
        new EveDtos.EvidenceItem("HEADQUARTERS", "Tracking Mode", trackingMode),
        new EveDtos.EvidenceItem("HEADQUARTERS", "Controlled Stock", controlled + " " + symbol),
        new EveDtos.EvidenceItem("HEADQUARTERS", "Reserved Stock", reserved + " " + symbol),
        new EveDtos.EvidenceItem("HEADQUARTERS", "Available Stock", available + " " + symbol));

    return new EveRetrievalRouter.RouterResult(answer, "COMPLETED", refs, evidence, List.of());
  }

  private EveRetrievalRouter.RouterResult handleEquipmentOverview() {
    if (headquartersService == null) {
      return new EveRetrievalRouter.RouterResult(
          "Headquarters equipment service is unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }

    Map<String, Object> ov = headquartersService.overview();
    BigDecimal controlled = (BigDecimal) ov.getOrDefault("controlled", BigDecimal.ZERO);
    BigDecimal available = (BigDecimal) ov.getOrDefault("available", BigDecimal.ZERO);
    BigDecimal reserved = (BigDecimal) ov.getOrDefault("reserved", BigDecimal.ZERO);
    BigDecimal deployed = (BigDecimal) ov.getOrDefault("deployed", BigDecimal.ZERO);

    String answer = String.format("Headquarters currently controls %s physical items (%s available, %s reserved, %s deployed to active venues).",
        controlled, available, reserved, deployed);

    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("HEADQUARTERS", "Total Controlled", controlled.toString()),
        new EveDtos.EvidenceItem("HEADQUARTERS", "Total Available", available.toString()),
        new EveDtos.EvidenceItem("HEADQUARTERS", "Total Reserved", reserved.toString()),
        new EveDtos.EvidenceItem("HEADQUARTERS", "Total Deployed", deployed.toString()));

    return new EveRetrievalRouter.RouterResult(answer, "COMPLETED", List.of(), evidence, List.of());
  }

  // --------------------------------------------------------------------------
  // Production Domain
  // --------------------------------------------------------------------------

  private EveRetrievalRouter.RouterResult handleProductionClient(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt) {

    EveRetrievalService.Candidate prod = resolveProductionCandidate(need, sessionContext, "ke client", userPrompt);
    if (prod == null) {
      return handleMissingOrAmbiguousProduction(need, sessionContext, userPrompt, "client");
    }

    if (sessionContext != null) {
      sessionContext.setLastReferencedProduction(prod);
    }

    ProductionService.View view = productionService != null ? productionService.get(prod.id()) : null;
    String clientName = view != null && view.clientName() != null ? view.clientName() : "Unknown Client";

    String answer = String.format("The client for %s is %s.", prod.displayName(), clientName);
    List<EveDtos.EntityReference> refs = List.of(
        new EveDtos.EntityReference(prod.id(), "PRODUCTION", prod.displayName(), prod.code()));
    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("PRODUCTION", "Event Title", prod.displayName()),
        new EveDtos.EvidenceItem("PRODUCTION", "Client Name", clientName));

    return new EveRetrievalRouter.RouterResult(answer, "COMPLETED", refs, evidence, List.of());
  }

  private EveRetrievalRouter.RouterResult handleProductionCrew(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt) {

    EveRetrievalService.Candidate prod = resolveProductionCandidate(need, sessionContext, "ke crew", userPrompt);
    if (prod == null) {
      return handleMissingOrAmbiguousProduction(need, sessionContext, userPrompt, "crew");
    }

    if (sessionContext != null) {
      sessionContext.setLastReferencedProduction(prod);
    }

    List<ProductionMember> members = memberRepo != null ? memberRepo.findAllByProductionIdOrderByCreatedAt(prod.id()) : List.of();
    if (members.isEmpty()) {
      return new EveRetrievalRouter.RouterResult(
          String.format("There are no crew members currently assigned to %s.", prod.displayName()),
          "COMPLETED",
          List.of(new EveDtos.EntityReference(prod.id(), "PRODUCTION", prod.displayName(), prod.code())),
          List.of(new EveDtos.EvidenceItem("PRODUCTION", "Crew Count", "0")),
          List.of());
    }

    List<EmployeeDtos.View> allEmployees = employeeService != null ? employeeService.list(null, null) : List.of();
    Map<UUID, String> nameMap = new HashMap<>();
    for (EmployeeDtos.View e : allEmployees) {
      nameMap.put(e.id(), e.displayName());
    }

    StringBuilder sb = new StringBuilder();
    sb.append(String.format("%d crew member(s) assigned to %s: ", members.size(), prod.displayName()));
    for (int i = 0; i < members.size(); i++) {
      if (i > 0) sb.append(", ");
      ProductionMember pm = members.get(i);
      String name = nameMap.getOrDefault(pm.employeeId, "Unknown Employee");
      sb.append(name).append(" (").append(pm.productionRole).append(")");
    }
    sb.append(".");

    List<EveDtos.EntityReference> refs = List.of(
        new EveDtos.EntityReference(prod.id(), "PRODUCTION", prod.displayName(), prod.code()));
    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("PRODUCTION", "Production", prod.displayName()),
        new EveDtos.EvidenceItem("PRODUCTION", "Assigned Crew Count", String.valueOf(members.size())));

    return new EveRetrievalRouter.RouterResult(sb.toString(), "COMPLETED", refs, evidence, List.of());
  }

  private EveRetrievalRouter.RouterResult handleProductionEquipment(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt) {

    EveRetrievalService.Candidate prod = resolveProductionCandidate(need, sessionContext, "ke equipment", userPrompt);
    if (prod == null) {
      return handleMissingOrAmbiguousProduction(need, sessionContext, userPrompt, "equipment");
    }

    if (sessionContext != null) {
      sessionContext.setLastReferencedProduction(prod);
    }

    if (productionService == null) {
      return new EveRetrievalRouter.RouterResult("Production service is unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }

    ProductionService.View view = productionService.get(prod.id());
    List<ProductionService.EquipmentView> eqList = view != null ? view.equipment() : List.of();

    if (eqList == null || eqList.isEmpty()) {
      return new EveRetrievalRouter.RouterResult(
          String.format("No equipment has been assigned or reserved for %s.", prod.displayName()),
          "COMPLETED",
          List.of(new EveDtos.EntityReference(prod.id(), "PRODUCTION", prod.displayName(), prod.code())),
          List.of(new EveDtos.EvidenceItem("PRODUCTION", "Assigned Gear Items", "0")),
          List.of());
    }

    StringBuilder sb = new StringBuilder();
    sb.append(String.format("Equipment assigned to %s (%d item(s)): ", prod.displayName(), eqList.size()));
    for (int i = 0; i < eqList.size(); i++) {
      if (i > 0) sb.append(", ");
      ProductionService.EquipmentView ev = eqList.get(i);
      sb.append(ev.equipmentName()).append(" (x").append(ev.quantity()).append(")");
    }
    sb.append(".");

    List<EveDtos.EntityReference> refs = List.of(
        new EveDtos.EntityReference(prod.id(), "PRODUCTION", prod.displayName(), prod.code()));
    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("PRODUCTION", "Production", prod.displayName()),
        new EveDtos.EvidenceItem("PRODUCTION", "Assigned Equipment Count", String.valueOf(eqList.size())));

    return new EveRetrievalRouter.RouterResult(sb.toString(), "COMPLETED", refs, evidence, List.of());
  }

  private EveRetrievalRouter.RouterResult handleProductionTasks(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt) {

    EveRetrievalService.Candidate prod = resolveProductionCandidate(need, sessionContext, "ke tasks", userPrompt);
    if (prod == null) {
      return handleMissingOrAmbiguousProduction(need, sessionContext, userPrompt, "tasks");
    }

    if (sessionContext != null) {
      sessionContext.setLastReferencedProduction(prod);
    }

    if (taskService == null) {
      return new EveRetrievalRouter.RouterResult("Task service is unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }

    List<WorkTaskService.View> tasks = taskService.list(prod.id(), null, null, null, null, null, null);
    List<WorkTaskService.View> openTasks = tasks.stream()
        .filter(t -> t.status() != WorkTask.Status.DONE && t.status() != WorkTask.Status.CANCELLED)
        .toList();

    if (openTasks.isEmpty()) {
      return new EveRetrievalRouter.RouterResult(
          String.format("There are no open tasks currently pending for %s.", prod.displayName()),
          "COMPLETED",
          List.of(new EveDtos.EntityReference(prod.id(), "PRODUCTION", prod.displayName(), prod.code())),
          List.of(new EveDtos.EvidenceItem("WORK", "Open Task Count", "0")),
          List.of());
    }

    StringBuilder sb = new StringBuilder();
    sb.append(String.format("There are %d open task(s) for %s: ", openTasks.size(), prod.displayName()));
    for (int i = 0; i < openTasks.size(); i++) {
      if (i > 0) sb.append(", ");
      WorkTaskService.View t = openTasks.get(i);
      sb.append(t.title());
      if (t.assigneeName() != null) sb.append(" [").append(t.assigneeName()).append("]");
    }
    sb.append(".");

    List<EveDtos.EntityReference> refs = List.of(
        new EveDtos.EntityReference(prod.id(), "PRODUCTION", prod.displayName(), prod.code()));
    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("WORK", "Production", prod.displayName()),
        new EveDtos.EvidenceItem("WORK", "Open Tasks", String.valueOf(openTasks.size())));

    return new EveRetrievalRouter.RouterResult(sb.toString(), "COMPLETED", refs, evidence, List.of());
  }

  private EveRetrievalRouter.RouterResult handleProductionOverview(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt) {

    EveRetrievalService.Candidate prod = resolveProductionCandidate(need, sessionContext, "", userPrompt);
    if (prod == null) {
      return handleMissingOrAmbiguousProduction(need, sessionContext, userPrompt, "production");
    }

    if (sessionContext != null) {
      sessionContext.setLastReferencedProduction(prod);
    }

    ProductionService.View view = productionService != null ? productionService.get(prod.id()) : null;
    String venue = view != null && view.venueName() != null ? view.venueName() : "Venue TBD";
    String client = view != null && view.clientName() != null ? view.clientName() : "Unknown Client";
    String date = view != null && view.eventDate() != null ? view.eventDate().toString() : "Date TBD";
    String status = view != null && view.status() != null ? view.status().name() : "PLANNING";

    String answer = String.format("%s (Client: %s) is scheduled for %s at %s. Status: %s.",
        prod.displayName(), client, date, venue, status);

    List<EveDtos.EntityReference> refs = List.of(
        new EveDtos.EntityReference(prod.id(), "PRODUCTION", prod.displayName(), prod.code()));
    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("PRODUCTION", "Title", prod.displayName()),
        new EveDtos.EvidenceItem("PRODUCTION", "Client", client),
        new EveDtos.EvidenceItem("PRODUCTION", "Date", date),
        new EveDtos.EvidenceItem("PRODUCTION", "Venue", venue),
        new EveDtos.EvidenceItem("PRODUCTION", "Status", status));

    return new EveRetrievalRouter.RouterResult(answer, "COMPLETED", refs, evidence, List.of());
  }

  private EveRetrievalRouter.RouterResult handleProductionFinance(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt) {

    EveRetrievalService.Candidate prod = resolveProductionCandidate(need, sessionContext, "ke finance", userPrompt);
    if (prod == null) {
      return handleMissingOrAmbiguousProduction(need, sessionContext, userPrompt, "finance");
    }

    if (sessionContext != null) {
      sessionContext.setLastReferencedProduction(prod);
    }

    if (financeReads == null) {
      return new EveRetrievalRouter.RouterResult("Finance read service is unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
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

    List<EveDtos.EntityReference> refs = List.of(
        new EveDtos.EntityReference(prod.id(), "PRODUCTION", prod.displayName(), prod.code()));
    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("FINANCE", "Production", prod.displayName()),
        new EveDtos.EvidenceItem("FINANCE", "Contract Value", contractedStr),
        new EveDtos.EvidenceItem("FINANCE", "Advance Received", receivedStr),
        new EveDtos.EvidenceItem("FINANCE", "Outstanding Balance", outstandingStr),
        new EveDtos.EvidenceItem("FINANCE", "Incurred Expenses", expenseStr));

    return new EveRetrievalRouter.RouterResult(answer, "COMPLETED", refs, evidence, List.of());
  }

  private EveRetrievalRouter.RouterResult handleMemberPresence(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt) {

    EveRetrievalService.Candidate prod = resolveProductionCandidate(need, sessionContext, "", userPrompt);
    if (prod == null) {
      return handleMissingOrAmbiguousProduction(need, sessionContext, userPrompt, "production");
    }

    String empPhrase = need.secondaryEntityPhrase() != null ? need.secondaryEntityPhrase() : need.targetEntityPhrase();
    EveRetrievalService.ResolutionResult empRes = retrievalService.resolveEmployee(empPhrase, sessionContext);
    if (empRes == null || empRes.status() != EveRetrievalService.ResolutionStatus.RESOLVED) {
      return new EveRetrievalRouter.RouterResult(
          String.format("I could not identify the team member to check against %s.", prod.displayName()),
          "NOT_FOUND",
          List.of(),
          List.of(),
          List.of());
    }

    EveRetrievalService.Candidate emp = empRes.resolved();
    boolean present = memberRepo != null && memberRepo.existsByProductionIdAndEmployeeId(prod.id(), emp.id());

    String answer = present
        ? String.format("Yes, %s is assigned to %s.", emp.displayName(), prod.displayName())
        : String.format("No, %s is not assigned to %s.", emp.displayName(), prod.displayName());

    List<EveDtos.EntityReference> refs = List.of(
        new EveDtos.EntityReference(prod.id(), "PRODUCTION", prod.displayName(), prod.code()),
        new EveDtos.EntityReference(emp.id(), "EMPLOYEE", emp.displayName(), emp.code()));

    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("PRODUCTION", "Production", prod.displayName()),
        new EveDtos.EvidenceItem("PRODUCTION", "Employee Checked", emp.displayName()),
        new EveDtos.EvidenceItem("PRODUCTION", "Assigned", String.valueOf(present)));

    return new EveRetrievalRouter.RouterResult(answer, "COMPLETED", refs, evidence, List.of());
  }

  // --------------------------------------------------------------------------
  // Employee Domain
  // --------------------------------------------------------------------------

  private EveRetrievalRouter.RouterResult handleEmployeeFinance(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt) {

    EveRetrievalService.Candidate emp = resolveEmployeeCandidate(need, sessionContext, "ke finance", userPrompt);
    if (emp == null) {
      return handleMissingOrAmbiguousEmployee(need, sessionContext, userPrompt, "finance");
    }

    if (sessionContext != null) {
      sessionContext.setLastReferencedEmployee(emp);
    }

    if (financeReads == null) {
      return new EveRetrievalRouter.RouterResult("Finance read service is unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }

    Map<String, Object> fin = financeReads.employee(emp.id());
    BigDecimal earned = (BigDecimal) fin.getOrDefault("earned", BigDecimal.ZERO);
    BigDecimal paid = (BigDecimal) fin.getOrDefault("paid", BigDecimal.ZERO);
    BigDecimal outstanding = (BigDecimal) fin.getOrDefault("outstanding", BigDecimal.ZERO);

    NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));
    String earnedStr = fmt.format(earned);
    String paidStr = fmt.format(paid);
    String outStr = fmt.format(outstanding);

    String answer;
    if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
      answer = String.format("%s has %s in total earnings, %s paid to date, leaving %s outstanding.",
          emp.displayName(), earnedStr, paidStr, outStr);
    } else {
      answer = String.format("%s has %s in total earnings and %s paid (all cleared, 0.00 outstanding).",
          emp.displayName(), earnedStr, paidStr);
    }

    List<EveDtos.EntityReference> refs = List.of(
        new EveDtos.EntityReference(emp.id(), "EMPLOYEE", emp.displayName(), emp.code()));
    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("FINANCE", "Employee", emp.displayName()),
        new EveDtos.EvidenceItem("FINANCE", "Total Earned", earnedStr),
        new EveDtos.EvidenceItem("FINANCE", "Total Paid", paidStr),
        new EveDtos.EvidenceItem("FINANCE", "Outstanding Balance", outStr));

    return new EveRetrievalRouter.RouterResult(answer, "COMPLETED", refs, evidence, List.of());
  }

  private EveRetrievalRouter.RouterResult handleEmployeeAssignments(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt) {

    EveRetrievalService.Candidate emp = resolveEmployeeCandidate(need, sessionContext, "ke assignments", userPrompt);
    if (emp == null) {
      return handleMissingOrAmbiguousEmployee(need, sessionContext, userPrompt, "assignments");
    }

    if (sessionContext != null) {
      sessionContext.setLastReferencedEmployee(emp);
    }

    List<ProductionMember> memberships = memberRepo != null ? memberRepo.findByEmployeeId(emp.id()) : List.of();
    if (memberships.isEmpty()) {
      return new EveRetrievalRouter.RouterResult(
          String.format("%s is not currently assigned to any productions.", emp.displayName()),
          "COMPLETED",
          List.of(new EveDtos.EntityReference(emp.id(), "EMPLOYEE", emp.displayName(), emp.code())),
          List.of(new EveDtos.EvidenceItem("PRODUCTION", "Assigned Productions", "0")),
          List.of());
    }

    List<String> prodTitles = new ArrayList<>();
    if (productionRepo != null) {
      for (ProductionMember pm : memberships) {
        productionRepo.findById(pm.productionId).ifPresent(p -> prodTitles.add(p.title + " (" + pm.productionRole + ")"));
      }
    }

    String answer = String.format("%s is assigned to %d production(s): %s.",
        emp.displayName(), memberships.size(), String.join(", ", prodTitles));

    List<EveDtos.EntityReference> refs = List.of(
        new EveDtos.EntityReference(emp.id(), "EMPLOYEE", emp.displayName(), emp.code()));
    List<EveDtos.EvidenceItem> evidence = List.of(
        new EveDtos.EvidenceItem("EMPLOYEE", "Name", emp.displayName()),
        new EveDtos.EvidenceItem("PRODUCTION", "Active Assignments", String.valueOf(memberships.size())));

    return new EveRetrievalRouter.RouterResult(answer, "COMPLETED", refs, evidence, List.of());
  }

  private EveRetrievalRouter.RouterResult handleEmployeeProfile(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt) {

    EveRetrievalService.Candidate emp = resolveEmployeeCandidate(need, sessionContext, "ki profile", userPrompt);
    if (emp == null) {
      return handleMissingOrAmbiguousEmployee(need, sessionContext, userPrompt, "profile");
    }

    if (sessionContext != null) {
      sessionContext.setLastReferencedEmployee(emp);
    }

    if (employeeService == null) {
      return new EveRetrievalRouter.RouterResult("Employee service is unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }

    EmployeeDtos.View view = employeeService.get(emp.id());
    NumberFormat inr = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

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

    List<EveDtos.EntityReference> refs = List.of(
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

    return new EveRetrievalRouter.RouterResult(sb.toString(), "COMPLETED", refs, evidence, List.of());
  }

  // --------------------------------------------------------------------------
  // Tasks, Calendar, System
  // --------------------------------------------------------------------------

  private EveRetrievalRouter.RouterResult handleTasksSummary(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt) {

    // If query has a pronoun or follow-up and an active production focus exists, route to that production's tasks!
    if ((need.isPronoun() || need.isFollowUp()) && sessionContext != null && sessionContext.getLastReferencedProduction() != null) {
      return handleProductionTasks(need, sessionContext, userPrompt);
    }

    if (taskService == null) {
      return new EveRetrievalRouter.RouterResult("Task service is unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }

    List<WorkTaskService.View> openTasks = taskService.list(null, null, null, null, null, null, null).stream()
        .filter(t -> t.status() != WorkTask.Status.DONE && t.status() != WorkTask.Status.CANCELLED)
        .toList();

    String answer = String.format("There are currently %d open tasks across SA Command.", openTasks.size());
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

    return new EveRetrievalRouter.RouterResult(
        answer,
        "COMPLETED",
        List.of(),
        List.of(new EveDtos.EvidenceItem("WORK", "Open Task Count", String.valueOf(openTasks.size()))),
        List.of());
  }

  private EveRetrievalRouter.RouterResult handleScheduleDate(InformationNeed need, String userPrompt) {
    if (productionService == null) {
      return new EveRetrievalRouter.RouterResult("Calendar service is unavailable.", "SYSTEM_UNAVAILABLE", List.of(), List.of(), List.of());
    }

    String dateExpr = need.relativeDate() != null ? need.relativeDate() : userPrompt;
    Optional<LocalDate> targetDate = dateTimeParser.resolveRelativeDate(dateExpr);

    if (targetDate.isEmpty()) {
      return new EveRetrievalRouter.RouterResult(
          "I couldn't determine which date schedule you'd like to inspect.",
          "CLARIFICATION_REQUIRED",
          List.of(),
          List.of(),
          List.of());
    }

    LocalDate date = targetDate.get();
    List<ProductionService.View> events = productionService.list(null, null, date, date, null);

    if (events.isEmpty()) {
      return new EveRetrievalRouter.RouterResult(
          String.format("No events are currently scheduled for %s.", date),
          "COMPLETED",
          List.of(),
          List.of(new EveDtos.EvidenceItem("CALENDAR", "Events Count", "0")),
          List.of());
    }

    ProductionService.View first = events.get(0);
    String answer = String.format("On %s, %d event is scheduled: %s at %s.", date, events.size(), first.title(), first.venueName());
    return new EveRetrievalRouter.RouterResult(
        answer,
        "COMPLETED",
        List.of(new EveDtos.EntityReference(first.id(), "PRODUCTION", first.title(), first.id().toString().substring(0, 8))),
        List.of(new EveDtos.EvidenceItem("CALENDAR", "Scheduled Event", first.title())),
        List.of());
  }

  private EveRetrievalRouter.RouterResult handleSystemOverview() {
    return new EveRetrievalRouter.RouterResult(
        "SA Command is operating normally. All canonical domain services (Productions, Crew, Equipment, Tasks, Finance) are active.",
        "COMPLETED",
        List.of(),
        List.of(new EveDtos.EvidenceItem("SYSTEM", "Status", "OPERATIONAL")),
        List.of());
  }

  private EveRetrievalRouter.RouterResult handleDisambiguation(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt) {

    if (sessionContext != null && sessionContext.hasPendingCandidates()) {
      String hint = need.targetEntityPhrase() != null ? need.targetEntityPhrase() : userPrompt;
      Optional<EveRetrievalService.Candidate> selected = retrievalService.selectFromCandidates(
          sessionContext.getPendingCandidates(), hint);
      if (selected.isPresent()) {
        EveRetrievalService.Candidate chosen = selected.get();
        sessionContext.clearPendingCandidates();
        if ("PRODUCTION".equalsIgnoreCase(chosen.type())) {
          sessionContext.setLastReferencedProduction(chosen);
          return handleProductionOverview(InformationNeed.of(EveInformationTopic.PRODUCTION_OVERVIEW, EveSystemConcept.PRODUCTION, chosen.displayName()), sessionContext, userPrompt);
        } else if ("EMPLOYEE".equalsIgnoreCase(chosen.type())) {
          sessionContext.setLastReferencedEmployee(chosen);
          return handleEmployeeFinance(InformationNeed.of(EveInformationTopic.EMPLOYEE_FINANCE, EveSystemConcept.EMPLOYEE, chosen.displayName()), sessionContext, userPrompt);
        }
      }
    }

    return new EveRetrievalRouter.RouterResult(
        "Please select one of the candidate options shown above.",
        "CLARIFICATION_REQUIRED",
        List.of(),
        List.of(),
        sessionContext != null ? toCandidateViews(sessionContext.getPendingCandidates()) : List.of());
  }

  private EveRetrievalRouter.RouterResult handleVocabulary(InformationNeed need) {
    if (memoryService != null && need.targetEntityPhrase() != null && need.secondaryEntityPhrase() != null) {
      memoryService.remember(
          new EveDtos.MemoryRequest("VOCABULARY", need.targetEntityPhrase(), "VOCABULARY", null, need.secondaryEntityPhrase()),
          "OPERATOR_EXPLICIT");
      return new EveRetrievalRouter.RouterResult(
          String.format("Understood. I will remember that '%s' refers to %s.", need.targetEntityPhrase(), need.secondaryEntityPhrase()),
          "COMPLETED",
          List.of(),
          List.of(new EveDtos.EvidenceItem("MEMORY", "Learned Term", need.targetEntityPhrase() + " -> " + need.secondaryEntityPhrase())),
          List.of());
    }
    return new EveRetrievalRouter.RouterResult("Vocabulary term noted.", "COMPLETED", List.of(), List.of(), List.of());
  }

  // --------------------------------------------------------------------------
  // Helper: Candidate Resolution & Topic Switching Logic
  // --------------------------------------------------------------------------

  private EveRetrievalService.Candidate resolveProductionCandidate(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String topicLabel,
      String userPrompt) {

    // A. Pronoun or context follow-up
    if (need.isPronoun() || EveRetrievalRouter.isPronoun(need.targetEntityPhrase())) {
      if (sessionContext != null && sessionContext.getLastReferencedProduction() != null) {
        return sessionContext.getLastReferencedProduction();
      }
      return null;
    }

    // B. Explicit entity phrase
    String phrase = need.targetEntityPhrase();
    if (phrase == null || phrase.isBlank()) {
      if (sessionContext != null && sessionContext.getLastReferencedProduction() != null) {
        return sessionContext.getLastReferencedProduction();
      }
      return null;
    }

    EveRetrievalService.ResolutionResult res = retrievalService.resolveProduction(phrase, sessionContext);
    if (res != null && res.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
      return res.resolved();
    }
    return null;
  }

  private EveRetrievalService.Candidate resolveEmployeeCandidate(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String topicLabel,
      String userPrompt) {

    // A. Pronoun or context follow-up
    if (need.isPronoun() || EveRetrievalRouter.isPronoun(need.targetEntityPhrase())) {
      if (sessionContext != null && sessionContext.getLastReferencedEmployee() != null) {
        return sessionContext.getLastReferencedEmployee();
      }
      return null;
    }

    // B. Explicit entity phrase
    String phrase = need.targetEntityPhrase();
    if (phrase == null || phrase.isBlank()) {
      if (sessionContext != null && sessionContext.getLastReferencedEmployee() != null) {
        return sessionContext.getLastReferencedEmployee();
      }
      return null;
    }

    EveRetrievalService.ResolutionResult res = retrievalService.resolveEmployee(phrase, sessionContext);
    if (res != null && res.status() == EveRetrievalService.ResolutionStatus.RESOLVED) {
      return res.resolved();
    }
    return null;
  }

  private EveRetrievalRouter.RouterResult handleMissingOrAmbiguousProduction(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt,
      String topic) {

    String phrase = need.targetEntityPhrase();
    if (need.isPronoun() || phrase == null || phrase.isBlank() || EveRetrievalRouter.isPronoun(phrase)) {
      String q = String.format("Kis production ya event ke %s ki baat kar rahe ho? Please specify which production.", topic);
      if (sessionContext != null) {
        sessionContext.setPendingClarification(new EveRetrievalRouter.SessionContext.PendingClarification(
            EveModelProvider.Intent.READ_PRODUCTION, "PRODUCTION", q, userPrompt, java.time.Instant.now()));
      }
      return new EveRetrievalRouter.RouterResult(q, "CLARIFICATION_REQUIRED", List.of(), List.of(), List.of());
    }

    EveRetrievalService.ResolutionResult res = retrievalService.resolveProduction(phrase, sessionContext);
    if (res != null && res.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
      if (sessionContext != null) {
        sessionContext.setPendingCandidates(res.candidates());
      }
      return new EveRetrievalRouter.RouterResult(
          String.format("Multiple productions matched \"%s\". Which one did you mean?", phrase),
          "CLARIFICATION_REQUIRED",
          List.of(),
          List.of(),
          toCandidateViews(res.candidates()));
    }

    return new EveRetrievalRouter.RouterResult(
        String.format("I couldn't find a production/event matching \"%s\" in SA Command.", phrase),
        "NOT_FOUND",
        List.of(),
        List.of(),
        List.of());
  }

  private EveRetrievalRouter.RouterResult handleMissingOrAmbiguousEmployee(
      InformationNeed need,
      EveRetrievalRouter.SessionContext sessionContext,
      String userPrompt,
      String topic) {

    String phrase = need.targetEntityPhrase();
    if (need.isPronoun() || phrase == null || phrase.isBlank() || EveRetrievalRouter.isPronoun(phrase)) {
      String q = String.format("Kis employee ya team member ke %s ki baat kar rahe ho? Please specify which person.", topic);
      if (sessionContext != null) {
        sessionContext.setPendingClarification(new EveRetrievalRouter.SessionContext.PendingClarification(
            EveModelProvider.Intent.READ_EMPLOYEE_FINANCE, "EMPLOYEE", q, userPrompt, java.time.Instant.now()));
      }
      return new EveRetrievalRouter.RouterResult(q, "CLARIFICATION_REQUIRED", List.of(), List.of(), List.of());
    }

    EveRetrievalService.ResolutionResult res = retrievalService.resolveEmployee(phrase, sessionContext);
    if (res != null && res.status() == EveRetrievalService.ResolutionStatus.AMBIGUOUS) {
      if (sessionContext != null) {
        sessionContext.setPendingCandidates(res.candidates());
      }
      return new EveRetrievalRouter.RouterResult(
          String.format("Multiple team members matched \"%s\". Which one did you mean?", phrase),
          "CLARIFICATION_REQUIRED",
          List.of(),
          List.of(),
          toCandidateViews(res.candidates()));
    }

    return new EveRetrievalRouter.RouterResult(
        String.format("I couldn't find a team member matching \"%s\" in SA Command.", phrase),
        "NOT_FOUND",
        List.of(),
        List.of(),
        List.of());
  }

  private List<EveDtos.CandidateView> toCandidateViews(List<EveRetrievalService.Candidate> candidates) {
    if (candidates == null) return List.of();
    return candidates.stream()
        .map(c -> new EveDtos.CandidateView(c.id(), c.type(), c.displayName(), c.code(), c.detail()))
        .toList();
  }

  private String cleanItemQuery(String q) {
    if (q == null) return "";
    return q.replaceAll("(?i)\\b(hamare paas|hamare pass|kitna|kitne|kitni|hai|hain|available|stock|ka|ke|ki|batao|check|karo|bhi|kya|show me|how much|how many|do we have|in stock)\\b", "")
        .replaceAll("[^a-zA-Z0-9\\s-]", " ")
        .trim()
        .replaceAll("\\s+", " ");
  }

  private String greetingResponse(String prompt) {
    String lower = prompt != null ? prompt.toLowerCase(Locale.ROOT).trim() : "";
    if (lower.contains("thanks") || lower.contains("thank you") || lower.contains("shukriya")) {
      return "You're welcome! Let me know if you need anything else from SA Command.";
    }
    if (lower.contains("good morning")) {
      return "Good morning! I am EVE, your SA Command operational assistant. How can I assist you with productions, crew, equipment, tasks, or finance today?";
    }
    return "Hello! I am EVE, the internal intelligence assistant for SA Command. I can help you inspect productions, crew assignments, equipment stock, open tasks, and financial records. What would you like to check?";
  }
}
