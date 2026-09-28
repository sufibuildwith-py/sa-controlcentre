package com.saproduction.command.eve;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * System Knowledge loader and provider for EVE.
 * Provides machine-readable domain knowledge, rules, and terminology snippets
 * to bring authoritative system facts into the bounded context envelope.
 *
 * Explicit Authority Model:
 * 1. FILE_BACKED: Repository knowledge files in eve/knowledge/ are authoritative.
 * 2. EMBEDDED_BOOTSTRAP_FALLBACK: Used only when filesystem knowledge directory is unavailable.
 * Embedded fallback never silently overrides or mingles with repository knowledge files.
 */
@Service
public class EveKnowledgeService {

  private static final Logger log = LoggerFactory.getLogger(EveKnowledgeService.class);

  public enum KnowledgeMode {
    FILE_BACKED,
    EMBEDDED_BOOTSTRAP_FALLBACK
  }

  private final Map<String, String> cachedKnowledge = new HashMap<>();
  private KnowledgeMode mode;

  public EveKnowledgeService() {
    this(null);
  }

  public EveKnowledgeService(Path explicitDirectory) {
    loadKnowledge(explicitDirectory);
  }

  public KnowledgeMode getMode() {
    return mode;
  }

  private void loadKnowledge(Path explicitDirectory) {
    Path knowledgeDir = null;

    if (explicitDirectory != null) {
      if (Files.isDirectory(explicitDirectory)) {
        knowledgeDir = explicitDirectory;
      }
    } else {
      List<Path> candidatePaths = List.of(
          Paths.get("eve", "knowledge"),
          Paths.get("..", "eve", "knowledge"),
          Paths.get("..", "..", "eve", "knowledge"));

      for (Path p : candidatePaths) {
        if (Files.isDirectory(p)) {
          knowledgeDir = p;
          break;
        }
      }
    }

    if (knowledgeDir != null) {
      boolean loadedAny = false;
      try {
        loadedAny |= loadFile(knowledgeDir, "domains.md", "DOMAINS");
        loadedAny |= loadFile(knowledgeDir, "entities.md", "ENTITIES");
        loadedAny |= loadFile(knowledgeDir, "relationships.md", "RELATIONSHIPS");
        loadedAny |= loadFile(knowledgeDir, "commands.md", "COMMANDS");
        loadedAny |= loadFile(knowledgeDir, "finance-rules.md", "FINANCE");
        loadedAny |= loadFile(knowledgeDir, "terminology.md", "TERMINOLOGY");
      } catch (Exception e) {
        log.warn("Error reading knowledge files: {}", e.getMessage());
      }

      if (loadedAny) {
        this.mode = KnowledgeMode.FILE_BACKED;
        log.info("EVE Knowledge initialized in FILE_BACKED mode from {}", knowledgeDir.toAbsolutePath());
        return;
      }
    }

    // Filesystem knowledge directory unavailable -> explicit degraded bootstrap fallback mode
    this.mode = KnowledgeMode.EMBEDDED_BOOTSTRAP_FALLBACK;
    log.warn("EVE Knowledge files unavailable. Operating in explicit EMBEDDED_BOOTSTRAP_FALLBACK mode.");
    loadEmbeddedFallbacks();
  }

  private boolean loadFile(Path dir, String filename, String key) {
    Path file = dir.resolve(filename);
    if (Files.isRegularFile(file)) {
      try {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        cachedKnowledge.put(key, content);
        return true;
      } catch (IOException e) {
        log.warn("Failed reading knowledge file {}: {}", filename, e.getMessage());
      }
    }
    return false;
  }

  private void loadEmbeddedFallbacks() {
    cachedKnowledge.put("DOMAINS", """
        Domains: People (Employee 360, attendance, payroll), Productions (events, schedule, crew),
        Headquarters (gear, physical inventory, reservations), Work (tasks), Finance (canonical ledger,
        obligations, transactions, owner positions), Billing (invoicing, direct charges).
        """);

    cachedKnowledge.put("FINANCE", """
        Finance Invariants:
        1. earned != paid: earned is sum of obligations; paid is sum of posted allocations; outstanding = earned - paid.
        2. Employee earning != Employee payment.
        3. Owner attribution is explicit: Outflow payments require explicit payer account (AZ-2 for Azeem Khan, AK-2 for Akash).
        4. Balances are derived dynamically from posted transactions, never manually edited.
        """);

    cachedKnowledge.put("ENTITIES", """
        Employee: identified by UUID or uppercase employee_code (e.g. SA-01).
        Production: event production organizing crew, gear, and tasks.
        Counterparty: clients or vendors with GSTIN and role.
        """);
  }

  public List<String> getRelevantKnowledge(String domain, String intent) {
    List<String> snippets = new ArrayList<>();
    if ("FINANCE".equalsIgnoreCase(domain) || (intent != null && intent.contains("FINANCE"))) {
      if (cachedKnowledge.containsKey("FINANCE")) {
        snippets.add(cachedKnowledge.get("FINANCE").trim());
      }
    }
    if ("EMPLOYEE".equalsIgnoreCase(domain) || (intent != null && intent.contains("EMPLOYEE"))) {
      if (cachedKnowledge.containsKey("ENTITIES")) {
        snippets.add(cachedKnowledge.get("ENTITIES").trim());
      }
    }
    if (snippets.isEmpty() && cachedKnowledge.containsKey("DOMAINS")) {
      snippets.add(cachedKnowledge.get("DOMAINS").trim());
    }
    return snippets;
  }
}
