package de.dlr.shepard.plugins.v1compat.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.plugins.v1compat.io.LegacyV1StatsIO;
import de.dlr.shepard.plugins.v1compat.services.LegacyV1StatsService;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LegacyV1StatsAdminRestTest {

  private LegacyV1StatsService stats;
  private LegacyV1StatsAdminRest rest;

  @BeforeEach
  void setUp() {
    stats = mock(LegacyV1StatsService.class);
    rest = new LegacyV1StatsAdminRest();
    rest.stats = stats;
  }

  @Test
  void getStats_defaultTopN_delegatesToServiceWithDefault() {
    LegacyV1StatsIO snap = new LegacyV1StatsIO(0L, List.of(), List.of(), null, null);
    when(stats.snapshot(LegacyV1StatsService.DEFAULT_TOP_N)).thenReturn(snap);

    Response response = rest.getStats(null);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isSameAs(snap);
    verify(stats).snapshot(LegacyV1StatsService.DEFAULT_TOP_N);
  }

  @Test
  void getStats_explicitTopN_passesThrough() {
    LegacyV1StatsIO snap = new LegacyV1StatsIO(0L, List.of(), List.of(), null, null);
    when(stats.snapshot(10)).thenReturn(snap);

    rest.getStats(10);

    verify(stats).snapshot(10);
  }

  @Test
  void getStats_topNTooLarge_clampedToMax() {
    LegacyV1StatsIO snap = new LegacyV1StatsIO(0L, List.of(), List.of(), null, null);
    when(stats.snapshot(LegacyV1StatsAdminRest.MAX_TOP_N)).thenReturn(snap);

    rest.getStats(100_000);

    verify(stats).snapshot(LegacyV1StatsAdminRest.MAX_TOP_N);
  }

  @Test
  void getStats_topNZeroOrNegative_clampedToOne() {
    LegacyV1StatsIO snap = new LegacyV1StatsIO(0L, List.of(), List.of(), null, null);
    when(stats.snapshot(1)).thenReturn(snap);

    rest.getStats(0);
    rest.getStats(-5);

    verify(stats, org.mockito.Mockito.times(2)).snapshot(1);
  }
}
