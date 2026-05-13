package de.dlr.shepard.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PublishableKindRegistryTest {

  private final PublishableKindRegistry registry = new PublishableKindRegistry();

  @Test
  void resolveDataObjectsSegment() {
    var k = registry.bySegment("data-objects");
    assertTrue(k.isPresent());
    assertEquals(PublishableKind.DATA_OBJECTS, k.get());
    assertEquals("DataObject", k.get().neo4jLabel());
    assertEquals("http://shepard.dlr.de/types/dlr:DataObject", k.get().digitalObjectType());
  }

  @Test
  void resolveCollectionsSegment() {
    var k = registry.bySegment("collections");
    assertTrue(k.isPresent());
    assertEquals(PublishableKind.COLLECTIONS, k.get());
    assertEquals("Collection", k.get().neo4jLabel());
  }

  @Test
  void unknownSegmentYieldsEmpty() {
    assertFalse(registry.bySegment("file-references").isPresent());
    assertFalse(registry.bySegment(null).isPresent());
    assertFalse(registry.bySegment("DataObjects").isPresent()); // case-sensitive
  }

  @Test
  void byNeo4jLabelRoundTrips() {
    var k = registry.byNeo4jLabel("DataObject");
    assertTrue(k.isPresent());
    assertEquals("data-objects", k.get().urlSegment());
    var k2 = registry.byNeo4jLabel("Collection");
    assertEquals("collections", k2.get().urlSegment());
  }

  @Test
  void unknownLabelYieldsEmpty() {
    assertFalse(registry.byNeo4jLabel("FileGroup").isPresent());
    assertFalse(registry.byNeo4jLabel(null).isPresent());
  }

  @Test
  void supportedSegmentsContainsBothKinds() {
    var segs = registry.supportedSegments();
    assertTrue(segs.contains("data-objects"));
    assertTrue(segs.contains("collections"));
    assertEquals(2, segs.size());
  }
}
