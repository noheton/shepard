package de.dlr.shepard.data.spatialdata.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.data.spatialdata.model.GeometryBuilder;
import de.dlr.shepard.data.spatialdata.model.SpatialGeometry;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.locationtech.jts.geom.Coordinate;

@QuarkusTest
//TODO: do not use test method ordering, but properly setup and delete data in setup/beforeall and afterall steps
@TestMethodOrder(OrderAnnotation.class)
public class SpatialGeometryRepositoryTest {

  @Inject
  SpatialGeometryRepository repository;

  Long movingTimeStamp = Instant.now().toEpochMilli() * 1_000_000;
  final Long containerId = 987651L;

  @Test
  @Order(1)
  @Transactional
  public void insert_singlePoint_success() {
    var dataPoint = new SpatialGeometry(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(0, 0, 0)),
      null,
      null
    );
    var result = repository.insert(containerId, dataPoint);
    assertEquals(1, result);
  }

  @Test
  @Order(2)
  @Transactional
  public void insert_storeMetadata_success() {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("track", 1);
    metadata.put("layer", 7);
    metadata.put("floatValue", 1.2345);
    metadata.put("stringValue", "hello world");

    var dataPoint = new SpatialGeometry(
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
  @Order(3)
  @Transactional
  public void insert_storeMeasurement_success() {
    Map<String, Object> measurements = new HashMap<>();
    measurements.put("a", 1);
    measurements.put("b", 7);
    measurements.put("c", 1.2345);
    measurements.put("d", new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });

    var dataPoint = new SpatialGeometry(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(4, 5, 6)),
      null,
      measurements
    );
    var result = repository.insert(containerId, dataPoint);
    assertEquals(1, result);
  }

  @Test
  @Order(4)
  @Transactional
  public void insert_multiplePoints_success() {
    final ArrayList<SpatialGeometry> entryList = new ArrayList<SpatialGeometry>();

    for (var i = 0; i < 100; i++) {
      entryList.add(
        new SpatialGeometry(
          containerId,
          generateTimestamp(),
          GeometryBuilder.fromCoordinate(new Coordinate(i, i, i)),
          null,
          null
        )
      );
    }

    var result = repository.insertMultiple(containerId, entryList.toArray(new SpatialGeometry[0]));
    assertEquals(100, result);
  }

  @Test
  @Order(5)
  public void getAll_returnsData_success() {
    var data = repository.getAll();

    assertNotNull(data);
    assertTrue(data.size() > 0);
  }

  @Test
  @Order(6)
  public void getAllCustom_returnsData_success() {
    var data = repository.getAllCustom();

    assertNotNull(data);
    assertTrue(data.size() > 0);
  }

  /* Bounding Box */
  @Test
  @Order(7)
  public void getByBoundingBox_returnsAllData_success() {
    var data = repository.getByBoundingBox(containerId, new Coordinate(0, 0, 0), new Coordinate(9999, 9999, 9999));

    assertNotNull(data);
    assertTrue(data.size() > 100);
  }

  @Test
  @Order(8)
  @Transactional
  public void getByBoundingBox_returnsSomeData_success() {
    // Setup
    var dataPoint1 = new SpatialGeometry(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(666, 666, 666)),
      null,
      null
    );
    var dataPoint2 = new SpatialGeometry(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(600, 666, 600)),
      null,
      null
    );
    var dataPoint3 = new SpatialGeometry(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(666, 700, 666)),
      null,
      null
    );
    var dataPoint4 = new SpatialGeometry(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(700, 700, 700)),
      null,
      null
    );
    var result = repository.insertMultiple(
      containerId,
      new SpatialGeometry[] { dataPoint1, dataPoint2, dataPoint3, dataPoint4 }
    );

    var data = repository.getByBoundingBox(containerId, new Coordinate(500, 500, 500), new Coordinate(700, 700, 700));

    assertNotNull(data);
    assertEquals(4, result);
    assertEquals(4, data.size());
  }

  @Test
  @Order(9)
  public void getByBoundingBox_returnsNoData_success() {
    var data = repository.getByBoundingBox(containerId, new Coordinate(-1, -1, -1), new Coordinate(-10, -10, -10));

    assertNotNull(data);
    assertEquals(0, data.size());
  }

  /* Bounding Sphere */
  @Test
  @Order(10)
  public void getByBoundingSphere_returnsAllData_success() {
    var data = repository.getByBoundingSphere(containerId, new Coordinate(0, 0, 0), 9999);

    assertNotNull(data);
    assertTrue(data.size() > 100);
  }

  @Test
  @Order(11)
  @Transactional
  public void getByBoundingSphere_returnsSomeData_success() {
    var dataPoint1 = new SpatialGeometry(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1666, 1666, 1666)),
      null,
      null
    );
    var dataPoint2 = new SpatialGeometry(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1600, 1666, 1600)),
      null,
      null
    );
    var dataPoint3 = new SpatialGeometry(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1666, 1700, 1666)),
      null,
      null
    );
    var dataPoint4 = new SpatialGeometry(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1700, 1700, 1700)),
      null,
      null
    );
    var dataPoint5 = new SpatialGeometry(
      containerId,
      generateTimestamp(),
      GeometryBuilder.fromCoordinate(new Coordinate(1800, 1800, 1800)),
      null,
      null
    );
    var result = repository.insertMultiple(
      containerId,
      new SpatialGeometry[] { dataPoint1, dataPoint2, dataPoint3, dataPoint4, dataPoint5 }
    );

    var data = repository.getByBoundingSphere(containerId, new Coordinate(1666, 1666, 1666), 100);

    assertNotNull(data);
    assertEquals(5, result);
    assertEquals(4, data.size());
  }

  @Test
  @Order(12)
  public void getByBoundingSphere_returnsNoData_success() {
    var data = repository.getByBoundingSphere(containerId, new Coordinate(-10, -10, -10), 1);

    assertNotNull(data);
    assertEquals(0, data.size());
  }

  /* KNN Search */
  @Test
  @Order(13)
  public void getByKNN_returnsAllData_success() {
    var data = repository.getByKNN(containerId, new Coordinate(0, 0, 0), 9999);

    assertNotNull(data);
    assertTrue(data.size() > 100);
  }

  @Test
  @Order(14)
  public void getByKNN_returnsSomeData_success() {
    var data = repository.getByKNN(containerId, new Coordinate(4, 4, 4), 3);

    assertNotNull(data);
    assertEquals(3, data.size());

    // the two nearest points from here should be (3,3,3), (5,5,5) and (4,4,4)
    for (SpatialGeometry entry : data) {
      Log.info(entry.getGeometry().getCoordinate());
      final var xCoord = entry.getGeometry().getCoordinate().x;
      final var yCoord = entry.getGeometry().getCoordinate().y;
      final var zCoord = entry.getGeometry().getCoordinate().z;
      assertTrue(xCoord == 3 || xCoord == 5 || xCoord == 4);
      assertTrue(yCoord == 3 || yCoord == 5 || xCoord == 4);
      assertTrue(zCoord == 3 || zCoord == 5 || xCoord == 4);
    }
  }

  @Test
  @Order(15)
  public void getByKNN_returnsNoData_success() {
    var data = repository.getByKNN(containerId, new Coordinate(-1, -1, -1), 0);

    assertNotNull(data);
    assertEquals(0, data.size());
  }

  @Test
  @Order(16)
  @Transactional
  public void deleteData_byContainerId_success() {
    int numberDeletedRows = repository.deleteByContainerId(containerId);
    assertTrue(numberDeletedRows > 0);
  }

  /***** Test of metadata field and jsonb queries */
  @Test
  @Order(17)
  @Transactional
  public void insert_storeMetadataWithNestedObject_success() {
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

    var dataPoint = new SpatialGeometry(
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
  @Order(18)
  public void getByBoundingBox_filterByMetadata_returnsOneRecord() {
    var metadataFilter = new HashMap<String, Object>();
    metadataFilter.put("layer", 7);
    var data = repository.getByBoundingBox(
      containerId,
      new Coordinate(0, 0, 0),
      new Coordinate(9999, 9999, 9999),
      null,
      null,
      metadataFilter
    );

    assertNotNull(data);
    assertTrue(data.size() == 1);
  }

  @Test
  @Order(19)
  public void getByKNN_filterByMetadata_returnsOneRecord() {
    var metadataFilter = new HashMap<String, Object>();
    metadataFilter.put("layer", 7);
    var data = repository.getByKNN(containerId, new Coordinate(0, 0, 0), 1, null, null, metadataFilter);

    assertNotNull(data);
    assertTrue(data.size() == 1);
  }

  @Test
  @Order(20)
  public void getByKNN_filterByTimestamp_returnsOneRecord() {
    var data = repository.getByKNN(containerId, new Coordinate(0, 0, 0), 1, 0l, generateTimestamp() * 1_000, null);

    assertNotNull(data);
    assertEquals(1, data.size());
  }

  private Long generateTimestamp() {
    final Long timestamp = movingTimeStamp;
    movingTimeStamp += 10;
    return timestamp;
  }
}
