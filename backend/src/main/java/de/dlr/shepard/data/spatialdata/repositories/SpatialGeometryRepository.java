package de.dlr.shepard.data.spatialdata.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.common.util.JsonConverter;
import de.dlr.shepard.data.spatialdata.model.SpatialGeometry;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.RequestScoped;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.locationtech.jts.geom.Coordinate;

@RequestScoped
public class SpatialGeometryRepository implements PanacheRepositoryBase<SpatialGeometry, Long> {

  @PersistenceUnit("spatial")
  EntityManager entityManager;

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

  public int insertMultiple(long containerId, SpatialGeometry[] geometry) {
    int allResultCount = 0;
    for (SpatialGeometry entry : geometry) {
      allResultCount += insert(containerId, entry);
    }

    //TODO: trigger a vacuum - required as:https://postgis.net/workshops/postgis-intro/indexing.html#vacuuming
    return allResultCount;
  }

  public SpatialGeometry getById(long id) {
    return this.findById(id);
  }

  public List<SpatialGeometry> getAll() {
    return this.listAll();
  }

  public List<SpatialGeometry> getAllCustom() {
    var sb = new StringBuilder();
    sb.append(
      "SELECT data.id, data.containerId, data.time, data.geometry, data.metadata, data.measurements FROM SpatialGeometry data"
    );

    var query = entityManager.createQuery(sb.toString(), SpatialGeometry.class);

    return query.getResultList();
  }

  /**
   *
   * @param Id entry Id to be deleted
   * @return the number of rows deleted (expected to be one in this case)
   */
  public int deleteById(int Id) {
    return entityManager
      .createNativeQuery("DELETE FROM spatial_data WHERE id=:id;")
      .setParameter("id", Id)
      .executeUpdate();
  }

  /**
   *
   * @param containerId to be used for deletion
   * @return the number of rows deleted
   */
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
  @SuppressWarnings("unchecked")
  public List<SpatialGeometry> getByBoundingBox(
    long containerId,
    Coordinate bottomLeft,
    Coordinate topRight,
    Long timestampStart,
    Long timestampEnd,
    Map<String, Object> metadataFilter
  ) {
    var whereClause = buildWhereClause(containerId, timestampStart, timestampEnd, metadataFilter);

    return entityManager
      .createNativeQuery(
        String.format(
          "SELECT * FROM spatial_data %s AND geometry &&& ST_3DMakeBox(ST_MakePoint(:x1, :y1, :z1), ST_MakePoint(:x2, :y2, :z2));",
          whereClause
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
  public List<SpatialGeometry> getByBoundingSphere(
    long containerId,
    Coordinate coordinate,
    double radius,
    Long timestampStart,
    Long timestampEnd,
    Map<String, Object> metadataFilter
  ) {
    var whereClause = buildWhereClause(containerId, timestampStart, timestampEnd, metadataFilter);

    return entityManager
      .createNativeQuery(
        String.format(
          "SELECT * FROM spatial_data %s AND ST_3DMaxDistance(ST_MakePoint(:x1, :y1, :z1), geometry) <= :radius;",
          whereClause
        ),
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
  public List<SpatialGeometry> getByKNN(
    long containerId,
    Coordinate coordinate,
    int k,
    Long timestampStart,
    Long timestampEnd,
    Map<String, Object> metadataFilter
  ) {
    var whereClause = buildWhereClause(containerId, timestampStart, timestampEnd, metadataFilter);

    return entityManager
      .createNativeQuery(
        String.format(
          "SELECT * FROM spatial_data %s ORDER BY geometry <<->> ST_MakePoint(:x1, :y1, :z1) LIMIT :k;",
          whereClause
        ),
        SpatialGeometry.class
      )
      .setParameter("k", k)
      .setParameter("x1", coordinate.x)
      .setParameter("y1", coordinate.y)
      .setParameter("z1", coordinate.z)
      .getResultList();
  }

  private String buildWhereClause(
    Long containerId,
    Long timestampStart,
    Long timestampEnd,
    Map<String, Object> metadataFilter
  ) {
    if (containerId == null && timestampStart == null && timestampEnd == null && metadataFilter.size() == 0) return "";

    var sb = new StringBuilder();
    sb.append(String.format("WHERE (container_id = %s", containerId));

    if (timestampStart != null && timestampEnd != null) {
      sb.append(String.format(" AND time > %s AND time < %s", timestampStart, timestampEnd));
    }

    if (metadataFilter != null && metadataFilter.size() > 0) {
      try {
        var mapper = new ObjectMapper();
        var filterAsString = mapper.writeValueAsString(metadataFilter);
        sb.append(String.format(" AND metadata @> '%s'", filterAsString));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    sb.append(")");
    return sb.toString();
  }
}
