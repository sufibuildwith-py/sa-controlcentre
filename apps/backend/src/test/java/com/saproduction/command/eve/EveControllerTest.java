package com.saproduction.command.eve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.saproduction.command.shared.ApiEnvelope;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EveControllerTest {

  private EveService service;
  private EveController controller;

  @BeforeEach
  void setUp() {
    service = mock(EveService.class);
    controller = new EveController(service);
  }

  @Test
  void queryDelegatesToServiceAndReturnsEnvelope() {
    EveDtos.QueryRequest request = new EveDtos.QueryRequest("How much does Sharma still need?", null);
    UUID sessionId = UUID.randomUUID();
    EveDtos.MessageView msg = new EveDtos.MessageView(UUID.randomUUID(), sessionId, "ASSISTANT", "Raj Sharma has ₹40,000 outstanding.", Instant.now());
    EveDtos.QueryResponse expected = new EveDtos.QueryResponse(sessionId, msg, List.of(), null, "COMPLETED", List.of());

    when(service.query(request)).thenReturn(expected);

    ApiEnvelope<EveDtos.QueryResponse> response = controller.query(request);

    assertThat(response.data()).isEqualTo(expected);
    verify(service).query(request);
  }

  @Test
  void listSessionsDelegatesToService() {
    UUID id = UUID.randomUUID();
    EveDtos.SessionView session = new EveDtos.SessionView(id, "Test Session", "ACTIVE", Instant.now(), Instant.now(), List.of());
    when(service.listSessions()).thenReturn(List.of(session));

    ApiEnvelope<List<EveDtos.SessionView>> response = controller.listSessions();

    assertThat(response.data()).hasSize(1);
    assertThat(response.data().get(0).title()).isEqualTo("Test Session");
  }

  @Test
  void getSessionDelegatesToService() {
    UUID id = UUID.randomUUID();
    EveDtos.SessionView session = new EveDtos.SessionView(id, "Test Session", "ACTIVE", Instant.now(), Instant.now(), List.of());
    when(service.getSession(id)).thenReturn(session);

    ApiEnvelope<EveDtos.SessionView> response = controller.getSession(id);

    assertThat(response.data().id()).isEqualTo(id);
  }

  @Test
  void memoryEndpointsDelegateToService() {
    UUID memId = UUID.randomUUID();
    EveDtos.MemoryRequest req = new EveDtos.MemoryRequest("VOCABULARY", "Raju", "EMPLOYEE", UUID.randomUUID(), "Raj Kumar");
    EveDtos.MemoryView view = new EveDtos.MemoryView(memId, "VOCABULARY", "Raju", "EMPLOYEE", req.canonicalId(), req.canonicalName(), 1.0, "OPERATOR", Instant.now(), Instant.now());

    when(service.remember(req)).thenReturn(view);
    when(service.listMemories()).thenReturn(List.of(view));
    when(service.deleteMemory(memId)).thenReturn(true);

    ApiEnvelope<EveDtos.MemoryView> remembered = controller.remember(req);
    assertThat(remembered.data().term()).isEqualTo("Raju");

    ApiEnvelope<List<EveDtos.MemoryView>> list = controller.listMemories();
    assertThat(list.data()).hasSize(1);

    ApiEnvelope<Boolean> deleted = controller.deleteMemory(memId);
    assertThat(deleted.data()).isTrue();
  }
}
