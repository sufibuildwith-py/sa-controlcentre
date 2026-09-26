package com.saproduction.command.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.saproduction.command.dashboard.CommandDashboardDto.CommandDashboard;
import com.saproduction.command.shared.ApiEnvelope;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CommandDashboardControllerTest {

  @Test
  void getDashboardDelegatesToReadServiceAndWrapsInEnvelope() {
    CommandDashboardReadService service = mock(CommandDashboardReadService.class);
    CommandDashboardController controller = new CommandDashboardController(service);

    LocalDate date = LocalDate.of(2026, 9, 26);
    CommandDashboard mockDashboard = mock(CommandDashboard.class);
    when(service.getDashboard(date)).thenReturn(mockDashboard);

    ApiEnvelope<CommandDashboard> response = controller.getDashboard(date);

    assertThat(response).isNotNull();
    assertThat(response.data()).isSameAs(mockDashboard);
    verify(service).getDashboard(date);
  }
}
