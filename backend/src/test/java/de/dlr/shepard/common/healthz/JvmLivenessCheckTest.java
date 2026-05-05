package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

public class JvmLivenessCheckTest {

  @Test
  public void alwaysReturnsUp() {
    var resp = new JvmLivenessCheck().call();
    assertEquals("jvm-liveness", resp.getName());
    assertEquals(HealthCheckResponse.Status.UP, resp.getStatus());
  }
}
