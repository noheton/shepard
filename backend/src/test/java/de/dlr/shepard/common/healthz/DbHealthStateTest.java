package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

public class DbHealthStateTest {

  @Test
  public void freshlyConstructed_isNotUp() {
    DbHealthState s = new DbHealthState();
    assertFalse(s.hasEverBeenUp());
    assertFalse(s.isFreshWithin(10_000L));
  }

  @Test
  public void recordSuccess_marksEverUpAndFresh() {
    DbHealthState s = new DbHealthState();
    s.recordSuccess(7L);
    assertTrue(s.hasEverBeenUp());
    assertTrue(s.isFreshWithin(60_000L));
  }

  @Test
  public void recordFailure_keepsEverUpFalseAndStoresError() {
    DbHealthState s = new DbHealthState();
    s.recordFailure(3L, new IllegalStateException("boom"));
    assertFalse(s.hasEverBeenUp());

    var b = HealthCheckResponse.named("x").down();
    s.writeData(b);
    var r = b.build();
    assertEquals("java.lang.IllegalStateException", r.getData().orElseThrow().get("errorClass"));
    assertEquals("boom", r.getData().orElseThrow().get("errorMessage"));
    assertEquals(3L, r.getData().orElseThrow().get("latencyMs"));
  }

  @Test
  public void recordSuccess_clearsPriorError() {
    DbHealthState s = new DbHealthState();
    s.recordFailure(3L, new RuntimeException("nope"));
    s.recordSuccess(2L);

    var b = HealthCheckResponse.named("x").up();
    s.writeData(b);
    var r = b.build();
    assertNotNull(r.getData().orElseThrow().get("lastSuccessfulPingMs"));
    assertNull(r.getData().orElseThrow().get("errorClass"));
    assertNull(r.getData().orElseThrow().get("errorMessage"));
  }
}
