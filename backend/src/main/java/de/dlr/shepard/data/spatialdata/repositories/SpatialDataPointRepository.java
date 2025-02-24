package de.dlr.shepard.data.spatialdata.repositories;

import de.dlr.shepard.common.util.JsonConverter;
import de.dlr.shepard.data.spatialdata.model.SpatialDataPoint;
import io.micrometer.core.annotation.Timed;
import io.quarkus.hibernate.orm.PersistenceUnit;
import jakarta.enterprise.context.RequestScoped;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.locationtech.jts.geom.Coordinate;

@RequestScoped
public class SpatialDataPointRepository {

  private final int INSERT_BATCH_SIZE = 20000;

  @PersistenceUnit("spatial")
  EntityManager entityManager;

  @Timed(value = "shepard.spatial-data.insert")
  public int insert(long containerId, SpatialDataPoint data) {
    var sql = new NativeInsertStatementBuilder()
      .insert("INSERT INTO spatial_data_points (container_id, time, position, metadata, measurements)")
      .addValues(
        String.format(
          Locale.US,
          "%d, '%s', ST_MakePoint(%f, %f, %f), CAST('%s' AS JSONB), CAST('%s' AS JSONB)",
          containerId,
          data.getTime(),
          data.getPosition().getCoordinate().x,
          data.getPosition().getCoordinate().y,
          data.getPosition().getCoordinate().z,
          JsonConverter.convertToString(data.getMetadata()),
          JsonConverter.convertToString(data.getMeasurements())
        )
      )
      .build();

    var query = entityManager.createNativeQuery(sql);
    var resultCount = query.executeUpdate();
    if (resultCount <= 0) throw new RuntimeException("SpatialData was not stored in database.");
    return resultCount;
  }

  @Timed(value = "shepard.spatial-data.insert-many")
  public int insertMultiple(long containerId, SpatialDataPoint[] data) {
    var allResultCount = 0;
    var sql = new NativeInsertStatementBuilder()
      .insert("INSERT INTO spatial_data_points (container_id, time, position, metadata, measurements)");

    for (int i = 0; i < data.length; i += INSERT_BATCH_SIZE) {
      int currentLimit = Math.min(i + INSERT_BATCH_SIZE, data.length);
      for (int j = i; j < currentLimit; j++) {
        sql.addValues(
          String.format(
            Locale.US,
            "%d, '%s', ST_MakePoint(%f, %f, %f), CAST('%s' AS JSONB), CAST('%s' AS JSONB)",
            containerId,
            data[j].getTime(),
            data[j].getPosition().getCoordinate().x,
            data[j].getPosition().getCoordinate().y,
            data[j].getPosition().getCoordinate().z,
            JsonConverter.convertToString(data[j].getMetadata()),
            JsonConverter.convertToString(data[j].getMeasurements())
          )
        );
      }
      var query = entityManager.createNativeQuery(sql.build());
      allResultCount += query.executeUpdate();
    }
    return allResultCount;
  }

  /**
   *
   * @param containerId to be used for deletion
   * @return the number of rows deleted
   */
  @Timed(value = "shepard.spatial-data.delete-by-container")
  public int deleteByContainerId(long containerId) {
    return entityManager
      .createNativeQuery("DELETE FROM spatial_data_points WHERE container_id=:containerId;")
      .setParameter("containerId", containerId)
      .executeUpdate();
  }

  @Timed(value = "shepard.spatial-data.get-by-container")
  @SuppressWarnings("unchecked")
  public List<SpatialDataPoint> getByContainerId(long containerId) {
    var query = new NativeQueryStringBuilder()
      .select("SELECT * FROM spatial_data_points")
      .addWhereCondition("container_id", containerId)
      .build();

    return entityManager.createNativeQuery(query, SpatialDataPoint.class).getResultList();
  }

  /**
   * Create an (axis-aligned) bounding box query request for spatial data.
   * The request uses the '&&&'' indexed operator, that acts similar to the ST_Intersects function.
   * If the point is part of the bounding box (i.e., on a bounding box corner) the bounding box check returns true
   */
  @Timed(value = "shepard.spatial-data.query-by-bounding-box")
  @SuppressWarnings("unchecked")
  public List<SpatialDataPoint> getByBoundingBox(
    long containerId,
    Coordinate bottomLeft,
    Coordinate topRight,
    Long timestampStart,
    Long timestampEnd,
    Map<String, Object> metadataFilter,
    Optional<Integer> limit
  ) {
    var query = new NativeQueryStringBuilder()
      .select("SELECT * FROM spatial_data_points")
      .addWhereCondition("container_id", containerId)
      .addTimeCondition("time", timestampStart, timestampEnd)
      .addJsonContainsCondition("metadata", metadataFilter)
      .addAABBGeometryCondition()
      .addLimitClause(limit)
      .build();

    return entityManager
      .createNativeQuery(query, SpatialDataPoint.class)
      .setParameter("x1", bottomLeft.x)
      .setParameter("y1", bottomLeft.y)
      .setParameter("z1", bottomLeft.z)
      .setParameter("x2", topRight.x)
      .setParameter("y2", topRight.y)
      .setParameter("z2", topRight.z)
      .getResultList();
  }

  @SuppressWarnings("unchecked")
  @Timed(value = "shepard.spatial-data.query-by-bounding-sphere")
  public List<SpatialDataPoint> getByBoundingSphere(
    long containerId,
    Coordinate coordinate,
    double radius,
    Long timestampStart,
    Long timestampEnd,
    Map<String, Object> metadataFilter,
    Optional<Integer> limit
  ) {
    var query = new NativeQueryStringBuilder()
      .select("SELECT * FROM spatial_data_points")
      .addWhereCondition("container_id", containerId)
      .addTimeCondition("time", timestampStart, timestampEnd)
      .addJsonContainsCondition("metadata", metadataFilter)
      .addBSGeometryCondition()
      .addLimitClause(limit)
      .build();

    return entityManager
      .createNativeQuery(query, SpatialDataPoint.class)
      .setParameter("x1", coordinate.x)
      .setParameter("y1", coordinate.y)
      .setParameter("z1", coordinate.z)
      .setParameter("radius", radius)
      .getResultList();
  }

  /**
   * Runs a k-nearest-neighbor search on the spatial data.
   * @param coordinate - Starting point for the KNN search
   * @param k - number of returned points
   */
  @SuppressWarnings("unchecked")
  @Timed(value = "shepard.spatial-data.query-by-knn")
  public List<SpatialDataPoint> getByKNN(
    long containerId,
    Coordinate coordinate,
    int k,
    Long timestampStart,
    Long timestampEnd,
    Map<String, Object> metadataFilter
  ) {
    var query = new NativeQueryStringBuilder()
      .select("SELECT * FROM spatial_data_points")
      .addWhereCondition("container_id", containerId)
      .addTimeCondition("time", timestampStart, timestampEnd)
      .addJsonContainsCondition("metadata", metadataFilter)
      .addKNNGeometryCondition()
      .build();

    return entityManager
      .createNativeQuery(query, SpatialDataPoint.class)
      .setParameter("k", k)
      .setParameter("x1", coordinate.x)
      .setParameter("y1", coordinate.y)
      .setParameter("z1", coordinate.z)
      .getResultList();
  }
}
