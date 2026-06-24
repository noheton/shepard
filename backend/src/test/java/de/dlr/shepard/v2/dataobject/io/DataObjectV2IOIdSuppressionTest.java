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
 * APISIMP-DO-IO-NUMERIC-ID-LEAK — verifies that
 * {@link DataObjectListItemV2IO} and {@link DataObjectDetailV2IO}
 * suppress all inherited numeric Neo4j id arrays and deprecated int counts
 * from the /v2/ wire shape, in addition to the node-internal {@code id}.
 *
 * <p>Both classes extend the shared v1 {@link de.dlr.shepard.context.collection.io.DataObjectIO}
 * (which exposes numeric arrays and counts). The v2 subclasses suppress them
 * via {@code @JsonIgnoreProperties} so v2 callers address entities by appId only.
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

  @Test
  void dataObjectListItemV2IO_suppressesNumericIdArrays() throws Exception {
    // APISIMP-DO-IO-NUMERIC-ID-LEAK: all inherited numeric arrays from DataObjectIO
    // must be absent from the v2 wire shape (even when ?include=full is requested).
    var io = makeListItem("018f-list-04", "TR-004");
    var writer = DataObjectListFieldFilter.writerFor(MAPPER, null, true);
    var json = writer.writeValueAsString(io);
    var tree = (ObjectNode) MAPPER.readTree(json);

    assertFalse(tree.has("collectionId"), "collectionId (numeric) must not appear on /v2/");
    assertFalse(tree.has("referenceIds"), "referenceIds (numeric[]) must not appear on /v2/");
    assertFalse(tree.has("successorIds"), "successorIds (numeric[]) must not appear on /v2/");
    assertFalse(tree.has("predecessorIds"), "predecessorIds (numeric[]) must not appear on /v2/");
    assertFalse(tree.has("childrenIds"), "childrenIds (numeric[]) must not appear on /v2/");
    assertFalse(tree.has("parentId"), "parentId (numeric) must not appear on /v2/");
    assertFalse(tree.has("incomingIds"), "incomingIds (numeric[]) must not appear on /v2/");
    assertFalse(tree.has("timeseriesReferenceCount"), "deprecated int count must not appear on /v2/");
    assertFalse(tree.has("fileBundleCount"), "deprecated int count must not appear on /v2/");
    assertFalse(tree.has("structuredDataReferenceCount"), "deprecated int count must not appear on /v2/");
    // v2 replacements must still be present
    assertTrue(tree.has("appId"), "appId must be present");
    assertTrue(tree.has("timeseriesCount"), "v2 long count must remain");
    assertTrue(tree.has("fileCount"), "v2 long count must remain");
    assertTrue(tree.has("structuredDataCount"), "v2 long count must remain");
  }

  @Test
  void dataObjectDetailV2IO_suppressesNumericIdArrays() throws Exception {
    // APISIMP-DO-IO-NUMERIC-ID-LEAK: all inherited numeric arrays from DataObjectIO
    // must be absent from the v2 detail wire shape.
    var io = new DataObjectDetailV2IO();
    io.setAppId("018f-detail-05");
    io.setName("TR-004");
    var tree = (ObjectNode) MAPPER.valueToTree(io);

    assertFalse(tree.has("collectionId"), "collectionId (numeric) must not appear on /v2/");
    assertFalse(tree.has("referenceIds"), "referenceIds (numeric[]) must not appear on /v2/");
    assertFalse(tree.has("successorIds"), "successorIds (numeric[]) must not appear on /v2/");
    assertFalse(tree.has("predecessorIds"), "predecessorIds (numeric[]) must not appear on /v2/");
    assertFalse(tree.has("childrenIds"), "childrenIds (numeric[]) must not appear on /v2/");
    assertFalse(tree.has("parentId"), "parentId (numeric) must not appear on /v2/");
    assertFalse(tree.has("incomingIds"), "incomingIds (numeric[]) must not appear on /v2/");
    assertFalse(tree.has("timeseriesReferenceCount"), "deprecated int count must not appear on /v2/");
    assertFalse(tree.has("fileBundleCount"), "deprecated int count must not appear on /v2/");
    assertFalse(tree.has("structuredDataReferenceCount"), "deprecated int count must not appear on /v2/");
    // v2 shape must still surface appId and typed container lists
    assertTrue(tree.has("appId"), "appId must be present");
    assertTrue(tree.has("containers"), "typed container list must be present");
    assertTrue(tree.has("predecessorSummaries"), "predecessorSummaries must be present");
    assertTrue(tree.has("successorSummaries"), "successorSummaries must be present");
  }
}
