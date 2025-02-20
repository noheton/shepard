package de.dlr.shepard.data.spatialdata.repositories;

import de.dlr.shepard.common.util.JsonConverter;
import de.dlr.shepard.data.spatialdata.model.SpatialGeometry;
import io.micrometer.core.annotation.Timed;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.RequestScoped;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Coordinate;

@RequestScoped
public class SpatialGeometryRepository implements PanacheRepositoryBase<SpatialGeometry, Long> {

  private final int INSERT_BATCH_SIZE = 20000;

  @PersistenceUnit("spatial")
  EntityManager entityManager;

  @Timed(value = "shepard.spatial-data.insert")
  public int insert(long containerId, SpatialGeometry geometry) {
    var sql =
      "INSERT INTO spatial_data (container_id, time, geometry, metadata, measurements) values (:container_id, :time, :geometry, CAST(:metadata AS JSONB), CAST(:measurements AS JSONB));";
    var query = entityManager.createNativeQuery(sql);
    query.setParameter("container_id", containerId);
    query.setParameter("time", geometry.getTime());
    query.setParameter("geometry", geometry.getGeometry());
    query.setParameter("metadata", JsonConverter.convertToString(geometry.getMetadata()));
    query.setParameter("measurements", JsonConverter.convertToString(geometry.getMeasurements()));
    var resultCount = query.executeUpdate();
    if (resultCount <= 0) throw new RuntimeException("SpatialGeometry was not stored in database.");
    return resultCount;
  }

  @Timed(value = "shepard.spatial-data.insert-many")
  public int insertMultiple(long containerId, SpatialGeometry[] geometry) {
    var allResultCount = 0;
    var sql = new NativeInsertStatementBuilder()
      .insert("INSERT INTO spatial_data (container_id, time, geometry, metadata, measurements)");

    for (int i = 0; i < geometry.length; i += INSERT_BATCH_SIZE) {
      int currentLimit = Math.min(i + INSERT_BATCH_SIZE, geometry.length);
      for (int j = i; j < currentLimit; j++) {
        sql.addValues(
          String.format(
            "%d, '%s', ST_GeomFromText('%s'), CAST('%s' AS JSONB), CAST('%s' AS JSONB)",
            containerId,
            geometry[j].getTime(),
            geometry[j].getGeometry().toText(),
            JsonConverter.convertToString(geometry[j].getMetadata()),
            JsonConverter.convertToString(geometry[j].getMeasurements())
          )
        );
      }
      var query = entityManager.createNativeQuery(sql.build());
      allResultCount += query.executeUpdate();
    }

    //TODO: trigger a vacuum - required as:https://postgis.net/workshops/postgis-intro/indexing.html#vacuuming
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
      .createNativeQuery("DELETE FROM spatial_data WHERE container_id=:containerId;")
      .setParameter("containerId", containerId)
      .executeUpdate();
  }

  /**
   * Create an (axis-aligned) bounding box query request for spatial data.
   * The request uses the '&&&'' indexed operator, that acts similar to the ST_Intersects function.
   * If the point is part of the bounding box (i.e., on a bounding box corner) the bounding box check returns true
   */
  @Timed(value = "shepard.spatial-data.query-by-bounding-box")
  @SuppressWarnings("unchecked")
  public List<SpatialGeometry> getByBoundingBox(
    long containerId,
    Coordinate bottomLeft,
    Coordinate topRight,
    Long timestampStart,
    Long timestampEnd,
    Map<String, Object> metadataFilter
  ) {
    var query = new NativeQueryStringBuilder()
      .select("select * from spatial_data")
      .addCondition("container_id", containerId)
      .addTimeCondition("time", timestampStart, timestampEnd)
      .addJsonCondition("metadata", metadataFilter)
      .build();

    return entityManager
      .createNativeQuery(
        String.format(
          "%s AND geometry &&& ST_3DMakeBox(ST_MakePoint(:x1, :y1, :z1), ST_MakePoint(:x2, :y2, :z2));",
          query
        ),
        SpatialGeometry.class
      )
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
  public List<SpatialGeometry> getByBoundingSphere(
    long containerId,
    Coordinate coordinate,
    double radius,
    Long timestampStart,
    Long timestampEnd,
    Map<String, Object> metadataFilter
  ) {
    var query = new NativeQueryStringBuilder()
      .select("select * from spatial_data")
      .addCondition("container_id", containerId)
      .addTimeCondition("time", timestampStart, timestampEnd)
      .addJsonCondition("metadata", metadataFilter)
      .build();

    return entityManager
      .createNativeQuery(
        String.format("%s AND ST_3DDWithin(geometry, ST_MakePoint(:x1, :y1, :z1), :radius);", query),
        SpatialGeometry.class
      )
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
  public List<SpatialGeometry> getByKNN(
    long containerId,
    Coordinate coordinate,
    int k,
    Long timestampStart,
    Long timestampEnd,
    Map<String, Object> metadataFilter
  ) {
    var query = new NativeQueryStringBuilder()
      .select("select * from spatial_data")
      .addCondition("container_id", containerId)
      .addTimeCondition("time", timestampStart, timestampEnd)
      .addJsonCondition("metadata", metadataFilter)
      .build();

    return entityManager
      .createNativeQuery(
        String.format("%s ORDER BY geometry <<->> ST_MakePoint(:x1, :y1, :z1) LIMIT :k;", query),
        SpatialGeometry.class
      )
      .setParameter("k", k)
      .setParameter("x1", coordinate.x)
      .setParameter("y1", coordinate.y)
      .setParameter("z1", coordinate.z)
      .getResultList();
  }
}
