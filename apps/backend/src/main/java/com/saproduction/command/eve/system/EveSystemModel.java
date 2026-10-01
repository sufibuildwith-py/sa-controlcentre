package com.saproduction.command.eve.system;

import java.util.*;
import org.springframework.stereotype.Component;

/**
 * Machine-readable, authoritative System Model of SA Command for EVE.
 * Represents real domains, entity concepts, relationships, and bounded capabilities.
 *
 * Core Principles:
 * 1. Truth in Capability: Only capabilities backed by real SA Command repositories and services exist.
 * 2. Domain Isolation: A query about one domain cannot resolve to or execute against another domain.
 * 3. Bounded Retrieval: Every capability exposes its input criteria and output contracts.
 * 4. Grounded Operations: No hallucinated capabilities or shadow stores.
 */
@Component
public class EveSystemModel {

  private final Map<String, EveCapability> capabilitiesById = new LinkedHashMap<>();
  private final Map<EveInformationTopic, List<EveCapability>> capabilitiesByTopic = new EnumMap<>(EveInformationTopic.class);

  public EveSystemModel() {
    registerCapabilities();
  }

  private void registerCapabilities() {
    // 1. Headquarters / Equipment Domain
    register(new EveCapability(
        "headquarters.equipment_stock",
        EveDomain.HEADQUARTERS_EQUIPMENT,
        EveSystemConcept.EQUIPMENT,
        EveInformationTopic.EQUIPMENT_STOCK,
        "Retrieves physical inventory counts, usable stock, reserved stock, and available stock for equipment or consumable items in headquarters",
        EveCapability.SafetyClass.READ_ONLY));

    register(new EveCapability(
        "headquarters.equipment_overview",
        EveDomain.HEADQUARTERS_EQUIPMENT,
        EveSystemConcept.EQUIPMENT,
        EveInformationTopic.EQUIPMENT_OVERVIEW,
        "Retrieves fleet-wide overview of total controlled, usable, reserved, and deployed equipment across headquarters locations",
        EveCapability.SafetyClass.READ_ONLY));

    // 2. Production Domain
    register(new EveCapability(
        "production.client_info",
        EveDomain.PRODUCTION,
        EveSystemConcept.PRODUCTION,
        EveInformationTopic.CLIENT_INFO,
        "Retrieves client, customer, or commissioning company details for a specific production",
        EveCapability.SafetyClass.READ_ONLY));

    register(new EveCapability(
        "production.crew_members",
        EveDomain.PRODUCTION,
        EveSystemConcept.PRODUCTION,
        EveInformationTopic.CREW_MEMBERS,
        "Retrieves assigned crew members, technical staff, audio/lighting engineers, and attendance requirements for a production",
        EveCapability.SafetyClass.READ_ONLY));

    register(new EveCapability(
        "production.equipment_assigned",
        EveDomain.PRODUCTION,
        EveSystemConcept.PRODUCTION,
        EveInformationTopic.EQUIPMENT_ASSIGNED,
        "Retrieves specific gear packages, equipment reservations, and quantities assigned to a production",
        EveCapability.SafetyClass.READ_ONLY));

    register(new EveCapability(
        "production.tasks",
        EveDomain.PRODUCTION,
        EveSystemConcept.PRODUCTION,
        EveInformationTopic.PRODUCTION_TASKS,
        "Retrieves work tasks and checklist items belonging to a specific production",
        EveCapability.SafetyClass.READ_ONLY));

    register(new EveCapability(
        "production.overview",
        EveDomain.PRODUCTION,
        EveSystemConcept.PRODUCTION,
        EveInformationTopic.PRODUCTION_OVERVIEW,
        "Retrieves general production overview including title, date, venue, status, and timeline",
        EveCapability.SafetyClass.READ_ONLY));

    register(new EveCapability(
        "production.finance",
        EveDomain.FINANCE,
        EveSystemConcept.PRODUCTION,
        EveInformationTopic.PRODUCTION_FINANCE,
        "Retrieves production financial records: contracted amount, advance payments received, outstanding balance, and transaction history",
        EveCapability.SafetyClass.READ_ONLY));

    register(new EveCapability(
        "production.member_check",
        EveDomain.PRODUCTION,
        EveSystemConcept.PRODUCTION,
        EveInformationTopic.MEMBER_PRESENCE,
        "Checks whether a specific employee was assigned to or worked on a specific production",
        EveCapability.SafetyClass.READ_ONLY));

    // 3. Employee Domain
    register(new EveCapability(
        "employee.finance_position",
        EveDomain.FINANCE,
        EveSystemConcept.EMPLOYEE,
        EveInformationTopic.EMPLOYEE_FINANCE,
        "Retrieves financial position for an employee: earned obligations, payment history, and outstanding net balance",
        EveCapability.SafetyClass.READ_ONLY));

    register(new EveCapability(
        "employee.profile_360",
        EveDomain.EMPLOYEE,
        EveSystemConcept.EMPLOYEE,
        EveInformationTopic.EMPLOYEE_PROFILE,
        "Retrieves 360 profile of an employee: contact details, employment status, department, and role",
        EveCapability.SafetyClass.READ_ONLY));

    register(new EveCapability(
        "employee.assignments",
        EveDomain.EMPLOYEE,
        EveSystemConcept.EMPLOYEE,
        EveInformationTopic.EMPLOYEE_ASSIGNMENTS,
        "Retrieves list of productions and events to which an employee is assigned",
        EveCapability.SafetyClass.READ_ONLY));

    // 4. Work / Task Domain
    register(new EveCapability(
        "work.tasks_summary",
        EveDomain.WORK_TASK,
        EveSystemConcept.WORK_TASK,
        EveInformationTopic.TASK_SUMMARY,
        "Retrieves summary of open, pending, and in-progress operational tasks across the organization",
        EveCapability.SafetyClass.READ_ONLY));

    // 5. Calendar Domain
    register(new EveCapability(
        "calendar.schedule_date",
        EveDomain.CALENDAR,
        EveSystemConcept.CALENDAR_DATE,
        EveInformationTopic.SCHEDULE_DATE,
        "Retrieves calendar events and productions scheduled on a specific relative or absolute date",
        EveCapability.SafetyClass.READ_ONLY));

    // 6. Governed Write Execution (Phase 3)
    register(new EveCapability(
        "finance.payment_proposal",
        EveDomain.FINANCE,
        EveSystemConcept.EMPLOYEE,
        EveInformationTopic.PAYMENT_PROPOSAL,
        "Constructs a governed financial payment proposal plan requiring explicit human operator confirmation",
        EveCapability.SafetyClass.GOVERNED_WRITE_PROPOSAL));

    // 7. System Conversational Capabilities
    register(new EveCapability(
        "system.greeting",
        EveDomain.SYSTEM,
        EveSystemConcept.SYSTEM,
        EveInformationTopic.GREETING,
        "Handles conversational greetings, pleasantries, and system orientation",
        EveCapability.SafetyClass.READ_ONLY));

    register(new EveCapability(
        "system.disambiguation",
        EveDomain.SYSTEM,
        EveSystemConcept.SYSTEM,
        EveInformationTopic.DISAMBIGUATION_CHOICE,
        "Resolves disambiguation choices when multiple matching candidates were presented",
        EveCapability.SafetyClass.READ_ONLY));

    register(new EveCapability(
        "system.vocabulary",
        EveDomain.SYSTEM,
        EveSystemConcept.SYSTEM,
        EveInformationTopic.VOCABULARY_DEFINITION,
        "Stores learned operator vocabulary or alias terms in memory",
        EveCapability.SafetyClass.READ_ONLY));

    register(new EveCapability(
        "system.overview",
        EveDomain.SYSTEM,
        EveSystemConcept.SYSTEM,
        EveInformationTopic.SYSTEM_OVERVIEW,
        "Provides general system status, active production counts, and capability summary",
        EveCapability.SafetyClass.READ_ONLY));
  }

  private void register(EveCapability capability) {
    capabilitiesById.put(capability.id(), capability);
    capabilitiesByTopic.computeIfAbsent(capability.topic(), k -> new ArrayList<>()).add(capability);
  }

  public Optional<EveCapability> findById(String id) {
    return Optional.ofNullable(capabilitiesById.get(id));
  }

  public Optional<EveCapability> findCapability(EveInformationTopic topic, EveSystemConcept concept) {
    List<EveCapability> list = capabilitiesByTopic.get(topic);
    if (list == null || list.isEmpty()) {
      return Optional.empty();
    }
    if (concept != null) {
      for (EveCapability cap : list) {
        if (cap.primaryConcept() == concept) {
          return Optional.of(cap);
        }
      }
    }
    return Optional.of(list.get(0));
  }

  public List<EveCapability> getCapabilitiesForTopic(EveInformationTopic topic) {
    return capabilitiesByTopic.getOrDefault(topic, List.of());
  }

  public Collection<EveCapability> getAllCapabilities() {
    return Collections.unmodifiableCollection(capabilitiesById.values());
  }

  /**
   * Checks whether SA Command possesses authoritative capabilities for a requested topic.
   */
  public boolean isSupported(EveInformationTopic topic) {
    return topic != null && topic != EveInformationTopic.UNSUPPORTED && topic != EveInformationTopic.UNKNOWN && capabilitiesByTopic.containsKey(topic);
  }
}
