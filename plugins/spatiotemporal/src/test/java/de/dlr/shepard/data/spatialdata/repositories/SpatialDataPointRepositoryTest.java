package de.dlr.shepard.data.spatialdata.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.data.spatialdata.io.FilterCondition;
import de.dlr.shepard.data.spatialdata.io.Operator;
import de.dlr.shepard.data.spatialdata.model.GeometryBuilder;
import de.dlr.shepard.data.spatialdata.model.SpatialDataPoint;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.locationtech.jts.geom.Coordinate;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActivateRequestContext
public class SpatialDataPointRepositoryTest {

  @Inject
  SpatialDataPointRepository repository;

  Long movingTimeStamp = Instant.now().toEpochMilli() * 1_000_000;
  final Long testContainerId = 987651L;
  final int numberOfTestPoints = 100;
  final ArrayList<SpatialDataPoint> testDataPoints = new ArrayList<SpatialDataPoint>();
  private ArrayList<Long> containerIdsForCleanup = new ArrayList<Long>();

  private long generateManagedContainerId() {
    var id = 0L;
    do {
      id = (long) (ThreadLocalRandom.current().nextDouble() * 1_000);
    } while (containerIdsForCleanup.contains(id));

    containerIdsForCleanup.add(id);
    return id;
  }

  @BeforeAll
  @Transactional
  public void setup() {
    containerIdsForCleanup.add(testContainerId);
    for (var i = 0; i < numberOfTestPoints; i++) {
      testDataPoints.add(
        new SpatialDataPoint(
          testContainerId,
          generateTimestamp(),
          GeometryBuilder.fromCoordinate(new Coordinate(i, i, i)),
          Map.of("a_meta_data", "metadata_%s".formatted(i)),
          Map.of("a_measurement", i)
        )
      );
    }
    repository.insert(testContainerId, testDataPoints.toArray(new SpatialDataPoint[0]));
  }

  @AfterAll
  @Transactional
  public void teardown() {
    containerIdsForCleanup.stream().forEach(id -> repository.deleteByContainerId(id));
  }

  @Test
  @Transactional
  public void insert_dataWithMetadataAndMeasurements_allPropertiesStored() {
    var containerId = generateManagedContainerId();
    var timestamp = 1_000_000_000_000L;
    var geometry = GeometryBuilder.fromCoordinate(new Coordinate(4, 5, 6));

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("intValue", 1);
    metadata.put("floatValue", 1.2345);
    metadata.put("stringValue", "hello world");

    Map<String, Object> measurements = new HashMap<>();
    measurements.put("a", 1);
    measurements.put("b", 1.2345);
    measurements.put("c", "{\"key\":\"value\"}");

    var dataPoint = new SpatialDataPoint(containerId, timestamp, geometry, metadata, measurements);

    var result = repository.insert(containerId, dataPoint);
    assertEquals(1, result);

    var current = repository.getByContainerId(containerId);
    assertEquals(1, current.size());
    assertEquals(1_000_000_000_000L, current.getFirst().getTime());
    assertEquals("org.locationtech.jts.geom.Point", current.getFirst().getPosition().getClass().getName());
    assertEquals(4, current.getFirst().getPosition().getCoordinate().x);
    assertEquals(5, current.getFirst().getPosition().getCoordinate().y);
    assertEquals(6, current.getFirst().getPosition().getCoordinate().z);
    assertEquals(metadata, current.getFirst().getMetadata());
    assertEquals(measurements, current.getFirst().getMeasurements());
  }

  @Test
  @Transactional
  public void insert_multiplePoints_success() {
    final ArrayList<SpatialDataPoint> entryList = new ArrayList<SpatialDataPoint>();
    var containerId = generateManagedContainerId();

    for (var i = 0; i < 100; i++) {
      entryList.add(
        new SpatialDataPoint(
          containerId,
          generateTimestamp(),
          GeometryBuilder.fromCoordinate(new Coordinate(i, i, i)),
          null,
          null
        )
      );
    }

    var result = repository.insert(containerId, entryList.toArray(new SpatialDataPoint[0]));
    assertEquals(100, result);

    var current = repository.getByContainerId(containerId);
    assertEquals(100, current.size());
    assertFalse(Double.isNaN(current.getFirst().getPosition().getCoordinate().x));
    assertFalse(Double.isNaN(current.getFirst().getPosition().getCoordinate().y));
    assertFalse(Double.isNaN(current.getFirst().getPosition().getCoordinate().z));
  }

  @Test
  public void getWithoutGeometryFilter_returnsAllData_success() {
    var expected = repository.getByContainerId(testContainerId);
    var actual = repository.get(
      testContainerId,
      -1L,
      Instant.now().toEpochMilli() * 1_000_000 + 1000,
      Collections.emptyMap(),
      Collections.emptyList(),
      null,
      null
    );
    assertEquals(expected, actual);
  }

  @Test
  public void getWithoutGeometryFilter_returnsLimitData_success() {
    var actual = repository.get(
      testContainerId,
      -1L,
      Instant.now().toEpochMilli() * 1_000_000 + 1000,
      Collections.emptyMap(),
      Collections.emptyList(),
      3,
      null
    );
    assertEquals(3, actual.size());
  }

  @Test
  public void getWithoutGeometryFilter_returnsSkippedData_success() {
    var actual = repository.get(testContainerId, null, null, Collections.emptyMap(), Collections.emptyList(), null, 10);
    assertEquals(10, actual.size());
  }

  @Test
  public void getWithoutGeometryFilter_returnsFilteredData_success() {
    var expected = List.of(testDataPoints.get(1));
    var actual = repository.get(
      testContainerId,
      -1L,
      Instant.now().toEpochMilli() * 1_000_000 + 1000,
      Map.of("a_meta_data", "metadata_1"),
      List.of(new FilterCondition("a_measurement", Operator.EQUALS, 1)),
      null,
      null
    );

    assertEquals(expected, actual);
  }

  /* Bounding Box */
  @Test
  public void getByBoundingBox_returnsAllData_success() {
    var data = repository.getByBoundingBox(
      testContainerId,
      new Coordinate(0, 0, 0),
      new Coordinate(9999, 9999, 9999),
      null,
      null,
      Collections.emptyMap(),
      Collections.emptyList(),
      null,
      null
    );

    assertNotNull(data);
    assertTrue(data.size() == numberOfTestPoints);
  }

  @Test
  @Transactional
  public void getByBoundingBox_returnsSomeData_success() {
    var containerId = generateManagedContainerId();
    // Setup
    var dataPoint1 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(666, 666, 666)),
      null,
      null
    );
    var dataPoint2 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(600, 666, 600)),
      null,
      null
    );
    var dataPoint3 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(666, 700, 666)),
      null,
      null
    );
    var dataPoint4 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(700, 700, 700)),
      null,
      null
    );
    var dataPoints = new SpatialDataPoint[] { dataPoint1, dataPoint2, dataPoint3, dataPoint4 };
    var result = repository.insert(containerId, dataPoints);

    var data = repository.getByBoundingBox(
      containerId,
      new Coordinate(500, 500, 500),
      new Coordinate(700, 700, 700),
      null,
      null,
      Collections.emptyMap(),
      Collections.emptyList(),
      null,
      null
    );

    assertNotNull(data);
    assertEquals(4, result);
    assertEquals(4, data.size());

    Arrays.stream(dataPoints).forEach(dataPoint -> assertTrue(data.contains(dataPoint)));
  }

  @Test
  @Transactional
  public void getByBoundingBox_returnsSomeData_withLimit_success() {
    var containerId = generateManagedContainerId();
    // Setup
    var dataPoint1 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(866, 866, 866)),
      null,
      null
    );
    var dataPoint2 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(800, 866, 800)),
      null,
      null
    );
    var dataPoint3 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(866, 900, 866)),
      null,
      null
    );
    var dataPoint4 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(900, 900, 900)),
      null,
      null
    );
    var result = repository.insert(
      containerId,
      new SpatialDataPoint[] { dataPoint1, dataPoint2, dataPoint3, dataPoint4 }
    );

    var data = repository.getByBoundingBox(
      containerId,
      new Coordinate(700, 700, 700),
      new Coordinate(900, 900, 900),
      null,
      null,
      Collections.emptyMap(),
      Collections.emptyList(),
      3,
      null
    );

    assertNotNull(data);
    assertEquals(4, result);
    assertEquals(3, data.size());
  }

  @Test
  @Transactional
  public void getByBoundingBox_returnsSomeData_withSkip_success() {
    var containerId = generateManagedContainerId();
    // Setup
    var dataPoint1 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(766, 766, 766)),
      null,
      null
    );
    var dataPoint2 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(700, 766, 700)),
      null,
      null
    );
    var dataPoint3 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(766, 700, 766)),
      null,
      null
    );
    var dataPoint4 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(700, 700, 700)),
      null,
      null
    );
    var result = repository.insert(
      containerId,
      new SpatialDataPoint[] { dataPoint1, dataPoint2, dataPoint3, dataPoint4 }
    );

    var data = repository.getByBoundingBox(
      containerId,
      new Coordinate(700, 700, 700),
      new Coordinate(800, 800, 800),
      null,
      null,
      Collections.emptyMap(),
      Collections.emptyList(),
      null,
      2
    );

    assertNotNull(data);
    assertEquals(4, result);
    assertEquals(2, data.size());
  }

  @Test
  public void getByBoundingBox_returnsNoData_success() {
    var data = repository.getByBoundingBox(
      testContainerId,
      new Coordinate(-1, -1, -1),
      new Coordinate(-10, -10, -10),
      null,
      null,
      Collections.emptyMap(),
      Collections.emptyList(),
      null,
      null
    );

    assertNotNull(data);
    assertEquals(0, data.size());
  }

  /* Bounding Sphere */
  @Test
  public void getByBoundingSphere_returnsAllData_success() {
    var data = repository.getByBoundingSphere(
      testContainerId,
      new Coordinate(0, 0, 0),
      9999,
      null,
      null,
      Collections.emptyMap(),
      Collections.emptyList(),
      null,
      null
    );

    assertNotNull(data);
    assertTrue(data.size() == numberOfTestPoints);
  }

  @Test
  @Transactional
  public void getByBoundingSphere_returnsSomeData_success() {
    var containerId = generateManagedContainerId();
    var dataPoint1 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1666, 1666, 1666)),
      null,
      null
    );
    var dataPoint2 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1600, 1666, 1600)),
      null,
      null
    );
    var dataPoint3 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1666, 1700, 1666)),
      null,
      null
    );
    var dataPoint4 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1700, 1700, 1700)),
      null,
      null
    );
    var dataPoint5 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1800, 1800, 1800)),
      null,
      null
    );
    var dataPoints = new SpatialDataPoint[] { dataPoint1, dataPoint2, dataPoint3, dataPoint4, dataPoint5 };
    var result = repository.insert(containerId, dataPoints);

    var data = repository.getByBoundingSphere(
      containerId,
      new Coordinate(1666, 1666, 1666),
      100,
      null,
      null,
      Collections.emptyMap(),
      Collections.emptyList(),
      null,
      null
    );

    assertNotNull(data);
    assertEquals(5, result);
    assertEquals(4, data.size());
    dataPoints = new SpatialDataPoint[] { dataPoint1, dataPoint2, dataPoint3, dataPoint4 };
    Arrays.stream(dataPoints).forEach(dataPoint -> assertTrue(data.contains(dataPoint)));
  }

  @Test
  @Transactional
  public void getByBoundingSphere_returnsData_withLIMIT_success() {
    var containerId = generateManagedContainerId();
    var dataPoint1 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1666, 1666, 1666)),
      null,
      null
    );
    var dataPoint2 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1600, 1666, 1600)),
      null,
      null
    );
    var dataPoint3 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1666, 1700, 1666)),
      null,
      null
    );
    var dataPoint4 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1700, 1700, 1700)),
      null,
      null
    );
    var dataPoint5 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1800, 1800, 1800)),
      null,
      null
    );
    var result = repository.insert(
      containerId,
      new SpatialDataPoint[] { dataPoint1, dataPoint2, dataPoint3, dataPoint4, dataPoint5 }
    );

    var data = repository.getByBoundingSphere(
      containerId,
      new Coordinate(1666, 1666, 1666),
      100,
      null,
      null,
      Collections.emptyMap(),
      Collections.emptyList(),
      3,
      null
    );

    assertNotNull(data);
    assertEquals(5, result);
    assertEquals(3, data.size());
  }

  @Test
  @Transactional
  public void getByBoundingSphere_returnsData_withSkip_success() {
    var containerId = generateManagedContainerId();
    var dataPoint1 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(3980, 20, 10)),
      null,
      null
    );
    var dataPoint2 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(4020, -15, 5)),
      null,
      null
    );
    var dataPoint3 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(4010, 25, -30)),
      null,
      null
    );
    var dataPoint4 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(3995, -35, 40)),
      null,
      null
    );
    var dataPoint5 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(4000, 10, -50)),
      null,
      null
    );
    var dataPoint6 = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(4005, 0, -40)),
      null,
      null
    );
    var result = repository.insert(
      containerId,
      new SpatialDataPoint[] { dataPoint1, dataPoint2, dataPoint3, dataPoint4, dataPoint5, dataPoint6 }
    );

    var data = repository.getByBoundingSphere(
      containerId,
      new Coordinate(4000, 0, 0),
      100,
      null,
      null,
      Collections.emptyMap(),
      Collections.emptyList(),
      null,
      2
    );

    assertNotNull(data);
    assertEquals(6, result);
    assertEquals(3, data.size());
  }

  @Test
  public void getByBoundingSphere_returnsNoData_success() {
    var data = repository.getByBoundingSphere(
      testContainerId,
      new Coordinate(-10, -10, -10),
      1,
      null,
      null,
      Collections.emptyMap(),
      Collections.emptyList(),
      null,
      null
    );

    assertNotNull(data);
    assertEquals(0, data.size());
  }

  /* KNN Search */
  @Test
  public void getByKNN_returnsAllData_success() {
    var data = repository.getByKNN(
      testContainerId,
      new Coordinate(0, 0, 0),
      9999,
      null,
      null,
      Collections.emptyMap(),
      Collections.emptyList()
    );

    assertNotNull(data);
    assertTrue(data.size() == numberOfTestPoints);
  }

  @Test
  public void getByKNN_returnsSomeData_success() {
    var data = repository.getByKNN(
      testContainerId,
      new Coordinate(4, 4, 4),
      3,
      null,
      null,
      Collections.emptyMap(),
      Collections.emptyList()
    );

    assertNotNull(data);
    assertEquals(3, data.size());

    // the three nearest points from here should be (3,3,3), (5,5,5) and (4,4,4)
    for (SpatialDataPoint entry : data) {
      Log.info(entry.getPosition().getCoordinate());
      final var xCoord = entry.getPosition().getCoordinate().x;
      final var yCoord = entry.getPosition().getCoordinate().y;
      final var zCoord = entry.getPosition().getCoordinate().z;
      assertTrue(xCoord == 3 || xCoord == 5 || xCoord == 4);
      assertTrue(yCoord == 3 || yCoord == 5 || yCoord == 4);
      assertTrue(zCoord == 3 || zCoord == 5 || zCoord == 4);
    }
  }

  @Test
  public void getByKNN_returnsNoData_success() {
    var data = repository.getByKNN(
      testContainerId,
      new Coordinate(-1, -1, -1),
      0,
      null,
      null,
      Collections.emptyMap(),
      Collections.emptyList()
    );

    assertNotNull(data);
    assertEquals(0, data.size());
  }

  @Test
  @Transactional
  public void deleteData_byContainerId_success() {
    var containerId = generateManagedContainerId();
    repository.insert(
      containerId,
      new SpatialDataPoint(containerId, generateTimestamp(), GeometryBuilder.fromXYZ(1, 1, 1), null, null)
    );

    int numberDeletedRows = repository.deleteByContainerId(containerId);
    assertTrue(numberDeletedRows > 0);
  }

  /***** Test of metadata field and jsonb queries */
  @Test
  @Transactional
  public void insert_storeMetadataWithNestedObject_success() {
    var containerId = generateManagedContainerId();

    var nestedObject = new HashMap<String, Object>();
    nestedObject.put("temperature", 23.4);
    nestedObject.put("humidity", 0.6);
    nestedObject.put("room", "living room");

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("track", 1);
    metadata.put("layer", 7);
    metadata.put("floatValue", 1.2345);
    metadata.put("stringValue", "hello world");
    metadata.put("sensors", nestedObject);

    var dataPoint = new SpatialDataPoint(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1, 2, 3)),
      metadata,
      null
    );
    var result = repository.insert(containerId, dataPoint);
    assertEquals(1, result);
  }

  @Test
  @Transactional
  public void getByBoundingBox_filterByMetadata_returnsOneRecord() {
    var containerId = generateManagedContainerId();
    var metadataFilter = new HashMap<String, Object>();
    metadataFilter.put("layer", 7);
    repository.insert(
      containerId,
      new SpatialDataPoint(containerId, 1l, GeometryBuilder.fromXYZ(1, 1, 1), metadataFilter, null)
    );

    var data = repository.getByBoundingBox(
      containerId,
      new Coordinate(0, 0, 0),
      new Coordinate(2, 2, 2),
      null,
      null,
      metadataFilter,
      Collections.emptyList(),
      null,
      null
    );

    assertNotNull(data);
    assertTrue(data.size() == 1);
  }

  @Test
  @Transactional
  public void getByKNN_filterByMetadata_returnsOneRecord() {
    var containerId = generateManagedContainerId();
    var metadataFilter = new HashMap<String, Object>();
    metadataFilter.put("layer", 7);
    var dataPoint = new SpatialDataPoint(containerId, 1l, GeometryBuilder.fromXYZ(1, 1, 1), metadataFilter, null);
    repository.insert(containerId, dataPoint);

    var data = repository.getByKNN(
      containerId,
      new Coordinate(0, 0, 0),
      1,
      null,
      null,
      metadataFilter,
      Collections.emptyList()
    );

    assertNotNull(data);
    assertTrue(data.size() == 1);
    assertTrue(data.getFirst().equals(dataPoint));
  }

  @Test
  @Transactional
  public void getByKNN_filterByNestedMetadata_returnsOneRecord() {
    var containerId = generateManagedContainerId();
    var nestedObject = new HashMap<String, Object>();
    nestedObject.put("temperature", 23.4);
    nestedObject.put("humidity", 0.6);
    nestedObject.put("room", "living room");

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("track", 1);
    metadata.put("layer", 7);
    metadata.put("floatValue", 1.2345);
    metadata.put("stringValue", "hello world");
    metadata.put("sensors", nestedObject);
    var dataPoint = new SpatialDataPoint(containerId, 1l, GeometryBuilder.fromXYZ(1, 1, 1), metadata, null);
    repository.insert(containerId, dataPoint);

    var metadataFilter = new HashMap<String, Object>();
    metadataFilter.put("layer", 7);
    metadataFilter.put("sensors", Map.of("room", "living room", "humidity", 0.6));

    var data = repository.getByKNN(
      containerId,
      new Coordinate(0, 0, 0),
      1,
      null,
      null,
      metadataFilter,
      Collections.emptyList()
    );

    assertNotNull(data);
    assertTrue(data.size() == 1);
    assertTrue(data.getFirst().equals(dataPoint));
  }

  @Test
  @Transactional
  public void getByKNN_filterByNestedMetadata_returnsNoData() {
    var containerId = generateManagedContainerId();
    var nestedObject = new HashMap<String, Object>();
    nestedObject.put("temperature", 23.4);
    nestedObject.put("humidity", 0.6);
    nestedObject.put("room", "living room");

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("track", 1);
    metadata.put("layer", 7);
    metadata.put("floatValue", 1.2345);
    metadata.put("stringValue", "hello world");
    metadata.put("sensors", nestedObject);
    var dataPoint = new SpatialDataPoint(containerId, 1l, GeometryBuilder.fromXYZ(1, 1, 1), metadata, null);
    repository.insert(containerId, dataPoint);

    var metadataFilter = new HashMap<String, Object>();
    metadataFilter.put("layer", 7);
    metadataFilter.put("sensors", Map.of("room", "dining room", "humidity", 0.6));

    var data = repository.getByKNN(
      containerId,
      new Coordinate(0, 0, 0),
      1,
      null,
      null,
      metadataFilter,
      Collections.emptyList()
    );

    assertNotNull(data);
    assertTrue(data.size() == 0);
  }

  @Test
  @Transactional
  public void getByKNN_filterByTimestamp_returnsOneRecord() {
    var containerId = generateManagedContainerId();
    var dataPoint1 = new SpatialDataPoint(containerId, 1l, GeometryBuilder.fromXYZ(1, 1, 1), null, null);
    var dataPoint2 = new SpatialDataPoint(containerId, 10l, GeometryBuilder.fromXYZ(1, 1, 1), null, null);
    repository.insert(containerId, new SpatialDataPoint[] { dataPoint1, dataPoint2 });

    var data = repository.getByKNN(
      containerId,
      new Coordinate(0, 0, 0),
      1,
      0l,
      2l,
      Collections.emptyMap(),
      Collections.emptyList()
    );

    assertNotNull(data);
    assertEquals(1, data.size());
    assertEquals(dataPoint1, data.getFirst());
  }

  @Test
  @Transactional
  public void getByKNN_filterByTimestamp_returnsNoData() {
    var containerId = generateManagedContainerId();
    repository.insert(containerId, new SpatialDataPoint(containerId, 1l, GeometryBuilder.fromXYZ(1, 1, 1), null, null));

    var data = repository.getByKNN(
      containerId,
      new Coordinate(0, 0, 0),
      1,
      2l,
      4l,
      Collections.emptyMap(),
      Collections.emptyList()
    );

    assertNotNull(data);
    assertEquals(0, data.size());
  }

  @Test
  @Transactional
  public void getByKNN_filterByTimestampStart_returnsOneRecord() {
    var containerId = generateManagedContainerId();
    var dataPoint1 = new SpatialDataPoint(containerId, 1l, GeometryBuilder.fromXYZ(1, 1, 1), null, null);
    var dataPoint2 = new SpatialDataPoint(containerId, 10l, GeometryBuilder.fromXYZ(10, 10, 10), null, null);
    repository.insert(containerId, new SpatialDataPoint[] { dataPoint1, dataPoint2 });

    var data = repository.getByKNN(
      containerId,
      new Coordinate(0, 0, 0),
      1,
      0l,
      null,
      Collections.emptyMap(),
      Collections.emptyList()
    );

    assertNotNull(data);
    assertEquals(1, data.size());
    assertEquals(dataPoint1, data.getFirst());
  }

  @Test
  @Transactional
  public void getByKNN_filterByTimestampEnd_returnsOneRecord() {
    var containerId = generateManagedContainerId();
    var dataPoint1 = new SpatialDataPoint(containerId, 1l, GeometryBuilder.fromXYZ(1, 1, 1), null, null);
    var dataPoint2 = new SpatialDataPoint(containerId, 10l, GeometryBuilder.fromXYZ(1, 1, 1), null, null);
    repository.insert(containerId, new SpatialDataPoint[] { dataPoint1, dataPoint2 });

    var data = repository.getByKNN(
      containerId,
      new Coordinate(0, 0, 0),
      1,
      null,
      2l,
      Collections.emptyMap(),
      Collections.emptyList()
    );

    assertNotNull(data);
    assertEquals(1, data.size());
    assertEquals(dataPoint1, data.getFirst());
  }

  @Test
  @Transactional
  public void getByKNN_filterByMetadataAndTimestamp_returnsOneRecord() {
    var containerId = generateManagedContainerId();
    var metadata = new HashMap<String, Object>();
    metadata.put("layer", 7);
    metadata.put("track", 10);

    var dataPoint1 = new SpatialDataPoint(containerId, 1l, GeometryBuilder.fromXYZ(1, 1, 1), metadata, null);
    var dataPoint2 = new SpatialDataPoint(containerId, 10l, GeometryBuilder.fromXYZ(2, 2, 2), metadata, null);
    repository.insert(containerId, new SpatialDataPoint[] { dataPoint1, dataPoint2 });

    var metadataFilter = new HashMap<String, Object>();
    metadataFilter.put("layer", 7);
    var data = repository.getByKNN(
      containerId,
      new Coordinate(0, 0, 0),
      1,
      0L,
      2L,
      metadataFilter,
      Collections.emptyList()
    );

    assertNotNull(data);
    assertTrue(data.size() == 1);
    assertTrue(data.getFirst().equals(dataPoint1));
  }

  @Test
  @Transactional
  public void getByKNN_filterByMetadataAndTimestamp_returnsNoData() {
    var containerId = generateManagedContainerId();
    var metadata = new HashMap<String, Object>();
    metadata.put("layer", 7);
    metadata.put("track", 10);

    var dataPoint1 = new SpatialDataPoint(containerId, 1l, GeometryBuilder.fromXYZ(1, 1, 1), metadata, null);
    var dataPoint2 = new SpatialDataPoint(containerId, 10l, GeometryBuilder.fromXYZ(2, 2, 2), metadata, null);
    repository.insert(containerId, new SpatialDataPoint[] { dataPoint1, dataPoint2 });

    var metadataFilter = new HashMap<String, Object>();
    metadataFilter.put("layer", 0);
    var data = repository.getByKNN(
      containerId,
      new Coordinate(0, 0, 0),
      1,
      10L,
      20L,
      metadataFilter,
      Collections.emptyList()
    );

    assertNotNull(data);
    assertTrue(data.size() == 0);
  }

  @Test
  @Transactional
  public void getByKNN_filterByMeasurements_returnsOneRecord() {
    var containerId = generateManagedContainerId();
    var measurments = new HashMap<String, Object>();
    var imageData = new HashMap<String, Object>();
    imageData.put("format", "png");
    imageData.put("size", 5000);
    measurments.put("temperature", 7);
    measurments.put("pressure", 10);
    measurments.put("image", imageData);

    var dataPoint1 = new SpatialDataPoint(containerId, 1l, GeometryBuilder.fromXYZ(1, 1, 1), null, measurments);
    imageData.put("size", 3000);
    measurments.put("image", imageData);
    var dataPoint2 = new SpatialDataPoint(containerId, 10l, GeometryBuilder.fromXYZ(2, 2, 2), null, measurments);
    repository.insert(containerId, new SpatialDataPoint[] { dataPoint1, dataPoint2 });

    var measurementsFilter = List.of(
      new FilterCondition("image,size", Operator.LESS_THAN, 5000),
      new FilterCondition("image,size", Operator.GREATER_THAN, 2000),
      new FilterCondition("temperature", Operator.EQUALS, 7)
    );
    var data = repository.getByKNN(
      containerId,
      new Coordinate(0, 0, 0),
      1,
      null,
      null,
      Collections.emptyMap(),
      measurementsFilter
    );

    assertNotNull(data);
    assertTrue(data.size() == 1);
  }

  @Test
  @Transactional
  public void getByKNN_filterByMeasurements_returnsNoData() {
    var containerId = generateManagedContainerId();
    var measurments = new HashMap<String, Object>();
    var imageData = new HashMap<String, Object>();
    imageData.put("format", "png");
    imageData.put("size", 5000);
    measurments.put("temperature", 7);
    measurments.put("pressure", 10);
    measurments.put("image", imageData);

    var dataPoint1 = new SpatialDataPoint(containerId, 1l, GeometryBuilder.fromXYZ(1, 1, 1), null, measurments);
    imageData.put("size", 3000);
    measurments.put("image", imageData);
    var dataPoint2 = new SpatialDataPoint(containerId, 10l, GeometryBuilder.fromXYZ(2, 2, 2), null, measurments);
    repository.insert(containerId, new SpatialDataPoint[] { dataPoint1, dataPoint2 });

    var measurementsFilter = List.of(new FilterCondition("temperature", Operator.EQUALS, 10));
    var data = repository.getByKNN(
      containerId,
      new Coordinate(0, 0, 0),
      1,
      null,
      null,
      Collections.emptyMap(),
      measurementsFilter
    );

    assertNotNull(data);
    assertTrue(data.isEmpty());
  }

  public void getByKNN_returnsSomeData_withSkip() {
    var containerId = generateManagedContainerId();
    var metadata = new HashMap<String, Object>();
    metadata.put("layer", 16);
    metadata.put("track", 16);

    var dataPoint1 = new SpatialDataPoint(containerId, 1l, GeometryBuilder.fromXYZ(1, 1, 1), metadata, null);
    var dataPoint2 = new SpatialDataPoint(containerId, 10l, GeometryBuilder.fromXYZ(2, 2, 2), metadata, null);
    var dataPoint3 = new SpatialDataPoint(containerId, 5l, GeometryBuilder.fromXYZ(3, 3, 3), metadata, null);
    var dataPoint4 = new SpatialDataPoint(containerId, 9l, GeometryBuilder.fromXYZ(4, 4, 4), metadata, null);
    repository.insert(containerId, new SpatialDataPoint[] { dataPoint1, dataPoint2, dataPoint3, dataPoint4 });

    var metadataFilter = new HashMap<String, Object>();
    metadataFilter.put("layer", 16);
    var data = repository.getByKNN(
      containerId,
      new Coordinate(0, 0, 0),
      4,
      0L,
      20L,
      metadataFilter,
      Collections.emptyList()
    );
    assertNotNull(data);
    assertEquals(2, data.size());
  }

  private Long generateTimestamp() {
    final Long timestamp = movingTimeStamp;
    movingTimeStamp += 10;
    return timestamp;
  }
}
