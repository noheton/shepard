package de.dlr.shepard.v2.dataobject.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-BASICENTITY-DROP-ID slice 2 + APISIMP-DO-IO-NUMERIC-ID-LEAK —
 * verifies that {@link DataObjectListItemV2IO} and {@link DataObjectDetailV2IO}
 * suppress all inherited Neo4j internal numeric id fields from the /v2/ wire shape.
 *
 * <p>Numeric fields suppressed: {@code id}, {@code collectionId},
 * {@code referenceIds}, {@code successorIds}, {@code predecessorIds},
 * {@code childrenIds}, {@code parentId}, {@code incomingIds}.
 */
class DataObjectV2IOIdSuppressionTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static DataObjectListItemV2IO makeListItem(String appId, String name) {
    Collection coll = new Collection();
    coll.setShepardId(42L);
    DataObject d = new DataObject();
    d.setAppId(appId);
    d.setName(name);
    d.setCollection(coll);
    return new DataObjectListItemV2IO(d, 3L, 5L, 2L);
  }

  @Test
  void dataObjectListItemV2IO_serializes_withoutId() throws Exception {
    var io = makeListItem("018f-list-01", "AFP-Layer-001");
    var writer = DataObjectListFieldFilter.writerFor(MAPPER, null, true);
    var json = writer.writeValueAsString(io);
    var tree = (ObjectNode) MAPPER.readTree(json);

    assertFalse(tree.has("id"), "id must not appear in /v2/ DataObject list responses");
    assertTrue(tree.has("appId"), "appId must be present");
    assertEquals("018f-list-01", tree.get("appId").asText());
    assertEquals("AFP-Layer-001", tree.get("name").asText());
  }

  @Test
  void dataObjectDetailV2IO_serializes_withoutId() throws Exception {
    var io = new DataObjectDetailV2IO();
    io.setAppId("018f-detail-02");
    io.setName("TR-004");
    var tree = (ObjectNode) MAPPER.valueToTree(io);

    assertFalse(tree.has("id"), "id must not appear in /v2/ DataObject detail responses");
    assertTrue(tree.has("appId"), "appId must be present");
    assertEquals("018f-detail-02", tree.get("appId").asText());
    assertEquals("TR-004", tree.get("name").asText());
  }

  @Test
  void dataObjectListItemV2IO_countsField_survives_serialization() throws Exception {
    var io = makeListItem("018f-list-03", "TR-004");
    var writer = DataObjectListFieldFilter.writerFor(MAPPER, null, true);
    var json = writer.writeValueAsString(io);
    var tree = (ObjectNode) MAPPER.readTree(json);

    assertFalse(tree.has("id"), "id must not appear in /v2/ DataObject list responses");
    assertEquals(3, tree.get("timeseriesCount").asLong());
    assertEquals(5, tree.get("fileCount").asLong());
    assertEquals(2, tree.get("structuredDataCount").asLong());
  }

  // APISIMP-DO-IO-NUMERIC-ID-LEAK — extended suppression tests

  private static final java.util.List<String> SUPPRESSED_NUMERIC_FIELDS =
    java.util.List.of("id", "collectionId", "referenceIds", "successorIds",
      "predecessorIds", "childrenIds", "parentId", "incomingIds");

  @Test
  void dataObjectListItemV2IO_suppressesAllInheritedNumericIdFields() throws Exception {
    var io = makeListItem("018f-list-numeric", "AFP-Layer-002");
    var writer = DataObjectListFieldFilter.writerFor(MAPPER, null, true);
    var json = writer.writeValueAsString(io);
    var tree = (ObjectNode) MAPPER.readTree(json);

    for (var field : SUPPRESSED_NUMERIC_FIELDS) {
      assertFalse(tree.has(field),
        "Numeric id field '" + field + "' must not appear in /v2/ DataObject list responses");
    }
    assertTrue(tree.has("appId"));
  }

  @Test
  void dataObjectDetailV2IO_suppressesAllInheritedNumericIdFields() throws Exception {
    var io = new DataObjectDetailV2IO();
    io.setAppId("018f-detail-numeric");
    io.setName("TR-004-anomaly");
    var tree = (ObjectNode) MAPPER.valueToTree(io);

    for (var field : SUPPRESSED_NUMERIC_FIELDS) {
      assertFalse(tree.has(field),
        "Numeric id field '" + field + "' must not appear in /v2/ DataObject detail responses");
    }
    assertTrue(tree.has("appId"));
  }
}
