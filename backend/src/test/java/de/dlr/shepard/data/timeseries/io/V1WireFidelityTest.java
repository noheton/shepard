package de.dlr.shepard.data.timeseries.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pinned-JSON serializer fidelity tests for the v1 timeseries IO
 * surface ({@code /shepard/api/timeseriesContainers/...}). These tests
 * are the regression net for the TS-ID wire-rename series — any field
 * added to a v1 IO will fail one of these assertions.
 *
 * <p>The rule the TS-ID series MUST honour: {@code /shepard/api/...}
 * stays byte-compatible with upstream shepard 5.2.0. {@code shepardId}
 * is a {@code /v2/}-only field; it must NOT appear in v1 JSON output.
 *
 * <p>Why pinned-JSON and not recorded HTTP fixtures: a serializer test
 * runs in the unit-test phase, doesn't boot Quarkus, and pins the exact
 * JSON property names. Adding a {@code @JsonProperty("shepardId")}
 * annotation anywhere on the v1 hierarchy will fail this test
 * immediately at CI time — cheaper than discovering it from a curl-vs-
 * fixture diff after deployment.
 */
public class V1WireFidelityTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private TimeseriesEntity sampleRow() {
    TimeseriesEntity row = new TimeseriesEntity(
      42L,
      "vibration",
      "g_rms",
      "AFP-1",
      "head",
      "ts1",
      DataPointValueType.Double
    );
    // shepardId is set on persistence — for the unit-level v1 wire test
    // we set it explicitly so we can assert it's NOT serialized into v1
    // JSON. The Postgres default would also populate it on real rows.
    row.setShepardId(UUID.fromString("00000000-0000-4000-8000-000000000abc"));
    return row;
  }

  @Test
  void timeseriesIO_v1_doesNotEmitShepardId() throws Exception {
    // The frozen upstream v5 wire shape: id, containerId, valueType,
    // measurement, device, location, symbolicName, field. shepardId
    // is NEW in this fork — it MUST NOT appear here.
    TimeseriesEntity row = sampleRow();
    TimeseriesIO io = new TimeseriesIO(row);

    String json = mapper.writeValueAsString(io);

    assertFalse(
      json.contains("\"shepardId\""),
      "v1 TimeseriesIO must NOT emit a 'shepardId' field — v5 byte-fidelity rule. JSON was: " + json
    );
    // Additionally lock the 5-tuple fields are present — guards against
    // a refactor that accidentally drops a property the upstream wire
    // depends on.
    assertTrue(json.contains("\"measurement\":\"vibration\""), "measurement missing from v1 wire");
    assertTrue(json.contains("\"device\":\"AFP-1\""), "device missing from v1 wire");
    assertTrue(json.contains("\"location\":\"head\""), "location missing from v1 wire");
    assertTrue(json.contains("\"symbolicName\":\"ts1\""), "symbolicName missing from v1 wire");
    assertTrue(json.contains("\"field\":\"g_rms\""), "field missing from v1 wire");
    assertTrue(json.contains("\"valueType\":\"Double\""), "valueType missing from v1 wire");
  }

  @Test
  void timeseriesIO_v1_pinsExactJsonShape() throws Exception {
    // Tighter assertion: the v1 wire is byte-exact. Order is not
    // contractual in JSON, but the property set is — so we parse back
    // and verify the property keys match the upstream contract.
    TimeseriesEntity row = sampleRow();
    TimeseriesIO io = new TimeseriesIO(row);

    String json = mapper.writeValueAsString(io);
    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> shape = mapper.readValue(json, java.util.Map.class);

    java.util.Set<String> expectedKeys = java.util.Set.of(
      "id",
      "containerId",
      "valueType",
      "measurement",
      "device",
      "location",
      "symbolicName",
      "field"
    );
    assertEquals(
      expectedKeys,
      shape.keySet(),
      "v1 TimeseriesIO property set drifted from upstream v5.2.0 — wire fidelity broken"
    );
  }
}
