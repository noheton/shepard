package de.dlr.shepard.v2.timeseries.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-CROSS-BULK-KIND-PATH — verifies that the old
 * {@code POST /v2/data-objects/cross-timeseries-bulk} path returns 410 Gone
 * with a {@code Location} header pointing to the new path.
 */
public class CrossDoBulkTombstoneRestTest {

  @Test
  void post_returns410GoneWithLocation() {
    CrossDoBulkTombstoneRest rest = new CrossDoBulkTombstoneRest();
    Response resp = rest.post();
    assertEquals(410, resp.getStatus());
    assertEquals(
      "/v2/data-objects/cross-bulk?kind=timeseries",
      resp.getHeaderString("Location")
    );
  }
}
