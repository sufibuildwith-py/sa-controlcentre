package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EveKnowledgeServiceTest {

  private EveKnowledgeService knowledgeService;

  @BeforeEach
  void setUp() {
    knowledgeService = new EveKnowledgeService();
  }

  @Test
  void fileBackedKnowledgeWinsWhenAvailable() {
    // When files exist in eve/knowledge/, service must be in FILE_BACKED mode
    assertThat(knowledgeService.getMode()).isEqualTo(EveKnowledgeService.KnowledgeMode.FILE_BACKED);

    List<String> financeSnippets = knowledgeService.getRelevantKnowledge("FINANCE", "READ_EMPLOYEE_FINANCE");
    assertThat(financeSnippets).isNotEmpty();
    String content = financeSnippets.get(0);
    // Real file has heading and full invariant definitions
    assertThat(content).contains("# SA Command — Financial Rules & Invariants");
    assertThat(content).contains("earned != paid");
    assertThat(content).contains("AZ-2");
  }

  @Test
  void fallbackIsUsedOnlyWhenFilesAreUnavailable() {
    // When pointing to a nonexistent path, it must gracefully enter EMBEDDED_BOOTSTRAP_FALLBACK mode
    Path nonExistentDir = Path.of("nonexistent", "knowledge", "dir", "xyz");
    EveKnowledgeService fallbackService = new EveKnowledgeService(nonExistentDir);

    assertThat(fallbackService.getMode()).isEqualTo(EveKnowledgeService.KnowledgeMode.EMBEDDED_BOOTSTRAP_FALLBACK);

    List<String> financeSnippets = fallbackService.getRelevantKnowledge("FINANCE", "READ_EMPLOYEE_FINANCE");
    assertThat(financeSnippets).isNotEmpty();
    // Embedded definition is concise bootstrap text, not markdown document
    assertThat(financeSnippets.get(0)).contains("Finance Invariants:");
    assertThat(financeSnippets.get(0)).contains("earned != paid");
  }

  @Test
  void fallbackCannotSilentlySupersedeRepositoryKnowledge() {
    // In FILE_BACKED mode, the content is strictly from the filesystem files
    List<String> snippets = knowledgeService.getRelevantKnowledge("FINANCE", null);
    assertThat(snippets).isNotEmpty();
    // Must contain the actual file header, not the embedded fallback header
    assertThat(snippets.get(0)).startsWith("# SA Command — Financial Rules & Invariants");
    assertThat(snippets.get(0)).doesNotStartWith("Finance Invariants:\n1. earned != paid");
  }

  @Test
  void providesEntityKnowledgeForEmployeeDomain() {
    List<String> employeeSnippets = knowledgeService.getRelevantKnowledge("EMPLOYEE", "READ_EMPLOYEE");

    assertThat(employeeSnippets).isNotEmpty();
    String content = employeeSnippets.get(0);
    assertThat(content).contains("Employee");
  }

  @Test
  void providesFallbackDomainOverview() {
    List<String> generalSnippets = knowledgeService.getRelevantKnowledge("UNKNOWN", "GENERAL");

    assertThat(generalSnippets).isNotEmpty();
    assertThat(generalSnippets.get(0)).contains("Domains");
  }
}
