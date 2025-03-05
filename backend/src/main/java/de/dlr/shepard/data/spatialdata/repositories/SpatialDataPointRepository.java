package de.dlr.shepard.data.spatialdata.repositories;

import de.dlr.shepard.common.util.JsonConverter;
import de.dlr.shepard.data.spatialdata.io.FilterCondition;
import de.dlr.shepard.data.spatialdata.model.SpatialDataPoint;
import io.micrometer.core.annotation.Timed;
import io.quarkus.hibernate.orm.PersistenceUnit;
import jakarta.enterprise.context.RequestScoped;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.locationtech.jts.geom.Coordinate;

@RequestScoped
public class SpatialDataPointRepository {

  private final int INSERT_BATCH_SIZE = 20000;
  public static final String SPATIAL_TABLE_NAME = "spatial_data_points";
  public static final String SPATIAL_COLUMN_CONTAINER_ID = "container_id";
  public static final String SPATIAL_COLUMN_TIME = "time";
  public static final String SPATIAL_COLUMN_POSITION = "position";
  public static final String SPATIAL_COLUMN_METADATA = "metadata";
  public static final String SPATIAL_COLUMN_MEASUREMENTS = "measurements";

  private static final String[] ALL_COLUMNS_STRING = new String[] { "*" };

  @PersistenceUnit("spatial")
  EntityManager entityManager;

  @Timed(value = "shepard.spatial-data.insert")
  public int insert(long containerId, SpatialDataPoint data) {
    var sql = new NativeInsertStatementBuilder()
      .insert(
        SPATIAL_TABLE_NAME,
        new String[] {
          SPATIAL_COLUMN_CONTAINER_ID,
          SPATIAL_COLUMN_TIME,
          SPATIAL_COLUMN_POSITION,
          SPATIAL_COLUMN_METADATA,
          SPATIAL_COLUMN_MEASUREMENTS,
        }
      )
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
      .insert(
        SPATIAL_TABLE_NAME,
        new String[] {
          SPATIAL_COLUMN_CONTAINER_ID,
          SPATIAL_COLUMN_TIME,
          SPATIAL_COLUMN_POSITION,
          SPATIAL_COLUMN_METADATA,
          SPATIAL_COLUMN_MEASUREMENTS,
        }
      );

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
      .createNativeQuery(
        "DELETE FROM %s WHERE %s=:containerId;".formatted(SPATIAL_TABLE_NAME, SPATIAL_COLUMN_CONTAINER_ID)
      )
      .setParameter("containerId", containerId)
      .executeUpdate();
  }

  @Timed(value = "shepard.spatial-data.get-by-container")
  @SuppressWarnings("unchecked")
  public List<SpatialDataPoint> getByContainerId(long containerId) {
    var query = new NativeQueryStringBuilder()
      .select(SPATIAL_TABLE_NAME, ALL_COLUMNS_STRING)
      .addWhereCondition(SPATIAL_COLUMN_CONTAINER_ID, containerId)
      .build();

    return entityManager.createNativeQuery(query, SpatialDataPoint.class).getResultList();
  }

  /**
   * Create an (axis-aligned) bounding box query request for spatial data.
   * The request uses the '&&&'' indexed operator, that acts similar to the
   * ST_Intersects function.
   * If the point is part of the bounding box (i.e., on a bounding box corner) the
   * bounding box check returns true
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
    List<FilterCondition> measurementsFilter,
    Integer limit,
    Integer skip
  ) {
    var queryBuilder = new NativeQueryStringBuilder()
      .select(SPATIAL_TABLE_NAME, ALL_COLUMNS_STRING)
      .addWhereCondition(SPATIAL_COLUMN_CONTAINER_ID, containerId)
      .addTimeCondition(SPATIAL_COLUMN_TIME, timestampStart, timestampEnd)
      .addJsonContainsCondition(SPATIAL_COLUMN_METADATA, metadataFilter)
      .addJsonFilterConditions(SPATIAL_COLUMN_MEASUREMENTS, measurementsFilter)
      .addAABBGeometryCondition(bottomLeft.x, bottomLeft.y, bottomLeft.z, topRight.x, topRight.y, topRight.z)
      .addSkipClause(skip)
      .addLimitClause(limit);

    var query = entityManager.createNativeQuery(queryBuilder.build(), SpatialDataPoint.class);
    queryBuilder.getGeometryFilterParameters().forEach(query::setParameter);
    return query.getResultList();
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
    List<FilterCondition> measurementsFilter,
    Integer limit,
    Integer skip
  ) {
    var queryBuilder = new NativeQueryStringBuilder()
      .select(SPATIAL_TABLE_NAME, ALL_COLUMNS_STRING)
      .addWhereCondition(SPATIAL_COLUMN_CONTAINER_ID, containerId)
      .addTimeCondition(SPATIAL_COLUMN_TIME, timestampStart, timestampEnd)
      .addJsonContainsCondition(SPATIAL_COLUMN_METADATA, metadataFilter)
      .addJsonFilterConditions(SPATIAL_COLUMN_MEASUREMENTS, measurementsFilter)
      .addBSGeometryCondition(coordinate.x, coordinate.y, coordinate.z, radius)
      .addSkipClause(skip)
      .addLimitClause(limit);

    var query = entityManager.createNativeQuery(queryBuilder.build(), SpatialDataPoint.class);
    queryBuilder.getGeometryFilterParameters().forEach(query::setParameter);
    return query.getResultList();
  }

  /**
   * Runs a k-nearest-neighbor search on the spatial data.
   *
   * @param coordinate - Starting point for the KNN search
   * @param k          - number of returned points
   */
  @SuppressWarnings("unchecked")
  @Timed(value = "shepard.spatial-data.query-by-knn")
  public List<SpatialDataPoint> getByKNN(
    long containerId,
    Coordinate coordinate,
    int k,
    Long timestampStart,
    Long timestampEnd,
    Map<String, Object> metadataFilter,
    List<FilterCondition> measurementsFilter
  ) {
    var queryBuilder = new NativeQueryStringBuilder()
      .select(SPATIAL_TABLE_NAME, ALL_COLUMNS_STRING)
      .addWhereCondition(SPATIAL_COLUMN_CONTAINER_ID, containerId)
      .addTimeCondition(SPATIAL_COLUMN_TIME, timestampStart, timestampEnd)
      .addJsonContainsCondition(SPATIAL_COLUMN_METADATA, metadataFilter)
      .addJsonFilterConditions(SPATIAL_COLUMN_MEASUREMENTS, measurementsFilter)
      .addKNNGeometryCondition(coordinate.x, coordinate.y, coordinate.z, k);

    var query = entityManager.createNativeQuery(queryBuilder.build(), SpatialDataPoint.class);
    queryBuilder.getGeometryFilterParameters().forEach(query::setParameter);
    return query.getResultList();
  }
}
