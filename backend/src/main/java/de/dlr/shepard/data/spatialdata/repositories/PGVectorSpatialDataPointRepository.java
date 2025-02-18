package de.dlr.shepard.data.spatialdata.repositories;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.JsonConverter;
import de.dlr.shepard.data.spatialdata.modelpgvector.PGVectorSpatialDataPoint;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.RequestScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Map;

@RequestScoped
public class PGVectorSpatialDataPointRepository implements PanacheRepositoryBase<PGVectorSpatialDataPoint, Long> {

  @PersistenceUnit("spatialpgvector")
  EntityManager entityManager;

  public int insert(PGVectorSpatialDataPoint point) {
    var sql = String.format(
      "INSERT INTO %s (container_id, time, point, metadata, measurements) values (:container_id, :time, :point, CAST(:metadata AS JSONB), CAST(:measurements AS JSONB));",
      Constants.SPATIAL_DATA_DATA_POINTS_TABLE_NAME_PGVECTOR
    );
    var query = entityManager.createNativeQuery(sql);
    query.setParameter("container_id", point.getContainerId());
    query.setParameter("time", point.getTimestamp());
    query.setParameter("point", point.getPoint());
    query.setParameter("metadata", point.getMetadata());
    query.setParameter("measurements", point.getMeasurements());
    var resultCount = query.executeUpdate();
    if (resultCount <= 0) throw new RuntimeException("Vector point was not stored in database.");
    return resultCount;
  }

  public void insert(List<PGVectorSpatialDataPoint> points) {
    points.forEach(point -> insert(point));
  }

  public List<PGVectorSpatialDataPoint> findAll(long containerId, Long startTime, Long endTime) {
    String queryString = String.format(
      "Select id, container_id, time, point, metadata, measurements FROM %s WHERE container_id = :containerId %s",
      Constants.SPATIAL_DATA_DATA_POINTS_TABLE_NAME_PGVECTOR,
      getConditionClause(startTime, endTime, null)
    );
    Query query = entityManager
      .createNativeQuery(queryString, PGVectorSpatialDataPoint.class)
      .setParameter("containerId", containerId);
    return (List<PGVectorSpatialDataPoint>) query.getResultList();
  }

  public List<PGVectorSpatialDataPoint> findKNearest(
    float x,
    float y,
    float z,
    int k,
    long containerId,
    Long startTime,
    Long endTime,
    Map<String, Object> metadata
  ) {
    String queryString = String.format(
      "Select id, container_id, time, point, metadata, measurements FROM %s WHERE container_id = :containerId %s ORDER BY point <-> '[%s,%s,%s]' LIMIT :limit",
      Constants.SPATIAL_DATA_DATA_POINTS_TABLE_NAME_PGVECTOR,
      getConditionClause(startTime, endTime, metadata),
      x,
      y,
      z
    );

    Query query = entityManager
      .createNativeQuery(queryString, PGVectorSpatialDataPoint.class)
      .setParameter("containerId", containerId)
      .setParameter("limit", k);

    return (List<PGVectorSpatialDataPoint>) query.getResultList();
  }

  public List<PGVectorSpatialDataPoint> findWithinSphere(
    float radius,
    float centerX,
    float centerY,
    float centerZ,
    long containerId,
    Long startTime,
    Long endTime,
    Map<String, Object> metadata
  ) {
    String queryString = String.format(
      "Select id, container_id, time, point, metadata, measurements FROM %s WHERE container_id = %s AND l2_distance(point, '[%s,%s,%s]') <= :radius %s",
      Constants.SPATIAL_DATA_DATA_POINTS_TABLE_NAME_PGVECTOR,
      containerId,
      centerX,
      centerY,
      centerZ,
      getConditionClause(startTime, endTime, metadata)
    );

    Query query = entityManager
      .createNativeQuery(queryString, PGVectorSpatialDataPoint.class)
      .setParameter("radius", radius);

    return (List<PGVectorSpatialDataPoint>) query.getResultList();
  }

  public List<PGVectorSpatialDataPoint> findWithinAxisAlignedBoundingBox(
    float minX,
    float minY,
    float minZ,
    float maxX,
    float maxY,
    float maxZ,
    long containerId,
    Long startTime,
    Long endTime,
    Map<String, Object> metadata
  ) {
    String queryString = String.format(
      "select id, container_id, time, point, metadata, measurements FROM %s WHERE " +
      "container_id=%s " +
      "AND (point::real[])[1] BETWEEN %s AND %s " +
      "AND (point::real[])[2] BETWEEN %s AND %s " +
      "AND (point::real[])[3] BETWEEN %s AND %s %s",
      Constants.SPATIAL_DATA_DATA_POINTS_TABLE_NAME_PGVECTOR,
      containerId,
      minX,
      maxX,
      minY,
      maxY,
      minZ,
      maxZ,
      getConditionClause(startTime, endTime, metadata)
    );
    var query = entityManager.createNativeQuery(queryString, PGVectorSpatialDataPoint.class);
    return (List<PGVectorSpatialDataPoint>) query.getResultList();
  }

  private String getConditionClause(Long startTime, Long endTime, Map<String, Object> metadata) {
    String whereClause = "";
    if (startTime != null && endTime != null) whereClause = String.format(
      "AND time BETWEEN %s AND %s ",
      startTime,
      endTime
    );
    if (metadata != null) whereClause = String.format(
      "%s AND metadata @> '%s' ",
      whereClause,
      JsonConverter.convertToString(metadata)
    );
    return whereClause;
  }

  public int deleteByContainerId(long containerId) {
    return entityManager
      .createNativeQuery(
        "DELETE FROM %s WHERE container_id=:containerId;".formatted(
            Constants.SPATIAL_DATA_DATA_POINTS_TABLE_NAME_PGVECTOR
          )
      )
      .setParameter("containerId", containerId)
      .executeUpdate();
  }
}
