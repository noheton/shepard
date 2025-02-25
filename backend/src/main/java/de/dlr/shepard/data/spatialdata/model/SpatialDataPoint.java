package de.dlr.shepard.data.spatialdata.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;

@Getter
@Setter
@EqualsAndHashCode(exclude = "id")
@Entity
@Table(name = "spatial_data_points")
public class SpatialDataPoint {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "container_id")
  private Long containerId;

  /** timestamp in nanoseconds */
  private Long time;

  /**
   * This field can hold any geometry that is part of the specification, like Point, Linear, Polygon, etc.
   * Atm it is only used to store 3D points.
   *
   * Hint: Be careful when using functions on the Geometry object because most of them do only work with 2D geometries.
   * For example, the Geometry.toText() method returns 'POINT (x y)' event if the underlying coordinate has a z value.
   */
  private Geometry position;

  /**
   * This field stores metadata that is in a JSON format.
   * The metadata is used to store additional information about the geometry that
   * can also be used for filtering.
   */
  @Column(columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> metadata;

  /**
   * Measurements hold the actual data of that position at this point in time.
   * These values are also stored in a JSON format.
   * It can be used to store just a single value or an array of values.
   * In the future further data types can be added.
   */
  @Column(columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, Object> measurements;

  public SpatialDataPoint() {}

  public SpatialDataPoint(
    Long id,
    Long containerId,
    Long time,
    Geometry position,
    Map<String, Object> metadata,
    Map<String, Object> measurements
  ) {
    this.id = id;
    this.containerId = containerId;
    this.time = time;
    this.position = position;
    this.metadata = metadata;
    this.measurements = measurements;
  }

  public SpatialDataPoint(
    Long containerId,
    Long time,
    Geometry position,
    Map<String, Object> metadata,
    Map<String, Object> measurements
  ) {
    this(null, containerId, time, position, metadata, measurements);
  }
}
