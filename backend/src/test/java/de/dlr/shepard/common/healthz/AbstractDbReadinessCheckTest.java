package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

public class AbstractDbReadinessCheckTest {

  private static AbstractDbReadinessCheck check(DbPinger p, long maxStalenessMs) {
    ReadinessConfig cfg = mock(ReadinessConfig.class);
    when(cfg.maxStalenessMs()).thenReturn(maxStalenessMs);
    return new AbstractDbReadinessCheck() {
      @Override
      protected DbPinger pinger() {
        return p;
      }

      @Override
      protected ReadinessConfig config() {
        return cfg;
      }
    };
  }

  @Test
  public void notRequired_returnsUpWithRequiredFalse() {
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("postgis");
    when(p.isRequired()).thenReturn(false);

    var resp = check(p, 30_000L).call();
    assertEquals("postgis-readiness", resp.getName());
    assertEquals(HealthCheckResponse.Status.UP, resp.getStatus());
    assertEquals(false, resp.getData().orElseThrow().get("required"));
    verify(p, never()).ping();
  }

  @Test
  public void freshState_returnsUpWithoutPinging() {
    DbHealthState state = new DbHealthState();
    state.recordSuccess(5L);
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("neo4j");
    when(p.isRequired()).thenReturn(true);
    when(p.state()).thenReturn(state);

    var resp = check(p, 60_000L).call();
    assertEquals(HealthCheckResponse.Status.UP, resp.getStatus());
    assertEquals(true, resp.getData().orElseThrow().get("required"));
    assertNotNull(resp.getData().orElseThrow().get("lastSuccessfulPingMs"));
    assertEquals(5L, resp.getData().orElseThrow().get("latencyMs"));
    verify(p, never()).ping();
  }

  @Test
  public void staleState_pingsAndReturnsUpOnSuccess() {
    DbHealthState state = new DbHealthState();
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("mongodb");
    when(p.isRequired()).thenReturn(true);
    when(p.state()).thenReturn(state);
    when(p.ping())
      .thenAnswer(inv -> {
        state.recordSuccess(2L);
        return true;
      });

    var resp = check(p, 30_000L).call();
    assertEquals(HealthCheckResponse.Status.UP, resp.getStatus());
    assertEquals(2L, resp.getData().orElseThrow().get("latencyMs"));
    verify(p).ping();
  }

  @Test
  public void staleState_returnsDownWithErrorOnFailure() {
    DbHealthState state = new DbHealthState();
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("timescaledb");
    when(p.isRequired()).thenReturn(true);
    when(p.state()).thenReturn(state);
    when(p.ping())
      .thenAnswer(inv -> {
        state.recordFailure(11L, new IllegalStateException("conn refused"));
        return false;
      });

    var resp = check(p, 30_000L).call();
    assertEquals("timescaledb-readiness", resp.getName());
    assertEquals(HealthCheckResponse.Status.DOWN, resp.getStatus());
    var data = resp.getData().orElseThrow();
    assertEquals("java.lang.IllegalStateException", data.get("errorClass"));
    assertTrue(((String) data.get("errorMessage")).contains("conn refused"));
    assertEquals(11L, data.get("latencyMs"));
    assertEquals(30_000L, data.get("maxStalenessMs"));
  }
}
