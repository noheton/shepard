package de.dlr.shepard.v2.timeseriescontainer.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link TimeseriesChannelV2IO} — the TS-ID PR-2 wire shape
 * for {@code GET /v2/timeseries-containers/{id}/channels}. The shape is
 * the first /v2/ surface that exposes {@code shepardId} as a JSON field.
 */
public class TimeseriesChannelV2IOTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private TimeseriesEntity sampleRow(UUID shepardId) {
    TimeseriesEntity row = new TimeseriesEntity(
      42L,
      "vibration",
      "g_rms",
      "AFP-1",
      "head",
      "ts1",
      DataPointValueType.Double
    );
    row.setShepardId(shepardId);
    return row;
  }

  @Test
  void from_copiesAllFieldsIncludingShepardId() {
    UUID id = UUID.randomUUID();
    TimeseriesEntity row = sampleRow(id);

    TimeseriesChannelV2IO io = TimeseriesChannelV2IO.from(row);

    assertEquals(id, io.shepardId(), "shepardId must round-trip through the projection helper");
    assertEquals(42L, io.containerId());
    assertEquals("vibration", io.measurement());
    assertEquals("g_rms", io.field());
    assertEquals("AFP-1", io.device());
    assertEquals("head", io.location());
    assertEquals("ts1", io.symbolicName());
    assertEquals(DataPointValueType.Double, io.valueType());
  }

  @Test
  void serializes_shepardId_asJsonField() throws Exception {
    // The /v2/ contract: shepardId is the canonical channel handle.
    // Lock the JSON property name — if a refactor renames it, this fails.
    UUID id = UUID.fromString("00000000-0000-4000-8000-000000000abc");
    TimeseriesChannelV2IO io = TimeseriesChannelV2IO.from(sampleRow(id));

    String json = mapper.writeValueAsString(io);
    assertTrue(
      json.contains("\"shepardId\":\"00000000-0000-4000-8000-000000000abc\""),
      "v2 channel IO must emit shepardId as a JSON field. JSON was: " + json
    );
  }

  @Test
  void serializes_completePropertySet() throws Exception {
    UUID id = UUID.randomUUID();
    TimeseriesChannelV2IO io = TimeseriesChannelV2IO.from(sampleRow(id));

    String json = mapper.writeValueAsString(io);
    @SuppressWarnings("unchecked")
    Map<String, Object> shape = mapper.readValue(json, Map.class);

    // The full /v2/ shape — shepardId + the legacy 5-tuple + id + container + valueType.
    java.util.Set<String> expected = java.util.Set.of(
      "shepardId",
      "id",
      "containerId",
      "measurement",
      "device",
      "location",
      "symbolicName",
      "field",
      "valueType"
    );
    assertEquals(expected, shape.keySet(), "v2 channel IO property set drifted");
  }

  @Test
  void v2_io_is_not_a_v1_io() {
    // Structural guard: this IO must NOT extend the v1 TimeseriesIO.
    // Sharing a base would risk leaking shepardId into v1 wire.
    assertNotNull(TimeseriesChannelV2IO.class.getSuperclass());
    assertEquals(
      Record.class,
      TimeseriesChannelV2IO.class.getSuperclass(),
      "TimeseriesChannelV2IO must extend Record directly — extending v1 TimeseriesIO would leak shepardId into v1 wire"
    );
  }
}
