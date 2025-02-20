package de.dlr.shepard.data.spatialdata.endpoints;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.*;

import de.dlr.shepard.data.spatialdata.model.AbstractGeometryFilter;
import de.dlr.shepard.data.spatialdata.model.AxisAlignedBoundingBox;
import de.dlr.shepard.data.spatialdata.model.BoundingSphere;
import de.dlr.shepard.data.spatialdata.model.GeometryFilterType;
import de.dlr.shepard.data.spatialdata.model.KNearestNeighbor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpatialDataParamParserTest {

  @Test
  void testParse_KNearestNeighbor_success() {
    String json =
      """
      {
        "type": "K_NEAREST_NEIGHBOR",
        "k": 5,
        "x": 10,
        "y": 20,
        "z": 30
      }""";

    AbstractGeometryFilter filter = SpatialDataParamParser.parseGeometryFilter(Optional.of(json)).orElse(null);
    assertNotNull(filter);
    assertEquals(GeometryFilterType.K_NEAREST_NEIGHBOR, filter.getType());
    assertTrue(filter instanceof KNearestNeighbor);
    KNearestNeighbor knnFilter = (KNearestNeighbor) filter;
    assertEquals(5, knnFilter.getK());
    assertEquals(10, knnFilter.getX());
    assertEquals(20, knnFilter.getY());
    assertEquals(30, knnFilter.getZ());
  }

  @Test
  void testParse_axisAlignedBoundingBox_success() {
    String json =
      """
      {
        "type": "AXIS_ALIGNED_BOUNDING_BOX",
        "minX": 0,
        "minY": 0,
        "minZ": 0,
        "maxX": 100,
        "maxY": 100,
        "maxZ": 100
      }""";

    AbstractGeometryFilter filter = SpatialDataParamParser.parseGeometryFilter(Optional.of(json)).orElse(null);
    assertNotNull(filter);
    assertEquals(GeometryFilterType.AXIS_ALIGNED_BOUNDING_BOX, filter.getType());
    assertTrue(filter instanceof AxisAlignedBoundingBox);
    AxisAlignedBoundingBox aabbFilter = (AxisAlignedBoundingBox) filter;
    assertEquals(0, aabbFilter.getMinX());
    assertEquals(0, aabbFilter.getMinY());
    assertEquals(0, aabbFilter.getMinZ());
    assertEquals(100, aabbFilter.getMaxX());
    assertEquals(100, aabbFilter.getMaxY());
    assertEquals(100, aabbFilter.getMaxZ());
  }

  @Test
  void testParse_boundingSphere_success() {
    String json =
      """
      {
        "type": "BOUNDING_SPHERE",
        "r": 50,
        "centerX": 15,
        "centerY": 25,
        "centerZ": 20
      }""";

    AbstractGeometryFilter filter = SpatialDataParamParser.parseGeometryFilter(Optional.of(json)).orElse(null);
    assertNotNull(filter);
    assertEquals(GeometryFilterType.BOUNDING_SPHERE, filter.getType());
    assertTrue(filter instanceof BoundingSphere);
    BoundingSphere sphereFilter = (BoundingSphere) filter;
    assertEquals(50, sphereFilter.getR());
    assertEquals(15, sphereFilter.getCenterX());
    assertEquals(25, sphereFilter.getCenterY());
    assertEquals(20, sphereFilter.getCenterZ());
  }

  @Test
  void testParse_invalidGeometryFilter_throwsException() {
    String invalidJson = "{ invalid json }";

    RuntimeException exception = assertThrows(RuntimeException.class, () -> {
      SpatialDataParamParser.parseGeometryFilter(Optional.of(invalidJson));
    });

    assertEquals("Invalid geometry filter param", exception.getMessage());
  }

  @Test
  void testParse_validMetadata_success() {
    String json =
      """
      {
        "key1": "value1",
        "key2": 2,
        "key3" : {
          "child key" : 2
        },
        "key4" : [1,2,3,4,5]
      }
      """;

    Map<String, Object> metadata = SpatialDataParamParser.parseMetadata(Optional.of(json)).orElse(null);
    assertNotNull(metadata);
    assertEquals("value1", metadata.get("key1"));
    assertEquals(2, metadata.get("key2"));

    @SuppressWarnings("unchecked")
    Map<String, Object> childEntry = (LinkedHashMap<String, Object>) metadata.get("key3");

    Map<String, Object> expectedChild = Map.ofEntries(entry("child key", 2));
    assertEquals(expectedChild, metadata.get("key3"));
    assertEquals(2, childEntry.get("child key"));
    assertEquals(List.of(1, 2, 3, 4, 5), metadata.get("key4"));
  }

  @Test
  void testParse_validComplexMetadata_success() {
    String json =
      """
      {
        "level1": {
          "level2": {
            "data": "deep nested value",
            "boolValue": false,
            "intValue":123
          }
        }
      }
        """;

    Map<String, Object> metadata = SpatialDataParamParser.parseMetadata(Optional.of(json)).orElse(null);
    assertNotNull(metadata);
    @SuppressWarnings("unchecked")
    Map<String, Object> level2 = (Map<String, Object>) ((Map<String, Object>) (metadata.get("level1"))).get("level2");
    assertEquals("deep nested value", level2.get("data"));
    assertEquals(false, level2.get("boolValue"));
    assertEquals(123, level2.get("intValue"));
  }

  @Test
  void testParse_invalidMetadata_throwsException() {
    String invalidJson = "{ invalid json }";

    RuntimeException exception = assertThrows(RuntimeException.class, () -> {
      SpatialDataParamParser.parseMetadata(Optional.of(invalidJson));
    });

    assertEquals("Invalid metadata param", exception.getMessage());
  }

  @Test
  void testParse_metadata_returnsNull() {
    Map<String, Object> metadata = SpatialDataParamParser.parseMetadata(Optional.empty()).orElse(null);
    assertNull(metadata);
  }

  @Test
  void testParse_geometryFilter_returnsNull() {
    AbstractGeometryFilter filter = SpatialDataParamParser.parseGeometryFilter(Optional.empty()).orElse(null);
    assertNull(filter);
  }
}
