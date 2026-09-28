package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.saproduction.command.shared.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class EveMemoryServiceTest {

  private JdbcTemplate jdbc;
  private EveMemoryService memoryService;

  @BeforeEach
  void setUp() {
    jdbc = mock(JdbcTemplate.class);
    memoryService = new EveMemoryService(jdbc);
  }

  @Test
  void remembersOperatorVocabularyTerm() {
    UUID canonId = UUID.randomUUID();
    EveDtos.MemoryRequest req = new EveDtos.MemoryRequest("VOCABULARY", "Raju", "EMPLOYEE", canonId, "Raj Kumar");

    EveDtos.MemoryView mockView = new EveDtos.MemoryView(
        UUID.randomUUID(), "VOCABULARY", "Raju", "EMPLOYEE", canonId, "Raj Kumar", 1.0, "OPERATOR_EXPLICIT", Instant.now(), Instant.now());

    when(jdbc.query(contains("FROM eve_memory WHERE lower(term)"), any(RowMapper.class), eq("Raju")))
        .thenReturn(List.of(mockView));

    EveDtos.MemoryView remembered = memoryService.remember(req, "OPERATOR_EXPLICIT");

    assertThat(remembered.term()).isEqualTo("Raju");
    assertThat(remembered.canonicalName()).isEqualTo("Raj Kumar");
    verify(jdbc).update(contains("INSERT INTO eve_memory"), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void recallsExistingTermCaseInsensitively() {
    EveDtos.MemoryView mockView = new EveDtos.MemoryView(
        UUID.randomUUID(), "VOCABULARY", "Royal", "PRODUCTION", UUID.randomUUID(), "Royal Wedding", 1.0, "OPERATOR_EXPLICIT", Instant.now(), Instant.now());

    when(jdbc.query(contains("FROM eve_memory WHERE lower(term)"), any(RowMapper.class), eq("royal")))
        .thenReturn(List.of(mockView));

    var recalled = memoryService.recall("royal");

    assertThat(recalled).isPresent();
    assertThat(recalled.get().canonicalName()).isEqualTo("Royal Wedding");
  }

  @Test
  void rejectsBlankMemoryTerm() {
    EveDtos.MemoryRequest req = new EveDtos.MemoryRequest("VOCABULARY", "  ", "EMPLOYEE", null, null);

    assertThatThrownBy(() -> memoryService.remember(req, null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Memory term cannot be blank");
  }
}
