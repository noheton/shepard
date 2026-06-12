package de.dlr.shepard.v2.collection.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.context.collection.entities.Collection;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-BASICENTITY-DROP-ID slice 2 — verifies that {@link CollectionV2IO}
 * suppresses the Neo4j internal {@code id} field from the /v2/ wire shape
 * while preserving {@code appId} and collection-specific fields.
 *
 * <p>The v1 {@code /shepard/api/} wire shape is not tested here; it is
 * covered by the existing v1 tests and must remain unchanged.
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
}
