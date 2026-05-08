package de.dlr.shepard.common.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

public class TraceIdProviderTest {

  @Test
  public void traceIdIsStableWithinTheSameInstance() {
    TraceIdProvider provider = new TraceIdProvider();

    String first = provider.getTraceId();
    String second = provider.getTraceId();

    assertNotNull(first);
    assertEquals(first, second);
    // Sanity: it parses as a UUID.
    UUID.fromString(first);
  }

  @Test
  public void distinctInstancesHaveDistinctIds() {
    TraceIdProvider a = new TraceIdProvider();
    TraceIdProvider b = new TraceIdProvider();

    assertNotEquals(a.getTraceId(), b.getTraceId());
  }
}
