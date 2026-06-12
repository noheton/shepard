package de.dlr.shepard.v2.containers.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.data.file.entities.FileContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-BASICENTITY-DROP-ID slice 1 — verifies that {@link ContainerV2IO}
 * (which now extends {@link BasicContainerV2IO}) suppresses the Neo4j
 * internal {@code id} field from the /v2/ wire shape while preserving
 * {@code appId} and kind-specific fields.
 *
 * <p>The v1 {@code /shepard/api/} wire shape is not tested here; it is
 * covered by the existing v1 tests and must remain unchanged.
 */
class BasicContainerV2IOTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private FileContainer fileContainer(long neoId, String appId, String name) {
    var c = new FileContainer(neoId);
    c.setAppId(appId);
    c.setName(name);
    return c;
  }

  private TimeseriesContainer tsContainer(long neoId, String appId, String name) {
    var c = new TimeseriesContainer(neoId);
    c.setAppId(appId);
    c.setName(name);
    return c;
  }

  @Test
  void fileContainerV2IO_serializes_withoutId() throws Exception {
    var io = new ContainerV2IO(fileContainer(42L, "018f-abc", "AFP-Layer-001"), "file");
    var tree = (ObjectNode) MAPPER.valueToTree(io);

    assertFalse(tree.has("id"), "id must not appear in /v2/ container responses");
    assertTrue(tree.has("appId"), "appId must be present");
    assertEquals("018f-abc", tree.get("appId").asText());
    assertEquals("file", tree.get("kind").asText());
    assertEquals("AFP-Layer-001", tree.get("name").asText());
  }

  @Test
  void tsContainerV2IO_serializes_withoutId() throws Exception {
    var io = new ContainerV2IO(tsContainer(99L, "019a-xyz", "Sensors"), "timeseries");
    var tree = (ObjectNode) MAPPER.valueToTree(io);

    assertFalse(tree.has("id"), "id must not appear in /v2/ container responses");
    assertEquals("019a-xyz", tree.get("appId").asText());
    assertEquals("timeseries", tree.get("kind").asText());
  }

  @Test
  void getUniqueId_returnsAppId_notNumericId() {
    var io = new ContainerV2IO(fileContainer(7L, "uuid-v7-here", "C"), "file");
    assertEquals("uuid-v7-here", io.getUniqueId(),
      "getUniqueId() on a v2 container IO must return appId, not numeric Neo4j id");
  }

  @Test
  void payloadFields_survive_serialization() throws Exception {
    var io = new ContainerV2IO(fileContainer(1L, "app-1", "F"), "file")
      .put("oid", "mongo-oid-123")
      .put("defaultCollectionIdList", null);
    var tree = (ObjectNode) MAPPER.valueToTree(io);

    assertEquals("mongo-oid-123", tree.get("payload").get("oid").asText());
  }
}
