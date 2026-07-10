package de.dlr.shepard.v2.timeseries.resources;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-ANOMALY-ACTION-PATH tombstone — verifies that the old
 * {@code POST /v2/references/{appId}/detect-anomalies} path returns 410 Gone
 * with a {@code Location} header pointing to the new action endpoint.
 */
class AnomalyDetectionTombstoneRestTest {

  @Test
  void post_returns410GoneWithLocation() {
    var rest = new AnomalyDetectionTombstoneRest();
    Response resp = rest.post("ref-uuid-123");
    assertThat(resp.getStatus()).isEqualTo(410);
    assertThat(resp.getHeaderString("Location"))
        .isEqualTo("/v2/references/ref-uuid-123/actions?action=detect-anomalies");
  }

  @Test
  void post_interpolatesAppIdInLocation() {
    var rest = new AnomalyDetectionTombstoneRest();
    String appId = "019xxx-some-uuid";
    Response resp = rest.post(appId);
    assertThat(resp.getHeaderString("Location"))
        .isEqualTo("/v2/references/" + appId + "/actions?action=detect-anomalies");
  }
}
