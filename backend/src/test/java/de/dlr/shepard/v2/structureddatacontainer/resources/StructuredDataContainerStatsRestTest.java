package de.dlr.shepard.v2.structureddatacontainer.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.dlr.shepard.common.exceptions.ProblemJson;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-STRUCT-CONTAINER-STATS-UNIFY — verifies the 410 Gone tombstone.
 *
 * <p>The bespoke {@code GET /v2/structured-data-containers/{appId}/stats} path was merged
 * into the kind-dispatcher ({@code GET /v2/containers/{appId}/stats}).
 * This test confirms the tombstone returns 410 for any appId.
 */
class StructuredDataContainerStatsRestTest {

  private StructuredDataContainerStatsRest rest;

  @BeforeEach
  void setUp() {
    rest = new StructuredDataContainerStatsRest();
  }

  @Test
  void returnsGone() {
    Response r = rest.getStats("01928eaa-2222-7000-9000-bbbbbbbbbbbb");
    assertEquals(410, r.getStatus());
  }

  @Test
  void returnsGoneForArbitraryAppId() {
    Response r = rest.getStats("00000000-0000-7000-8000-000000000001");
    assertEquals(410, r.getStatus());
  }

  @Test
  void responseBodyIsProblemJsonWithLocationHeader() {
    String appId = "01928eaa-2222-7000-9000-bbbbbbbbbbbb";
    Response r = rest.getStats(appId);
    ProblemJson body = (ProblemJson) r.getEntity();
    assertNotNull(body);
    assertEquals("urn:shepard:error:gone", body.type());
    String location = (String) r.getHeaders().getFirst("Location");
    assertNotNull(location);
    assertEquals("/v2/containers/" + appId + "/stats", location);
  }
}
