package de.dlr.shepard.data.spatialdata.repositories;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.data.spatialdata.modelpgvector.PGVectorSpatialDataPoint;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class PGVectorSpatialDataPointRepositoryTest {

  @Inject
  PGVectorSpatialDataPointRepository pointRepository;

  private long containerId;

  @BeforeEach
  void init() {
    containerId = System.currentTimeMillis();
  }

  @AfterEach
  @Transactional
  void cleanup() {
    pointRepository.deleteByContainerId(containerId);
  }

  @Test
  @Transactional
  public void insert_storePoint_success() {
    Long timestamp = System.currentTimeMillis() * 1000;
    PGVectorSpatialDataPoint point1 = new PGVectorSpatialDataPoint(
      containerId,
      timestamp,
      new float[] { 0, 0, 0 },
      null,
      null
    );
    int result = pointRepository.insert(point1);
    assertTrue(result == 1);
    List<PGVectorSpatialDataPoint> points = pointRepository.findAll(containerId, null, null);
    assertTrue(points.size() == 1);
    assertTrue(points.contains(point1));
  }

  @Test
  @Transactional
  public void insert_storePointWithMetadata_success() {
    Long timestamp = System.currentTimeMillis() * 1000;
    PGVectorSpatialDataPoint point1 = new PGVectorSpatialDataPoint(
      containerId,
      timestamp,
      new float[] { 0, 0, 0 },
      null,
      """
      {"track": 5}
      """
    );
    int result = pointRepository.insert(point1);
    assertTrue(result == 1);
    List<PGVectorSpatialDataPoint> points = pointRepository.findAll(containerId, null, null);
    assertTrue(points.size() == 1);
    assertTrue(points.contains(point1));
  }

  @Test
  @Transactional
  public void insert_storePointWithMeasurements_success() {
    Long timestamp = System.currentTimeMillis() * 1000;
    PGVectorSpatialDataPoint point1 = new PGVectorSpatialDataPoint(
      containerId,
      timestamp,
      new float[] { 0, 0, 0 },
      """
      {"data": [1,2,3]}
      """,
      null
    );
    int result = pointRepository.insert(point1);
    assertTrue(result == 1);
    List<PGVectorSpatialDataPoint> points = pointRepository.findAll(containerId, null, null);
    assertTrue(points.size() == 1);
    assertTrue(points.contains(point1));
  }

  @Test
  @Transactional
  public void insert_storeListOfPoints_success() {
    Long timestamp = System.currentTimeMillis() * 1000;
    PGVectorSpatialDataPoint point1 = new PGVectorSpatialDataPoint(
      containerId,
      timestamp,
      new float[] { 0, 0, 0 },
      null,
      null
    );
    PGVectorSpatialDataPoint point2 = new PGVectorSpatialDataPoint(
      containerId,
      timestamp + 1,
      new float[] { 10, 0, 0 },
      null,
      null
    );
    pointRepository.insert(List.of(point1, point2));
    List<PGVectorSpatialDataPoint> points = pointRepository.findAll(containerId, null, null);
    assertTrue(points.size() == 2);
    assertTrue(points.contains(point1));
    assertTrue(points.contains(point2));
  }

  @Test
  @Transactional
  public void findKNearest_success() {
    Long timestamp = System.currentTimeMillis() * 1000;
    int numberOfPoints = 5;
    int k = 2;
    List<PGVectorSpatialDataPoint> pointsToInsert = new ArrayList<PGVectorSpatialDataPoint>();
    IntStream.range(0, numberOfPoints).forEach(i -> {
      PGVectorSpatialDataPoint point = new PGVectorSpatialDataPoint(
        containerId,
        timestamp,
        new float[] { i, i, i },
        null,
        null
      );
      pointRepository.insert(point);
      pointsToInsert.add(point);
    });
    List<PGVectorSpatialDataPoint> points = pointRepository.findKNearest(0, 0, 0, k, containerId, null, null, null);
    assertTrue(points.size() == k);
    IntStream.range(0, k).forEach(i -> assertTrue(points.contains(pointsToInsert.get(i))));
  }

  @Test
  @Transactional
  public void findKNearest_filterWithMetaData_success() {
    Long timestamp = System.currentTimeMillis() * 1000;
    int numberOfPoints = 5;
    int k = 2;
    IntStream.range(0, numberOfPoints).forEach(i -> {
      PGVectorSpatialDataPoint point = new PGVectorSpatialDataPoint(
        containerId,
        timestamp,
        new float[] { i, i, i },
        null,
        """
        {"track":5, "base": {"A":%s, "B":2}}
        """.formatted(i)
      );
      pointRepository.insert(point);
    });
    List<PGVectorSpatialDataPoint> points = pointRepository.findKNearest(
      0,
      0,
      0,
      k,
      containerId,
      null,
      null,
      Map.of("base", Map.of("A", 1))
    );
    assertTrue(points.size() == 1);
  }

  @Test
  @Transactional
  public void findWithinSphere_FilterWithMetadata_success() {
    Long timestamp = System.currentTimeMillis() * 1000;
    int numberOfPoints = 5;
    List<PGVectorSpatialDataPoint> pointsToInsert = new ArrayList<PGVectorSpatialDataPoint>();
    IntStream.range(0, numberOfPoints).forEach(i -> {
      PGVectorSpatialDataPoint point = new PGVectorSpatialDataPoint(
        containerId,
        timestamp,
        new float[] { i, i, i },
        null,
        """
        {"track":5, "base": {"A":1, "B":%s}}
        """.formatted(i)
      );
      pointRepository.insert(point);
      pointsToInsert.add(point);
    });
    List<PGVectorSpatialDataPoint> points = pointRepository.findWithinSphere(
      100f,
      0,
      0,
      0,
      containerId,
      null,
      null,
      Map.of("base", Map.of("B", 2))
    );
    assertTrue(points.size() == 1);
    assertTrue(points.contains(pointsToInsert.get(2)));
  }

  @Test
  @Transactional
  public void findWithinSphere_returnsData_success() {
    Long timestamp = System.currentTimeMillis() * 1000;
    int numberOfPoints = 5;
    List<PGVectorSpatialDataPoint> pointsToInsert = new ArrayList<PGVectorSpatialDataPoint>();
    IntStream.range(0, numberOfPoints).forEach(i -> {
      PGVectorSpatialDataPoint point = new PGVectorSpatialDataPoint(
        containerId,
        timestamp,
        new float[] { i, i, i },
        null,
        null
      );
      pointRepository.insert(point);
      pointsToInsert.add(point);
    });
    List<PGVectorSpatialDataPoint> points = pointRepository.findWithinSphere(
      1f,
      0f,
      0f,
      0f,
      containerId,
      null,
      null,
      null
    );
    assertTrue(points.size() == 1);
    assertTrue(points.contains(points.get(0)));
    points = pointRepository.findWithinSphere(2.1f, 0, 0, 0, containerId, -1L, timestamp + numberOfPoints, null);
    assertTrue(points.size() == 2);
    assertTrue(points.contains(points.get(0)));
    assertTrue(points.contains(points.get(1)));
  }

  @Test
  @Transactional
  public void findWithinSphere_returnsNone_success() {
    Long timestamp = System.currentTimeMillis() * 1000;
    int numberOfPoints = 5;
    List<PGVectorSpatialDataPoint> pointsToInsert = new ArrayList<PGVectorSpatialDataPoint>();
    IntStream.range(0, numberOfPoints).forEach(i -> {
      PGVectorSpatialDataPoint point = new PGVectorSpatialDataPoint(
        containerId,
        timestamp,
        new float[] { i, i, i },
        null,
        null
      );
      pointRepository.insert(point);
      pointsToInsert.add(point);
    });
    List<PGVectorSpatialDataPoint> points = pointRepository.findWithinSphere(
      1f,
      10f,
      10f,
      10f,
      containerId,
      null,
      null,
      null
    );
    assertTrue(points.isEmpty());
  }

  @Test
  @Transactional
  public void findWithinAxisAlignedBoundingBox_returnsData_success() {
    Long timestamp = System.currentTimeMillis() * 1000;
    int numberOfPoints = 5;
    List<PGVectorSpatialDataPoint> pointsToInsert = new ArrayList<PGVectorSpatialDataPoint>();
    IntStream.range(0, numberOfPoints).forEach(i -> {
      PGVectorSpatialDataPoint point = new PGVectorSpatialDataPoint(
        containerId,
        timestamp,
        new float[] { i, i, i },
        null,
        null
      );
      pointRepository.insert(point);
      pointsToInsert.add(point);
    });
    List<PGVectorSpatialDataPoint> points = pointRepository.findWithinAxisAlignedBoundingBox(
      0,
      0,
      0,
      1,
      1,
      1,
      containerId,
      null,
      null,
      null
    );
    assertTrue(points.size() == 2);
    assertTrue(points.contains(pointsToInsert.get(0)));
    assertTrue(points.contains(pointsToInsert.get(1)));
    points = pointRepository.findWithinAxisAlignedBoundingBox(
      0,
      0,
      0,
      10,
      10,
      10,
      containerId,
      -1L,
      timestamp + numberOfPoints,
      null
    );
    assertTrue(points.size() == numberOfPoints);
  }

  @Test
  @Transactional
  public void findWithinAxisAlignedBoundingBox_returnsNone_success() {
    Long timestamp = System.currentTimeMillis() * 1000;
    int numberOfPoints = 5;
    List<PGVectorSpatialDataPoint> pointsToInsert = new ArrayList<PGVectorSpatialDataPoint>();
    IntStream.range(0, numberOfPoints).forEach(i -> {
      PGVectorSpatialDataPoint point = new PGVectorSpatialDataPoint(
        containerId,
        timestamp,
        new float[] { i, i, i },
        null,
        null
      );
      pointRepository.insert(point);
      pointsToInsert.add(point);
    });
    List<PGVectorSpatialDataPoint> points = pointRepository.findWithinAxisAlignedBoundingBox(
      100,
      100,
      100,
      1000,
      1000,
      1000,
      containerId,
      null,
      null,
      null
    );
    assertTrue(points.isEmpty());
  }

  @Test
  @Transactional
  public void findWithinAxisAlignedBoundingBox_filterWithMetaData_success() {
    Long timestamp = System.currentTimeMillis() * 1000;
    int numberOfPoints = 5;
    List<PGVectorSpatialDataPoint> pointsToInsert = new ArrayList<PGVectorSpatialDataPoint>();
    IntStream.range(0, numberOfPoints).forEach(i -> {
      PGVectorSpatialDataPoint point = new PGVectorSpatialDataPoint(
        containerId,
        timestamp,
        new float[] { i, i, i },
        null,
        """
        {"track":%s, "base": {"A":1, "B":2}}
        """.formatted(i)
      );
      pointRepository.insert(point);
      pointsToInsert.add(point);
    });

    List<PGVectorSpatialDataPoint> points = pointRepository.findWithinAxisAlignedBoundingBox(
      0,
      0,
      0,
      10,
      10,
      10,
      containerId,
      null,
      null,
      Map.of("track", 0)
    );
    assertTrue(points.size() == 1);
    assertTrue(points.contains(pointsToInsert.get(0)));
  }
}
