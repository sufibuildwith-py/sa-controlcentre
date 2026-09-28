package com.saproduction.command.eve;

import com.saproduction.command.employee.Employee;
import com.saproduction.command.employee.EmployeeDtos;
import com.saproduction.command.employee.EmployeeRepository;
import com.saproduction.command.employee.EmployeeService;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Deterministic retrieval engine for EVE.
 * Enforces the authoritative canonical resolution order:
 * 1. Exact canonical identifier / UUID
 * 2. Exact canonical code (case-insensitive, e.g. "SA-01")
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

  @Autowired
  public EveRetrievalService(
      EmployeeRepository employeeRepo,
      EmployeeService employeeService,
      @Autowired(required = false) EveMemoryService memoryService) {
    this.employeeRepo = employeeRepo;
    this.employeeService = employeeService;
    this.memoryService = memoryService;
  }

  public enum MatchMethod {
    EXACT_ID,
    EXACT_CODE,
    EXACT_NAME,
    BOUNDED_SEARCH,
    MEMORY_HINT,
    MODEL_SELECTION
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
    if (spokenValue == null || spokenValue.isBlank()) {
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
            // Re-validate against canonical repository before resolving
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

    // 5. Canonical DB search yielded 0 matches for this spoken term (e.g. operator used an alias like "Raju").
    // Memory can suggest a candidate, BUT memory is strictly a hint:
    // - Memory cannot invent an entity
    // - Memory cannot bypass canonical DB resolution
    // - Stale memory pointing to a deleted or non-existent entity must return NOT_FOUND
    // - Candidate suggested by memory MUST be fetched and re-validated against canonical DB state
    if (memoryService != null) {
      Optional<EveDtos.MemoryView> mem = memoryService.recall(trimmed);
      if (mem.isPresent() && "EMPLOYEE".equalsIgnoreCase(mem.get().canonicalType())) {
        EveDtos.MemoryView hint = mem.get();
        if (hint.canonicalId() != null) {
          Optional<Employee> verified = employeeRepo.findById(hint.canonicalId());
          if (verified.isPresent()) {
            Employee emp = verified.get();
            if (emp.status != Employee.Status.INACTIVE) {
              // Return canonical database data (fresh displayName, current code)
              return ResolutionResult.resolved(toCandidate(emp), MatchMethod.MEMORY_HINT, trimmed);
            }
          }
          // Stale memory pointing to deleted/missing entity: do NOT resolve.
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

  /**
   * Model-assisted candidate selection strictly constrained to an existing candidate set.
   * Invariant: The model may select among real candidates; it can NEVER invent an entity.
   */
  public Optional<Candidate> selectFromCandidates(List<Candidate> candidates, String selectionHint) {
    if (candidates == null || candidates.isEmpty() || selectionHint == null || selectionHint.isBlank()) {
      return Optional.empty();
    }
    String hint = normalizeWhitespace(selectionHint.trim()).toLowerCase(Locale.ROOT);
    return candidates.stream()
        .filter(c -> c.id().toString().equalsIgnoreCase(hint)
            || c.code().toLowerCase(Locale.ROOT).equalsIgnoreCase(hint)
            || normalizeWhitespace(c.displayName()).toLowerCase(Locale.ROOT).equalsIgnoreCase(hint)
            || normalizeWhitespace(c.displayName()).toLowerCase(Locale.ROOT).contains(hint))
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

  private Candidate toCandidate(Employee e) {
    return new Candidate(
        e.id,
        "EMPLOYEE",
        e.displayName,
        e.employeeCode,
        e.roleTitle != null ? e.roleTitle : "Team Member");
  }

  private Candidate toCandidate(EmployeeDtos.View v) {
    return new Candidate(
        v.id(),
        "EMPLOYEE",
        v.displayName(),
        v.employeeCode(),
        v.roleTitle() != null ? v.roleTitle() : "Team Member");
  }
}
