package de.dlr.shepard.data.spatialdata.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;

@Getter
@Setter
@Entity
@Table(name = "spatial_data")
public class SpatialGeometry {

  @GeneratedValue
  @Id
  private Long id;

  @Column(name = "container_id")
  private Long containerId;

  /** timestamp in nanoseconds */
  private Long time;

  // Todo: Do we store only points or a geometry or MultiPoints?
  // The internal representation in PostGIS is always Geometry stored as WKB (well-known binary).
  // So we are free to store Points, Linear, Polygon, etc.
  // @Column(columnDefinition = "geometry")
  // @Column(columnDefinition = "BINARY(2048)")
  private Geometry geometry;

  // Todo: Some of the metadata can be deduplicated with a relationship to
  // another table like the timeseries table, e.g. track, plane, etc.
  // That would save space and queries are more performant.
  @Column(columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> metadata;

  // Todo: Measurements seems to be data that should be stored in other databases maybe (timeseries, documentdb).
  @Column(columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> measurements;

  public SpatialGeometry() {}

  public SpatialGeometry(
    Long id,
    Long containerId,
    Long time,
    Geometry geometry,
    Map<String, Object> metadata,
    Map<String, Object> measurements
  ) {
    this.id = id;
    this.containerId = containerId;
    this.time = time;
    this.geometry = geometry;
    this.metadata = metadata;
    this.measurements = measurements;
  }

  public SpatialGeometry(
    Long containerId,
    Long time,
    Geometry geometry,
    Map<String, Object> metadata,
    Map<String, Object> measurements
  ) {
    this(null, containerId, time, geometry, metadata, measurements);
  }

  public String getMetadataAsString() {
    var mapper = new ObjectMapper();
    try {
      return mapper.writeValueAsString(metadata);
    } catch (Exception e) {
      // Todo: how to handle that exception correctly?
      Log.errorf("Error while converting metadata to JSON string. %s", e);
      throw new RuntimeException(e);
    }
  }
  // public void setMetadata(String metadataAsString) {
  //   var mapper = new ObjectMapper();
  //   try {
  //     this.metadata = mapper.readValue(metadataAsString, Object.class);
  //   } catch (Exception e) {
  //     Log.errorf("Error while converting JSON string to metadata object. %s", e);
  //     throw new RuntimeException(e);
  //   }
  // }

}
