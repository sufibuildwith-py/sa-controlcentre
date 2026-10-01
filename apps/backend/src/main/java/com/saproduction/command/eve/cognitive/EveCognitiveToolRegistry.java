package com.saproduction.command.eve.cognitive;

import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeDtos;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.eve.EveDtos;
import com.saproduction.command.eve.EveRetrievalService;
import com.saproduction.command.finance.FinanceReadService;
import com.saproduction.command.headquarters.HeadquartersService;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionMember;
import com.saproduction.command.production.ProductionMemberRepository;
import com.saproduction.command.production.ProductionRepository;
import com.saproduction.command.work.WorkTask;
import com.saproduction.command.work.WorkTaskRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Finite, bounded registry of read-only cognitive tools.
 * All tools strictly query authoritative PostgreSQL repositories and canonical domain services.
 * Arbitrary tool execution is prevented through strict allowlisting.
 */
@Component
public class EveCognitiveToolRegistry {

  private static final Logger log = LoggerFactory.getLogger(EveCognitiveToolRegistry.class);
  private final Map<String, EveCognitiveTool> tools = new LinkedHashMap<>();

  private final ProductionRepository productionRepo;
  private final ProductionMemberRepository productionMemberRepo;
  private final WorkTaskRepository workTaskRepo;
  private final EmployeeRepository employeeRepo;
  private final EmployeeService employeeService;
  private final FinanceReadService financeReadService;
  private final HeadquartersService headquartersService;
  private final EveRetrievalService retrievalService;
  private final EveTemporalReasoningService temporalService;

  @Autowired
  public EveCognitiveToolRegistry(
      ProductionRepository productionRepo,
      ProductionMemberRepository productionMemberRepo,
      WorkTaskRepository workTaskRepo,
      EmployeeRepository employeeRepo,
      EmployeeService employeeService,
      FinanceReadService financeReadService,
      @Autowired(required = false) HeadquartersService headquartersService,
      EveRetrievalService retrievalService,
      EveTemporalReasoningService temporalService) {
    this.productionRepo = productionRepo;
    this.productionMemberRepo = productionMemberRepo;
    this.workTaskRepo = workTaskRepo;
    this.employeeRepo = employeeRepo;
    this.employeeService = employeeService;
    this.financeReadService = financeReadService;
    this.headquartersService = headquartersService;
    this.retrievalService = retrievalService;
    this.temporalService = temporalService;

    registerTools();
  }

  public EveCognitiveToolRegistry() {
    this.productionRepo = null;
    this.productionMemberRepo = null;
    this.workTaskRepo = null;
    this.employeeRepo = null;
    this.employeeService = null;
    this.financeReadService = null;
    this.headquartersService = null;
    this.retrievalService = null;
    this.temporalService = null;
  }

  public Optional<EveCognitiveTool> getTool(String name) {
    if (name == null) return Optional.empty();
    return Optional.ofNullable(tools.get(name.trim().toLowerCase(Locale.ROOT)));
  }

  public Collection<EveCognitiveTool> getAllTools() {
    return Collections.unmodifiableCollection(tools.values());
  }

  public void register(EveCognitiveTool tool) {
    tools.put(tool.name().toLowerCase(Locale.ROOT), tool);
  }

  private void registerTools() {
    // 1. search_productions
    register(new EveCognitiveTool() {
      @Override
      public String name() { return "search_productions"; }

      @Override
      public String description() {
        return "Search production and event records by date range, title, or client. Returns matching productions.";
      }

      @Override
      public Map<String, String> parameterSchema() {
        return Map.of(
            "startDate", "ISO LocalDate (YYYY-MM-DD) or relative expression like 'next week'",
            "endDate", "ISO LocalDate (YYYY-MM-DD)",
            "query", "Optional title or client search phrase");
      }

      @Override
      public EveToolResult execute(Map<String, Object> params) {
        LocalDate start = parseDate(params.get("startDate"));
        LocalDate end = parseDate(params.get("endDate"));
        String query = params.get("query") != null ? params.get("query").toString().trim().toLowerCase(Locale.ROOT) : "";

        // If timeRange was passed directly as string
        if (start == null && params.containsKey("timeRange")) {
          var range = temporalService.resolveDateRange(params.get("timeRange").toString());
          if (range.isPresent()) {
            start = range.get().start();
            end = range.get().end();
          }
        }

        List<Production> all = productionRepo.findAll();
        List<Production> matched = new ArrayList<>();
        for (Production p : all) {
          if (p.status == Production.Status.CANCELLED) continue;
          if (start != null && p.eventDate != null && p.eventDate.isBefore(start)) continue;
          if (end != null && p.eventDate != null && p.eventDate.isAfter(end)) continue;
          if (!query.isBlank()) {
            boolean titleMatches = p.title != null && p.title.toLowerCase(Locale.ROOT).contains(query);
            boolean clientMatches = p.clientName != null && p.clientName.toLowerCase(Locale.ROOT).contains(query);
            if (!titleMatches && !clientMatches) continue;
          }
          matched.add(p);
        }

        matched.sort(Comparator.comparing((Production p) -> p.eventDate != null ? p.eventDate : LocalDate.MIN));

        List<EveDtos.EvidenceItem> evidence = new ArrayList<>();
        List<EveDtos.EntityReference> entities = new ArrayList<>();
        for (Production p : matched) {
          evidence.add(new EveDtos.EvidenceItem("PRODUCTION", p.title, p.eventDate + " @ " + p.venueName + " (" + p.status + ")"));
          entities.add(new EveDtos.EntityReference(p.id, "PRODUCTION", p.title, "PROD-" + p.title));
        }

        String summary = String.format("Found %d matching productions", matched.size());
        return EveToolResult.ok(summary, evidence, entities, matched);
      }
    });

    // 2. get_production
    register(new EveCognitiveTool() {
      @Override
      public String name() { return "get_production"; }

      @Override
      public String description() {
        return "Retrieve authoritative record of a specific production by title or ID.";
      }

      @Override
      public Map<String, String> parameterSchema() {
        return Map.of("title", "Production title or phrase", "id", "UUID of production");
      }

      @Override
      public EveToolResult execute(Map<String, Object> params) {
        Production found = resolveProduction(params);
        if (found == null) {
          return EveToolResult.fail("Production not found");
        }

        List<EveDtos.EvidenceItem> evidence = List.of(
            new EveDtos.EvidenceItem("PRODUCTION", "Title", found.title),
            new EveDtos.EvidenceItem("PRODUCTION", "Client", found.clientName),
            new EveDtos.EvidenceItem("PRODUCTION", "Date", found.eventDate != null ? found.eventDate.toString() : "TBD"),
            new EveDtos.EvidenceItem("PRODUCTION", "Venue", found.venueName + (found.venueAddress != null ? " (" + found.venueAddress + ")" : "")),
            new EveDtos.EvidenceItem("PRODUCTION", "Status", found.status.name()),
            new EveDtos.EvidenceItem("PRODUCTION", "Progress", found.progressPercent + "%")
        );
        List<EveDtos.EntityReference> entities = List.of(
            new EveDtos.EntityReference(found.id, "PRODUCTION", found.title, "PROD-" + found.title)
        );

        return EveToolResult.ok("Retrieved production: " + found.title, evidence, entities, found);
      }
    });

    // 3. get_production_crew
    register(new EveCognitiveTool() {
      @Override
      public String name() { return "get_production_crew"; }

      @Override
      public String description() {
        return "Retrieve crew members and technical staff assigned to a specific production.";
      }

      @Override
      public Map<String, String> parameterSchema() {
        return Map.of("productionTitle", "Production title", "productionId", "UUID of production");
      }

      @Override
      public EveToolResult execute(Map<String, Object> params) {
        Production prod = resolveProduction(params);
        if (prod == null) {
          return EveToolResult.fail("Production not found to inspect crew");
        }

        List<ProductionMember> members = productionMemberRepo.findAllByProductionIdOrderByCreatedAt(prod.id);
        List<EveDtos.EvidenceItem> evidence = new ArrayList<>();
        List<EveDtos.EntityReference> entities = new ArrayList<>();
        entities.add(new EveDtos.EntityReference(prod.id, "PRODUCTION", prod.title, "PROD-" + prod.title));

        List<Map<String, Object>> crewData = new ArrayList<>();
        for (ProductionMember m : members) {
          Optional<Employee> emp = employeeRepo.findById(m.employeeId);
          String name = emp.map(e -> e.displayName).orElse("Employee " + m.employeeId);
          String code = emp.map(e -> e.employeeCode).orElse("EMP");
          evidence.add(new EveDtos.EvidenceItem("PRODUCTION_CREW", name, m.productionRole + " (" + m.assignmentStatus + ")"));
          entities.add(new EveDtos.EntityReference(m.employeeId, "EMPLOYEE", name, code));

          crewData.add(Map.of(
              "employeeId", m.employeeId,
              "name", name,
              "code", code,
              "role", m.productionRole != null ? m.productionRole : "Crew",
              "status", m.assignmentStatus != null ? m.assignmentStatus.name() : "CONFIRMED"
          ));
        }

        String summary = String.format("Found %d crew members assigned to %s", members.size(), prod.title);
        return EveToolResult.ok(summary, evidence, entities, crewData);
      }
    });

    // 4. get_production_tasks
    register(new EveCognitiveTool() {
      @Override
      public String name() { return "get_production_tasks"; }

      @Override
      public String description() {
        return "Retrieve operational tasks, checklist items, and completion statuses for a production.";
      }

      @Override
      public Map<String, String> parameterSchema() {
        return Map.of("productionTitle", "Production title", "productionId", "UUID of production", "openOnly", "true/false");
      }

      @Override
      public EveToolResult execute(Map<String, Object> params) {
        Production prod = resolveProduction(params);
        if (prod == null) {
          return EveToolResult.fail("Production not found to inspect tasks");
        }

        boolean openOnly = "true".equalsIgnoreCase(String.valueOf(params.get("openOnly")));
        List<WorkTask> tasks = workTaskRepo.findAllByProductionId(prod.id);
        if (openOnly) {
          tasks = tasks.stream()
              .filter(t -> t.status != WorkTask.Status.DONE && t.status != WorkTask.Status.CANCELLED)
              .toList();
        }

        List<EveDtos.EvidenceItem> evidence = new ArrayList<>();
        List<Map<String, Object>> taskData = new ArrayList<>();
        for (WorkTask t : tasks) {
          String assigneeName = "Unassigned";
          if (t.assignedEmployeeId != null) {
            assigneeName = employeeRepo.findById(t.assignedEmployeeId).map(e -> e.displayName).orElse("Employee " + t.assignedEmployeeId);
          }
          evidence.add(new EveDtos.EvidenceItem("WORK_TASK", t.title, t.status + " | Assignee: " + assigneeName + " | Priority: " + t.priority));
          taskData.add(Map.of(
              "taskId", t.id,
              "title", t.title,
              "status", t.status.name(),
              "priority", t.priority.name(),
              "assignee", assigneeName
          ));
        }

        String summary = String.format("%d %stasks for %s", tasks.size(), openOnly ? "open " : "", prod.title);
        return EveToolResult.ok(summary, evidence, List.of(new EveDtos.EntityReference(prod.id, "PRODUCTION", prod.title, "PROD")), taskData);
      }
    });

    // 5. get_production_finance
    register(new EveCognitiveTool() {
      @Override
      public String name() { return "get_production_finance"; }

      @Override
      public String description() {
        return "Retrieve financial ledger records for a production: contracted amount, advance received, outstanding balance.";
      }

      @Override
      public Map<String, String> parameterSchema() {
        return Map.of("productionTitle", "Production title", "productionId", "UUID of production");
      }

      @Override
      public EveToolResult execute(Map<String, Object> params) {
        Production prod = resolveProduction(params);
        if (prod == null) {
          return EveToolResult.fail("Production not found to inspect finance");
        }

        Map<String, Object> fin = financeReadService.production(prod.id);
        BigDecimal contracted = (BigDecimal) fin.get("contracted");
        BigDecimal received = (BigDecimal) fin.get("received");
        BigDecimal outstanding = (BigDecimal) fin.get("outstanding");

        List<EveDtos.EvidenceItem> evidence = List.of(
            new EveDtos.EvidenceItem("FINANCE", "Contract", "INR " + (contracted != null ? contracted.toPlainString() : "0")),
            new EveDtos.EvidenceItem("FINANCE", "Advance Received", "INR " + (received != null ? received.toPlainString() : "0")),
            new EveDtos.EvidenceItem("FINANCE", "Outstanding Balance", "INR " + (outstanding != null ? outstanding.toPlainString() : "0"))
        );
        List<EveDtos.EntityReference> entities = List.of(
            new EveDtos.EntityReference(prod.id, "PRODUCTION", prod.title, "PROD-" + prod.title)
        );

        String summary = String.format("%s Finance: Contract INR %s, Received INR %s, Outstanding INR %s",
            prod.title, contracted, received, outstanding);
        return EveToolResult.ok(summary, evidence, entities, fin);
      }
    });

    // 6. search_production_receivables
    register(new EveCognitiveTool() {
      @Override
      public String name() { return "search_production_receivables"; }

      @Override
      public String description() {
        return "Find productions with outstanding unpaid balances (productions that owe us money).";
      }

      @Override
      public Map<String, String> parameterSchema() {
        return Map.of("minOutstanding", "Minimum outstanding balance (default 1)");
      }

      @Override
      public EveToolResult execute(Map<String, Object> params) {
        var page = financeReadService != null ? financeReadService.productions(0, 100, null) : null;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (page != null && page.get("items") != null)
            ? (List<Map<String, Object>>) page.get("items")
            : List.of();

        List<Map<String, Object>> owing = new ArrayList<>();
        List<EveDtos.EvidenceItem> evidence = new ArrayList<>();
        List<EveDtos.EntityReference> entities = new ArrayList<>();

        for (Map<String, Object> item : items) {
          BigDecimal contracted = (BigDecimal) item.get("contracted");
          BigDecimal received = (BigDecimal) item.get("received");
          if (contracted != null && received != null) {
            BigDecimal outstanding = contracted.subtract(received);
            if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
              String title = (String) item.get("title");
              UUID id = (UUID) item.get("id");
              owing.add(Map.of(
                  "id", id,
                  "title", title,
                  "contracted", contracted,
                  "received", received,
                  "outstanding", outstanding
              ));
              evidence.add(new EveDtos.EvidenceItem("FINANCE", title, "Outstanding: INR " + outstanding.toPlainString() + " (Contract: " + contracted + ", Received: " + received + ")"));
              entities.add(new EveDtos.EntityReference(id, "PRODUCTION", title, "PROD"));
            }
          }
        }

        String summary = String.format("Found %d productions with outstanding balances", owing.size());
        return EveToolResult.ok(summary, evidence, entities, owing);
      }
    });

    // 7. search_employees
    register(new EveCognitiveTool() {
      @Override
      public String name() { return "search_employees"; }

      @Override
      public String description() {
        return "Search employee profiles by name, role, department, or active status.";
      }

      @Override
      public Map<String, String> parameterSchema() {
        return Map.of("query", "Employee name or token", "department", "Department filter");
      }

      @Override
      public EveToolResult execute(Map<String, Object> params) {
        String q = params.get("query") != null ? params.get("query").toString().trim() : null;

        List<EmployeeDtos.View> employees = employeeService.list(q, null);
        List<EveDtos.EvidenceItem> evidence = new ArrayList<>();
        List<EveDtos.EntityReference> entities = new ArrayList<>();

        for (EmployeeDtos.View e : employees) {
          evidence.add(new EveDtos.EvidenceItem("EMPLOYEE", e.displayName(), e.roleTitle() + " | " + e.department() + " | " + e.employmentType()));
          entities.add(new EveDtos.EntityReference(e.id(), "EMPLOYEE", e.displayName(), e.employeeCode()));
        }

        String summary = String.format("Found %d matching employees", employees.size());
        return EveToolResult.ok(summary, evidence, entities, employees);
      }
    });

    // 8. get_employee_360
    register(new EveCognitiveTool() {
      @Override
      public String name() { return "get_employee_360"; }

      @Override
      public String description() {
        return "Retrieve complete profile of an employee: contact details, role, department, joining date, and salary.";
      }

      @Override
      public Map<String, String> parameterSchema() {
        return Map.of("name", "Employee name or token", "employeeId", "UUID of employee");
      }

      @Override
      public EveToolResult execute(Map<String, Object> params) {
        Employee emp = resolveEmployee(params);
        if (emp == null) {
          return EveToolResult.fail("Employee not found");
        }

        List<EveDtos.EvidenceItem> evidence = List.of(
            new EveDtos.EvidenceItem("EMPLOYEE", "Name", emp.displayName),
            new EveDtos.EvidenceItem("EMPLOYEE", "Code", emp.employeeCode),
            new EveDtos.EvidenceItem("EMPLOYEE", "Role", emp.roleTitle != null ? emp.roleTitle : "Staff"),
            new EveDtos.EvidenceItem("EMPLOYEE", "Department", emp.department != null ? emp.department : "Operations"),
            new EveDtos.EvidenceItem("EMPLOYEE", "Employment Type", emp.employmentType != null ? emp.employmentType : "FULL_TIME"),
            new EveDtos.EvidenceItem("EMPLOYEE", "Status", emp.status != null ? emp.status.name() : "ACTIVE"),
            new EveDtos.EvidenceItem("EMPLOYEE", "Salary", "INR " + (emp.baseSalaryMinor / 100)),
            new EveDtos.EvidenceItem("EMPLOYEE", "Phone", emp.phone != null ? emp.phone : "None")
        );
        List<EveDtos.EntityReference> entities = List.of(
            new EveDtos.EntityReference(emp.id, "EMPLOYEE", emp.displayName, emp.employeeCode)
        );

        return EveToolResult.ok("Retrieved employee profile: " + emp.displayName, evidence, entities, emp);
      }
    });

    // 9. get_employee_finance
    register(new EveCognitiveTool() {
      @Override
      public String name() { return "get_employee_finance"; }

      @Override
      public String description() {
        return "Retrieve financial position for an employee: earned obligations, payment history, and outstanding net balance.";
      }

      @Override
      public Map<String, String> parameterSchema() {
        return Map.of("name", "Employee name or token", "employeeId", "UUID of employee");
      }

      @Override
      public EveToolResult execute(Map<String, Object> params) {
        Employee emp = resolveEmployee(params);
        if (emp == null) {
          return EveToolResult.fail("Employee not found to inspect finance");
        }

        Map<String, Object> fin = financeReadService.employee(emp.id);
        BigDecimal earned = (BigDecimal) fin.get("earned");
        BigDecimal paid = (BigDecimal) fin.get("paid");
        BigDecimal outstanding = (BigDecimal) fin.get("outstanding");

        List<EveDtos.EvidenceItem> evidence = List.of(
            new EveDtos.EvidenceItem("FINANCE", "Earned", "INR " + (earned != null ? earned.toPlainString() : "0")),
            new EveDtos.EvidenceItem("FINANCE", "Paid", "INR " + (paid != null ? paid.toPlainString() : "0")),
            new EveDtos.EvidenceItem("FINANCE", "Outstanding Balance", "INR " + (outstanding != null ? outstanding.toPlainString() : "0"))
        );
        List<EveDtos.EntityReference> entities = List.of(
            new EveDtos.EntityReference(emp.id, "EMPLOYEE", emp.displayName, emp.employeeCode)
        );

        String summary = String.format("%s Financial Balance: Earned INR %s, Paid INR %s, Outstanding INR %s",
            emp.displayName, earned, paid, outstanding);
        return EveToolResult.ok(summary, evidence, entities, fin);
      }
    });

    // 10. get_employee_assignments
    register(new EveCognitiveTool() {
      @Override
      public String name() { return "get_employee_assignments"; }

      @Override
      public String description() {
        return "Retrieve the list of productions and events to which an employee is assigned.";
      }

      @Override
      public Map<String, String> parameterSchema() {
        return Map.of("name", "Employee name", "employeeId", "UUID of employee");
      }

      @Override
      public EveToolResult execute(Map<String, Object> params) {
        Employee emp = resolveEmployee(params);
        if (emp == null) {
          return EveToolResult.fail("Employee not found to check assignments");
        }

        List<ProductionMember> assignments = productionMemberRepo.findByEmployeeId(emp.id);
        List<EveDtos.EvidenceItem> evidence = new ArrayList<>();
        List<EveDtos.EntityReference> entities = new ArrayList<>();
        entities.add(new EveDtos.EntityReference(emp.id, "EMPLOYEE", emp.displayName, emp.employeeCode));

        List<Map<String, Object>> prods = new ArrayList<>();
        for (ProductionMember m : assignments) {
          Optional<Production> prodOpt = productionRepo.findById(m.productionId);
          if (prodOpt.isPresent()) {
            Production p = prodOpt.get();
            evidence.add(new EveDtos.EvidenceItem("PRODUCTION_ASSIGNMENT", p.title, p.eventDate + " | Role: " + m.productionRole + " (" + m.assignmentStatus + ")"));
            entities.add(new EveDtos.EntityReference(p.id, "PRODUCTION", p.title, "PROD"));
            prods.add(Map.of(
                "productionId", p.id,
                "title", p.title,
                "date", p.eventDate != null ? p.eventDate.toString() : "",
                "role", m.productionRole,
                "status", m.assignmentStatus.name()
            ));
          }
        }

        String summary = String.format("Employee %s is assigned to %d productions", emp.displayName, assignments.size());
        return EveToolResult.ok(summary, evidence, entities, prods);
      }
    });

    // 11. search_tasks
    register(new EveCognitiveTool() {
      @Override
      public String name() { return "search_tasks"; }

      @Override
      public String description() {
        return "Search operational tasks across the organization by status or priority.";
      }

      @Override
      public Map<String, String> parameterSchema() {
        return Map.of("openOnly", "true/false");
      }

      @Override
      public EveToolResult execute(Map<String, Object> params) {
        boolean openOnly = !"false".equalsIgnoreCase(String.valueOf(params.get("openOnly")));
        List<WorkTask> tasks = workTaskRepo.findAll();
        if (openOnly) {
          tasks = tasks.stream()
              .filter(t -> t.status != WorkTask.Status.DONE && t.status != WorkTask.Status.CANCELLED)
              .toList();
        }

        List<EveDtos.EvidenceItem> evidence = new ArrayList<>();
        for (WorkTask t : tasks) {
          evidence.add(new EveDtos.EvidenceItem("WORK_TASK", t.title, t.status + " | Priority: " + t.priority));
        }

        String summary = String.format("Found %d %stasks across the system", tasks.size(), openOnly ? "open " : "");
        return EveToolResult.ok(summary, evidence, List.of(), tasks);
      }
    });

    // 12. get_equipment_stock
    register(new EveCognitiveTool() {
      @Override
      public String name() { return "get_equipment_stock"; }

      @Override
      public String description() {
        return "Retrieve physical equipment inventory counts and usable stock in Headquarters.";
      }

      @Override
      public Map<String, String> parameterSchema() {
        return Map.of("query", "Item or equipment name");
      }

      @Override
      public EveToolResult execute(Map<String, Object> params) {
        if (headquartersService == null) {
          return EveToolResult.fail("Headquarters service unavailable");
        }
        String q = params.get("query") != null ? params.get("query").toString().trim() : "";
        Map<String, Object> page = headquartersService.equipment(0, 20, q, null, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");

        List<EveDtos.EvidenceItem> evidence = new ArrayList<>();
        List<EveDtos.EntityReference> entities = new ArrayList<>();
        if (items != null) {
          for (Map<String, Object> item : items) {
            String name = (String) item.get("name");
            Object total = item.get("totalControlledQuantity");
            Object usable = item.get("usableQuantity");
            Object available = item.get("availableQuantity");
            UUID id = (UUID) item.get("id");
            evidence.add(new EveDtos.EvidenceItem("HEADQUARTERS", name, "Total: " + total + ", Usable: " + usable + ", Available: " + available));
            if (id != null) {
              entities.add(new EveDtos.EntityReference(id, "EQUIPMENT", name, "EQ"));
            }
          }
        }

        String summary = String.format("Found %d equipment records matching '%s'", items != null ? items.size() : 0, q);
        return EveToolResult.ok(summary, evidence, entities, items);
      }
    });
  }

  private LocalDate parseDate(Object val) {
    if (val == null) return null;
    String str = val.toString().trim();
    if (str.isBlank()) return null;
    try {
      return LocalDate.parse(str, DateTimeFormatter.ISO_LOCAL_DATE);
    } catch (Exception ignored) {}
    // Try temporal reasoning service
    return temporalService.resolveDate(str).orElse(null);
  }

  private Production resolveProduction(Map<String, Object> params) {
    Object idObj = params.get("id");
    if (idObj == null) idObj = params.get("productionId");
    if (idObj != null) {
      try {
        UUID id = (idObj instanceof UUID u) ? u : UUID.fromString(idObj.toString().trim());
        return productionRepo != null ? productionRepo.findById(id).orElse(null) : null;
      } catch (Exception ignored) {}
    }
    String title = "";
    if (params.containsKey("title") && params.get("title") != null) {
      title = params.get("title").toString();
    } else if (params.containsKey("productionTitle") && params.get("productionTitle") != null) {
      title = params.get("productionTitle").toString();
    }
    if (title.isBlank()) return null;

    var res = retrievalService.resolveProduction(title);
    if (res.resolved() != null) {
      return productionRepo.findById(res.resolved().id()).orElse(null);
    }
    return null;
  }

  private Employee resolveEmployee(Map<String, Object> params) {
    if (params.containsKey("employeeId") && params.get("employeeId") != null) {
      try {
        UUID id = UUID.fromString(params.get("employeeId").toString().trim());
        return employeeRepo.findById(id).orElse(null);
      } catch (Exception ignored) {}
    }
    String name = "";
    if (params.containsKey("name") && params.get("name") != null) {
      name = params.get("name").toString();
    } else if (params.containsKey("employeeName") && params.get("employeeName") != null) {
      name = params.get("employeeName").toString();
    }
    if (name.isBlank()) return null;

    var res = retrievalService.resolveEmployee(name);
    if (res.resolved() != null) {
      return employeeRepo.findById(res.resolved().id()).orElse(null);
    }
    return null;
  }
}
