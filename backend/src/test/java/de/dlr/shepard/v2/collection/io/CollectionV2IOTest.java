package de.dlr.shepard.v2.collection.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.context.collection.entities.Collection;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-BASICENTITY-DROP-ID slice 2 + APISIMP-COLL-IO-NUMERIC-ID-LEAK —
 * verifies that {@link CollectionV2IO} suppresses all inherited Neo4j internal
 * numeric id fields from the /v2/ wire shape while preserving {@code appId}
 * and collection-specific fields.
 *
 * <p>Numeric fields suppressed: {@code id}, {@code dataObjectIds},
 * {@code incomingIds}, {@code defaultFileContainerId}.
 */
class CollectionV2IOTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Collection collection(String appId, String name) {
    var c = new Collection();
    c.setAppId(appId);
    c.setName(name);
    return c;
  }

  @Test
  void collectionV2IO_serializes_withoutId() throws Exception {
    var io = new CollectionV2IO(collection("018f-abc", "TR-001"));
    var tree = (ObjectNode) MAPPER.valueToTree(io);

    assertFalse(tree.has("id"), "id must not appear in /v2/ collection responses");
    assertTrue(tree.has("appId"), "appId must be present");
    assertEquals("018f-abc", tree.get("appId").asText());
    assertEquals("TR-001", tree.get("name").asText());
  }

  @Test
  void collectionV2IO_preserves_v2Fields() throws Exception {
    var c = collection("019a-xyz", "MFFD-Q1");
    c.setHeroImageUrl("https://example.com/img.png");
    var io = new CollectionV2IO(c);
    var tree = (ObjectNode) MAPPER.valueToTree(io);

    assertFalse(tree.has("id"), "id must not appear in /v2/ responses");
    assertEquals("https://example.com/img.png", tree.get("heroImageUrl").asText());
    assertEquals("019a-xyz", tree.get("appId").asText());
  }

  @Test
  void collectionV2IO_noArgConstructor_compiles() {
    var io = new CollectionV2IO();
    io.setAppId("uuid-test");
    assertEquals("uuid-test", io.getAppId());
  }

  // APISIMP-COLL-IO-NUMERIC-ID-LEAK — extended suppression tests

  private static final java.util.List<String> SUPPRESSED_COLLECTION_NUMERIC_FIELDS =
    java.util.List.of("id", "dataObjectIds", "incomingIds", "defaultFileContainerId");

  @Test
  void collectionV2IO_suppressesAllInheritedNumericIdFields() throws Exception {
    var io = new CollectionV2IO(collection("018f-coll-numeric", "MFFD-Q1"));
    var tree = (ObjectNode) MAPPER.valueToTree(io);

    for (var field : SUPPRESSED_COLLECTION_NUMERIC_FIELDS) {
      assertFalse(tree.has(field),
        "Numeric id field '" + field + "' must not appear in /v2/ Collection responses");
    }
    assertTrue(tree.has("appId"));
    assertEquals("018f-coll-numeric", tree.get("appId").asText());
  }
}
