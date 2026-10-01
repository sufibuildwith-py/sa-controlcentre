package com.saproduction.command.eve.cognitive;

import com.saproduction.command.eve.system.EveDomain;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Typed Bounded Capability Registry for EVE Cognitive Runtime 2.0 (Section 7).
 * Encapsulates all General, SA Command, and External capabilities.
 */
@Component
public class EveCapabilityRegistry {

  private final Map<String, EveCapabilityDefinition> capabilitiesById = new LinkedHashMap<>();
  private final Map<EveDomain, List<EveCapabilityDefinition>> capabilitiesByDomain = new EnumMap<>(EveDomain.class);

  public EveCapabilityRegistry() {
    registerAll();
  }

  private void registerAll() {
    // 1. GENERAL CAPABILITIES
    register(EveCapabilityDefinition.general(
        "general.arithmetic",
        Set.of(EveOperation.CALCULATE),
        "EveArithmeticCapability",
        "Deterministic evaluation of mathematical expressions (e.g. 30-10+4-12, 17*23, 50000/4)"));

    register(EveCapabilityDefinition.general(
        "general.date_time",
        Set.of(EveOperation.CALCULATE, EveOperation.READ),
        "EveTemporalReasoningService",
        "Deterministic date/time and date-range resolution in Asia/Kolkata"));

    register(EveCapabilityDefinition.general(
        "general.recommendation",
        Set.of(EveOperation.RECOMMEND),
        "EveGeneralReasoningService",
        "General operational and workplace recommendations (e.g. lunch menu options, practical suggestions)"));

    register(EveCapabilityDefinition.general(
        "general.conversation",
        Set.of(EveOperation.GENERAL_CONVERSATION),
        "EveResponseComposer",
        "Natural conversational pleasantries, greetings, and system orientation"));

    // 2. SA COMMAND CANONICAL CAPABILITIES
    register(EveCapabilityDefinition.saCommand(
        "sa_command.employee_lookup",
        EveDomain.EMPLOYEE,
        Set.of(EveOperation.READ, EveOperation.LOOKUP, EveOperation.LIST),
        List.of("employeeName"),
        "EmployeeService",
        "Authoritative 360 profile, role, department, and contact information of employees"));

    register(EveCapabilityDefinition.saCommand(
        "sa_command.employee_finance",
        EveDomain.FINANCE,
        Set.of(EveOperation.READ, EveOperation.CALCULATE),
        List.of("employeeName"),
        "FinanceReadService",
        "Authoritative financial balance, earned obligations, and disbursements for employees"));

    register(EveCapabilityDefinition.saCommand(
        "sa_command.production_lookup",
        EveDomain.PRODUCTION,
        Set.of(EveOperation.READ, EveOperation.LOOKUP, EveOperation.LIST),
        List.of("productionName"),
        "ProductionService",
        "Authoritative overview, client details, venue, and status of productions"));

    register(EveCapabilityDefinition.saCommand(
        "sa_command.production_crew",
        EveDomain.PRODUCTION,
        Set.of(EveOperation.READ, EveOperation.COUNT, EveOperation.FILTER),
        List.of("productionName"),
        "ProductionMemberRepository",
        "Assigned crew members and technical staff on a production"));

    register(EveCapabilityDefinition.saCommand(
        "sa_command.production_tasks",
        EveDomain.WORK_TASK,
        Set.of(EveOperation.READ, EveOperation.COUNT, EveOperation.COMPARE, EveOperation.RANK),
        List.of("productionName"),
        "WorkTaskRepository",
        "Work tasks and checklists on productions, including pending task comparisons and ranking"));

    register(EveCapabilityDefinition.saCommand(
        "sa_command.production_equipment",
        EveDomain.PRODUCTION,
        Set.of(EveOperation.READ),
        List.of("productionName"),
        "ProductionService",
        "Equipment packages and gear assigned to a production"));

    register(EveCapabilityDefinition.saCommand(
        "sa_command.equipment_inventory",
        EveDomain.HEADQUARTERS_EQUIPMENT,
        Set.of(EveOperation.READ, EveOperation.COUNT),
        List.of("itemName"),
        "HeadquartersService",
        "Headquarters physical stock, usable inventory, and warehouse availability"));

    register(EveCapabilityDefinition.saCommand(
        "sa_command.finance_production",
        EveDomain.FINANCE,
        Set.of(EveOperation.READ, EveOperation.CALCULATE, EveOperation.DECISION_SUPPORT),
        List.of("productionName"),
        "FinanceReadService",
        "Production financial records: contract value, advance received, outstanding balance"));

    register(EveCapabilityDefinition.saCommand(
        "sa_command.scheduling",
        EveDomain.CALENDAR,
        Set.of(EveOperation.READ, EveOperation.COUNT, EveOperation.FILTER),
        List.of("dateRange"),
        "ProductionRepository",
        "Productions scheduled within a specified time range or date"));

    // 3. EXTERNAL CAPABILITIES
    register(EveCapabilityDefinition.external(
        "external.weather",
        Set.of(EveOperation.READ, EveOperation.DECISION_SUPPORT),
        "None (External Live Provider Unconfigured)",
        "Real-time live weather information for field shoots and outdoor productions"));
  }

  private void register(EveCapabilityDefinition cap) {
    capabilitiesById.put(cap.id(), cap);
    capabilitiesByDomain.computeIfAbsent(cap.domain(), k -> new ArrayList<>()).add(cap);
  }

  public Optional<EveCapabilityDefinition> findById(String id) {
    return Optional.ofNullable(capabilitiesById.get(id));
  }

  public List<EveCapabilityDefinition> getByDomain(EveDomain domain) {
    return capabilitiesByDomain.getOrDefault(domain, List.of());
  }

  public Optional<EveCapabilityDefinition> findBestCapability(EveDomain domain, EveOperation op) {
    List<EveCapabilityDefinition> domainCaps = capabilitiesByDomain.get(domain);
    if (domainCaps != null) {
      for (var cap : domainCaps) {
        if (cap.supportedOperations().contains(op)) {
          return Optional.of(cap);
        }
      }
    }
    return Optional.empty();
  }

  public Collection<EveCapabilityDefinition> getAll() {
    return Collections.unmodifiableCollection(capabilitiesById.values());
  }
}
