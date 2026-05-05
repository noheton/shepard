package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

public class AbstractDbStartupCheckTest {

  private static AbstractDbStartupCheck check(DbPinger p) {
    return new AbstractDbStartupCheck() {
      @Override
      protected DbPinger pinger() {
        return p;
      }
    };
  }

  @Test
  public void notRequired_returnsUp() {
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("postgis");
    when(p.isRequired()).thenReturn(false);

    var resp = check(p).call();
    assertEquals("postgis-startup", resp.getName());
    assertEquals(HealthCheckResponse.Status.UP, resp.getStatus());
    assertEquals(false, resp.getData().orElseThrow().get("required"));
    verify(p, never()).ping();
  }

  @Test
  public void neverUp_attemptsPingAndUpOnSuccess() {
    DbHealthState state = new DbHealthState();
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("neo4j");
    when(p.isRequired()).thenReturn(true);
    when(p.state()).thenReturn(state);
    when(p.ping())
      .thenAnswer(inv -> {
        state.recordSuccess(4L);
        return true;
      });

    var resp = check(p).call();
    assertEquals(HealthCheckResponse.Status.UP, resp.getStatus());
    assertEquals(4L, resp.getData().orElseThrow().get("latencyMs"));
    verify(p).ping();
  }

  @Test
  public void neverUp_failedPingProducesDownWithError() {
    DbHealthState state = new DbHealthState();
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("mongodb");
    when(p.isRequired()).thenReturn(true);
    when(p.state()).thenReturn(state);
    when(p.ping())
      .thenAnswer(inv -> {
        state.recordFailure(9L, new RuntimeException("nope"));
        return false;
      });

    var resp = check(p).call();
    assertEquals(HealthCheckResponse.Status.DOWN, resp.getStatus());
    assertEquals("java.lang.RuntimeException", resp.getData().orElseThrow().get("errorClass"));
  }

  @Test
  public void wasUpOnce_skipsPingAndStaysUp() {
    DbHealthState state = new DbHealthState();
    state.recordSuccess(1L);
    DbPinger p = mock(DbPinger.class);
    when(p.name()).thenReturn("timescaledb");
    when(p.isRequired()).thenReturn(true);
    when(p.state()).thenReturn(state);

    var resp = check(p).call();
    assertEquals(HealthCheckResponse.Status.UP, resp.getStatus());
    verify(p, never()).ping();
  }
}
