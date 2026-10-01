package com.saproduction.command.eve;

import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeDtos;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.employee.EmployeeService;
import com.saproduction.command.production.Production;
import com.saproduction.command.production.ProductionRepository;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Deterministic retrieval engine for EVE.
 * Enforces the authoritative canonical resolution order across all domains:
 * 1. Exact canonical identifier / UUID
 * 2. Exact canonical code or name (case-insensitive)
 * 3. Normalized exact name (case & whitespace insensitive)
 * 4. Bounded canonical DB search -> Canonical candidate set
 * 5. Memory as supporting evidence / disambiguation hint (strictly validated against canonical DB)
 * 6. Ambiguity preservation when canonical candidates cannot be uniquely disambiguated
 * 7. Model-assisted selection strictly constrained to retrieved candidate set (never invents IDs)
 */
@Service
public class EveRetrievalService {

  private final EmployeeRepository employeeRepo;
  private final EmployeeService employeeService;
  private final EveMemoryService memoryService;
  private final ProductionRepository productionRepo;
  private final com.saproduction.command.eve.semantic.EveSemanticResolutionService semanticService;

  public EveRetrievalService(
      EmployeeRepository employeeRepo,
      EmployeeService employeeService,
      EveMemoryService memoryService) {
    this(employeeRepo, employeeService, memoryService, null, null);
  }

  public EveRetrievalService(
      EmployeeRepository employeeRepo,
      EmployeeService employeeService,
      EveMemoryService memoryService,
      ProductionRepository productionRepo) {
    this(employeeRepo, employeeService, memoryService, productionRepo, null);
  }

  @Autowired
  public EveRetrievalService(
      EmployeeRepository employeeRepo,
      EmployeeService employeeService,
      @Autowired(required = false) EveMemoryService memoryService,
      @Autowired(required = false) ProductionRepository productionRepo,
      @Autowired(required = false) com.saproduction.command.eve.semantic.EveSemanticResolutionService semanticService) {
    this.employeeRepo = employeeRepo;
    this.employeeService = employeeService;
    this.memoryService = memoryService;
    this.productionRepo = productionRepo;
    this.semanticService = semanticService;
  }

  public enum MatchMethod {
    EXACT_ID,
    EXACT_CODE,
    EXACT_NAME,
    BOUNDED_SEARCH,
    MEMORY_HINT,
    MODEL_SELECTION,
    SEMANTIC_MATCH,
    SEMANTIC_RERANKED
  }

  public enum ResolutionStatus {
    RESOLVED,
    AMBIGUOUS,
    NOT_FOUND
  }

  public record ResolutionProvenance(
      String spokenValue,
      UUID canonicalId,
      String canonicalName,
      MatchMethod matchMethod,
      double confidence) {}

  public record Candidate(
      UUID id,
      String type,
      String displayName,
      String code,
      String detail) {}

  public record ResolutionResult(
      ResolutionStatus status,
      Candidate resolved,
      List<Candidate> candidates,
      ResolutionProvenance provenance) {

    public static ResolutionResult resolved(Candidate candidate, MatchMethod method, String spokenValue) {
      return new ResolutionResult(
          ResolutionStatus.RESOLVED,
          candidate,
          List.of(candidate),
          new ResolutionProvenance(spokenValue, candidate.id(), candidate.displayName(), method, 1.0));
    }

    public static ResolutionResult modelSelected(Candidate candidate, String spokenValue) {
      return new ResolutionResult(
          ResolutionStatus.RESOLVED,
          candidate,
          List.of(candidate),
          new ResolutionProvenance(spokenValue, candidate.id(), candidate.displayName(), MatchMethod.MODEL_SELECTION, 0.9));
    }

    public static ResolutionResult ambiguous(List<Candidate> candidates, String spokenValue) {
      return new ResolutionResult(
          ResolutionStatus.AMBIGUOUS,
          null,
          candidates,
          new ResolutionProvenance(spokenValue, null, null, MatchMethod.BOUNDED_SEARCH, 0.5));
    }

    public static ResolutionResult notFound(String spokenValue) {
      return new ResolutionResult(
          ResolutionStatus.NOT_FOUND,
          null,
          List.of(),
          new ResolutionProvenance(spokenValue, null, null, MatchMethod.BOUNDED_SEARCH, 0.0));
    }
  }

  public ResolutionResult resolveEmployee(String spokenValue) {
    return resolveEmployee(spokenValue, null);
  }

  public ResolutionResult resolveEmployee(String spokenValue, EveRetrievalRouter.SessionContext sessionContext) {
    if (spokenValue == null || spokenValue.isBlank() || EveRetrievalRouter.isPronoun(spokenValue)) {
      return ResolutionResult.notFound(spokenValue);
    }

    String trimmed = normalizeWhitespace(spokenValue.trim());

    // 1. Exact UUID (Canonical DB check)
    try {
      UUID id = UUID.fromString(trimmed);
      Optional<Employee> emp = employeeRepo.findById(id);
      if (emp.isPresent()) {
        return ResolutionResult.resolved(toCandidate(emp.get()), MatchMethod.EXACT_ID, trimmed);
      }
    } catch (IllegalArgumentException ignored) {
      // Not a UUID, proceed
    }

    // 2. Exact Employee Code (Canonical DB check, case-insensitive, e.g. "SA-01")
    Optional<Employee> byCode = employeeRepo.findByEmployeeCodeIgnoreCase(trimmed);
    if (byCode.isPresent()) {
      return ResolutionResult.resolved(toCandidate(byCode.get()), MatchMethod.EXACT_CODE, trimmed);
    }
    if (trimmed.matches("(?i)^[a-z]{2,5}-\\d{3,6}$")) {
      return ResolutionResult.notFound(trimmed);
    }

    // 3. Normalized Exact Display Name or First Name (Canonical DB lookup)
    List<EmployeeDtos.View> allActive = employeeService.list(null, null);
    List<EmployeeDtos.View> exactMatches = allActive.stream()
        .filter(e -> normalizeWhitespace(e.displayName()).equalsIgnoreCase(trimmed)
            || (e.firstName() != null && normalizeWhitespace(e.firstName()).equalsIgnoreCase(trimmed)))
        .toList();

    if (exactMatches.size() == 1) {
      return ResolutionResult.resolved(toCandidate(exactMatches.get(0)), MatchMethod.EXACT_NAME, trimmed);
    } else if (exactMatches.size() > 1) {
      return ResolutionResult.ambiguous(
          exactMatches.stream().map(this::toCandidate).toList(),
          trimmed);
    }

    // 3B. Semantic Resolution Stage (Vector similarity + Reranker)
    if (semanticService != null && semanticService.isEnabled()) {
      ResolutionResult semRes = semanticService.resolveEmployee(spokenValue, sessionContext);
      if (semRes.status() != ResolutionStatus.NOT_FOUND) {
        return semRes;
      }
    }

    // 4. Bounded Canonical DB Search -> Form Canonical Candidate Set
    List<EmployeeDtos.View> canonicalCandidates = employeeService.list(trimmed, null);

    if (!canonicalCandidates.isEmpty()) {
      // Case 4A: Exactly 1 canonical match found in DB search.
      // Canonical DB truth wins over any conflicting memory hint.
      if (canonicalCandidates.size() == 1) {
        return ResolutionResult.resolved(toCandidate(canonicalCandidates.get(0)), MatchMethod.BOUNDED_SEARCH, trimmed);
      }

      // Case 4B: Multiple canonical candidates found.
      // Memory may only serve as a supporting disambiguation hint among ONLY these canonical candidates.
      if (memoryService != null) {
        Optional<EveDtos.MemoryView> mem = memoryService.recall(trimmed);
        if (mem.isPresent() && "EMPLOYEE".equalsIgnoreCase(mem.get().canonicalType())) {
          EveDtos.MemoryView hint = mem.get();
          Optional<EmployeeDtos.View> matchingCandidate = canonicalCandidates.stream()
              .filter(c -> (hint.canonicalId() != null && c.id().equals(hint.canonicalId()))
                  || (hint.canonicalName() != null && c.displayName().equalsIgnoreCase(hint.canonicalName())))
              .findFirst();

          if (matchingCandidate.isPresent()) {
            Optional<Employee> verified = employeeRepo.findById(matchingCandidate.get().id());
            if (verified.isPresent() && verified.get().status != Employee.Status.INACTIVE) {
              return ResolutionResult.resolved(toCandidate(verified.get()), MatchMethod.MEMORY_HINT, trimmed);
            }
          }
        }
      }

      // If memory cannot disambiguate or memory points outside the canonical candidate set, keep AMBIGUOUS
      return ResolutionResult.ambiguous(
          canonicalCandidates.stream().map(this::toCandidate).toList(),
          trimmed);
    }

    // 4C. Bounded Canonical DB Token Search for Employee
    String[] tokens = trimmed.toLowerCase(Locale.ROOT).split("\\s+");
    Map<EmployeeDtos.View, Integer> empMatchCounts = new HashMap<>();
    for (String token : tokens) {
      String t = token.replaceAll("[^a-zA-Z0-9]", "");
      if (t.length() >= 3 && !isStopWord(t)) {
        for (EmployeeDtos.View e : allActive) {
          if (matchesTokenWord(e.displayName(), t)) {
            empMatchCounts.put(e, empMatchCounts.getOrDefault(e, 0) + 1);
          }
        }
      }
    }
    if (!empMatchCounts.isEmpty()) {
      int maxMatches = Collections.max(empMatchCounts.values());
      List<EmployeeDtos.View> bestMatches = empMatchCounts.entrySet().stream()
          .filter(e -> e.getValue() == maxMatches)
          .map(Map.Entry::getKey)
          .toList();
      if (bestMatches.size() == 1) {
        return ResolutionResult.resolved(toCandidate(bestMatches.get(0)), MatchMethod.BOUNDED_SEARCH, trimmed);
      }
      return ResolutionResult.ambiguous(
          bestMatches.stream().map(this::toCandidate).toList(),
          trimmed);
    }

    // 5. Canonical DB search yielded 0 matches for this spoken term.
    // Memory can suggest a candidate, BUT memory is strictly a hint:
    if (memoryService != null) {
      Optional<EveDtos.MemoryView> mem = memoryService.recall(trimmed);
      if (mem.isPresent() && "EMPLOYEE".equalsIgnoreCase(mem.get().canonicalType())) {
        EveDtos.MemoryView hint = mem.get();
        if (hint.canonicalId() != null) {
          Optional<Employee> verified = employeeRepo.findById(hint.canonicalId());
          if (verified.isPresent()) {
            Employee emp = verified.get();
            if (emp.status != Employee.Status.INACTIVE) {
              return ResolutionResult.resolved(toCandidate(emp), MatchMethod.MEMORY_HINT, trimmed);
            }
          }
        } else if (hint.canonicalName() != null) {
          List<EmployeeDtos.View> nameMatches = allActive.stream()
              .filter(e -> e.displayName().equalsIgnoreCase(hint.canonicalName()))
              .toList();
          if (nameMatches.size() == 1) {
            Optional<Employee> verified = employeeRepo.findById(nameMatches.get(0).id());
            if (verified.isPresent() && verified.get().status != Employee.Status.INACTIVE) {
              return ResolutionResult.resolved(toCandidate(verified.get()), MatchMethod.MEMORY_HINT, trimmed);
            }
          }
        }
      }
    }

    return ResolutionResult.notFound(trimmed);
  }

  public ResolutionResult resolveProduction(String spokenValue) {
    return resolveProduction(spokenValue, null);
  }

  public ResolutionResult resolveProduction(String spokenValue, EveRetrievalRouter.SessionContext sessionContext) {
    if (spokenValue == null || spokenValue.isBlank() || productionRepo == null || EveRetrievalRouter.isPronoun(spokenValue)) {
      return ResolutionResult.notFound(spokenValue);
    }

    String trimmed = normalizeWhitespace(spokenValue.trim());
    String lower = trimmed.toLowerCase(Locale.ROOT);

    // 1. Exact UUID
    try {
      UUID id = UUID.fromString(trimmed);
      Optional<Production> prod = productionRepo.findById(id);
      if (prod.isPresent()) {
        return ResolutionResult.resolved(toCandidate(prod.get()), MatchMethod.EXACT_ID, trimmed);
      }
    } catch (IllegalArgumentException ignored) {
      // Not a UUID
    }

    List<Production> allProds = productionRepo.findAll().stream()
        .filter(p -> p.status != Production.Status.CANCELLED)
        .toList();

    // 2. Exact Title Match
    List<Production> exactMatches = allProds.stream()
        .filter(p -> normalizeWhitespace(p.title).equalsIgnoreCase(trimmed))
        .toList();

    if (exactMatches.size() == 1) {
      return ResolutionResult.resolved(toCandidate(exactMatches.get(0)), MatchMethod.EXACT_NAME, trimmed);
    } else if (exactMatches.size() > 1) {
      return ResolutionResult.ambiguous(
          exactMatches.stream().map(this::toCandidate).toList(),
          trimmed);
    }

    // 2B. Semantic Resolution Stage (Vector similarity + Reranker)
    if (semanticService != null && semanticService.isEnabled()) {
      ResolutionResult semRes = semanticService.resolveProduction(spokenValue, sessionContext);
      if (semRes.status() != ResolutionStatus.NOT_FOUND) {
        return semRes;
      }
    }

    // 3. Bounded Canonical DB Search (title or client containing query)
    List<Production> canonicalCandidates = allProds.stream()
        .filter(p -> p.title.toLowerCase(Locale.ROOT).contains(lower)
            || (p.clientName != null && p.clientName.toLowerCase(Locale.ROOT).contains(lower)))
        .toList();

    if (!canonicalCandidates.isEmpty()) {
      if (canonicalCandidates.size() == 1) {
        return ResolutionResult.resolved(toCandidate(canonicalCandidates.get(0)), MatchMethod.BOUNDED_SEARCH, trimmed);
      }

      // Disambiguate using memory hint if available
      if (memoryService != null) {
        Optional<EveDtos.MemoryView> mem = memoryService.recall(trimmed);
        if (mem.isPresent() && "PRODUCTION".equalsIgnoreCase(mem.get().canonicalType())) {
          EveDtos.MemoryView hint = mem.get();
          Optional<Production> matching = canonicalCandidates.stream()
              .filter(p -> (hint.canonicalId() != null && p.id.equals(hint.canonicalId()))
                  || (hint.canonicalName() != null && p.title.equalsIgnoreCase(hint.canonicalName())))
              .findFirst();

          if (matching.isPresent()) {
            return ResolutionResult.resolved(toCandidate(matching.get()), MatchMethod.MEMORY_HINT, trimmed);
          }
        }
      }

      return ResolutionResult.ambiguous(
          canonicalCandidates.stream().map(this::toCandidate).toList(),
          trimmed);
    }

    // 3B. Bounded Canonical DB Token Search (significant tokens, e.g. "mips" from "culturl evnt mips")
    String[] tokens = lower.split("\\s+");
    Map<Production, Integer> matchCounts = new HashMap<>();
    for (String token : tokens) {
      String t = token.replaceAll("[^a-zA-Z0-9]", "");
      if (t.length() >= 4 && !isStopWord(t)) {
        for (Production p : allProds) {
          if (matchesTokenWord(p.title, t) || (p.clientName != null && matchesTokenWord(p.clientName, t))) {
            matchCounts.put(p, matchCounts.getOrDefault(p, 0) + 1);
          }
        }
      }
    }
    if (!matchCounts.isEmpty()) {
      int maxMatches = Collections.max(matchCounts.values());
      List<Production> bestMatches = matchCounts.entrySet().stream()
          .filter(e -> e.getValue() == maxMatches)
          .map(Map.Entry::getKey)
          .toList();
      if (bestMatches.size() == 1) {
        return ResolutionResult.resolved(toCandidate(bestMatches.get(0)), MatchMethod.BOUNDED_SEARCH, trimmed);
      }
      return ResolutionResult.ambiguous(
          bestMatches.stream().map(this::toCandidate).toList(),
          trimmed);
    }

    // 4. Memory hint for alias when canonical search yielded 0 matches
    if (memoryService != null) {
      Optional<EveDtos.MemoryView> mem = memoryService.recall(trimmed);
      if (mem.isPresent() && "PRODUCTION".equalsIgnoreCase(mem.get().canonicalType())) {
        EveDtos.MemoryView hint = mem.get();
        if (hint.canonicalId() != null) {
          Optional<Production> verified = productionRepo.findById(hint.canonicalId());
          if (verified.isPresent()) {
            return ResolutionResult.resolved(toCandidate(verified.get()), MatchMethod.MEMORY_HINT, trimmed);
          }
        }
      }
    }

    return ResolutionResult.notFound(trimmed);
  }

  /**
   * Model-assisted candidate selection strictly constrained to an existing candidate set.
   * Invariant: The model may select among real candidates; it can NEVER invent an entity.
   */
  public Optional<Candidate> selectFromCandidates(List<Candidate> candidates, String selectionHint) {
    if (candidates == null || candidates.isEmpty() || selectionHint == null || selectionHint.isBlank()) {
      return Optional.empty();
    }
    String hint = normalizeWhitespace(selectionHint.trim()).toLowerCase(Locale.ROOT);

    // Support index-based ordinal hints ("first", "1st", "1", "pehla", "second", "2nd", "2", "dusra", "doosra", "the second one", "second wala")
    if (hint.equals("first") || hint.equals("1st") || hint.equals("1") || hint.contains("first") || hint.contains("1st") || hint.contains("pehla") || hint.contains("pehle")) {
      return Optional.of(candidates.get(0));
    }
    if ((hint.equals("second") || hint.equals("2nd") || hint.equals("2") || hint.contains("second") || hint.contains("2nd") || hint.contains("dusra") || hint.contains("doosra")) && candidates.size() > 1) {
      return Optional.of(candidates.get(1));
    }
    if ((hint.equals("third") || hint.equals("3rd") || hint.equals("3") || hint.contains("third") || hint.contains("3rd") || hint.contains("teesra") || hint.contains("tisra")) && candidates.size() > 2) {
      return Optional.of(candidates.get(2));
    }

    return candidates.stream()
        .filter(c -> c.id().toString().equalsIgnoreCase(hint)
            || (c.code() != null && c.code().toLowerCase(Locale.ROOT).equalsIgnoreCase(hint))
            || normalizeWhitespace(c.displayName()).toLowerCase(Locale.ROOT).equalsIgnoreCase(hint)
            || normalizeWhitespace(c.displayName()).toLowerCase(Locale.ROOT).contains(hint)
            || (c.detail() != null && c.detail().toLowerCase(Locale.ROOT).contains(hint)))
        .findFirst();
  }

  public ResolutionResult resolveFromCandidateSelection(List<Candidate> candidates, String selectionHint, String spokenValue) {
    Optional<Candidate> selected = selectFromCandidates(candidates, selectionHint);
    if (selected.isPresent()) {
      return ResolutionResult.modelSelected(selected.get(), spokenValue);
    }
    return ResolutionResult.ambiguous(candidates, spokenValue);
  }

  private String normalizeWhitespace(String str) {
    return str.replaceAll("\\s+", " ").trim();
  }

  private boolean isStopWord(String word) {
    if (word == null) return true;
    String w = word.toLowerCase(Locale.ROOT);
    return w.equals("event") || w.equals("production") || w.equals("details")
        || w.equals("client") || w.equals("customer") || w.equals("crew")
        || w.equals("tasks") || w.equals("task") || w.equals("equipment")
        || w.equals("about") || w.equals("tell") || w.equals("show")
        || w.equals("wala") || w.equals("wale") || w.equals("wali")
        || w.equals("kiska") || w.equals("kaun") || w.equals("ka")
        || w.equals("ki") || w.equals("ke") || w.equals("me")
        || w.equals("mein") || w.equals("ko") || w.equals("hai") || w.equals("tha")
        || w.equals("and") || w.equals("the") || w.equals("for") || w.equals("with")
        || w.equals("from") || w.equals("this") || w.equals("that") || w.equals("these")
        || w.equals("those") || w.equals("what") || w.equals("when") || w.equals("where")
        || w.equals("which") || w.equals("who") || w.equals("how") || w.equals("pay")
        || w.equals("give") || w.equals("check") || w.equals("view") || w.equals("get")
        || w.equals("aur") || w.equals("kya") || w.equals("kab") || w.equals("kahan")
        || w.equals("finance") || w.equals("contract") || w.equals("advance") || w.equals("outstanding")
        || w.equals("venue") || w.equals("date") || w.equals("salary") || w.equals("role")
        || w.equals("department") || w.equals("profile") || w.equals("phone") || w.equals("email")
        || w.equals("ignore") || w.equals("rules") || w.equals("instructions") || w.equals("previous");
  }

  private boolean matchesTokenWord(String text, String token) {
    if (text == null || token == null) return false;
    String[] words = text.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+");
    for (String w : words) {
      if (w.equalsIgnoreCase(token) || (w.startsWith(token) && token.length() >= 4)) {
        return true;
      }
      if (Math.min(w.length(), token.length()) >= 5 && editDistance(w, token) <= 1) {
        return true;
      }
      if (Math.min(w.length(), token.length()) >= 6 && editDistance(w, token) <= 2) {
        return true;
      }
    }
    return false;
  }

  private int editDistance(String a, String b) {
    if (a == null || b == null) return Integer.MAX_VALUE;
    int[][] dp = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
    for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
        dp[i][j] = Math.min(
            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
            dp[i - 1][j - 1] + cost);
      }
    }
    return dp[a.length()][b.length()];
  }

  public Candidate toCandidate(Employee e) {
    return new Candidate(
        e.id,
        "EMPLOYEE",
        e.displayName,
        e.employeeCode,
        e.roleTitle != null ? e.roleTitle : "Team Member");
  }

  public Candidate toCandidate(EmployeeDtos.View v) {
    return new Candidate(
        v.id(),
        "EMPLOYEE",
        v.displayName(),
        v.employeeCode(),
        v.roleTitle() != null ? v.roleTitle() : "Team Member");
  }

  public Candidate toCandidate(Production p) {
    return new Candidate(
        p.id,
        "PRODUCTION",
        p.title,
        p.id.toString().substring(0, 8),
        String.format("Client: %s, Date: %s, Venue: %s", p.clientName, p.eventDate, p.venueName));
  }
}
