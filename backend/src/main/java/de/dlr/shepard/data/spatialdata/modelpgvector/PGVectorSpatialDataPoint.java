package de.dlr.shepard.data.spatialdata.modelpgvector;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.JsonConverter;
import io.quarkus.agroal.DataSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@DataSource("spatialpgvector")
@Table(name = Constants.SPATIAL_DATA_DATA_POINTS_TABLE_NAME_PGVECTOR)
public class PGVectorSpatialDataPoint {

  @Id
  @GeneratedValue
  private Long id;

  @Column(name = "container_id")
  private long containerId;

  @Column(name = "time")
  private Long timestamp;

  @Column
  @JdbcTypeCode(SqlTypes.VECTOR)
  @Array(length = 3) // dimensions
  private float[] point;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String metadata;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String measurements;

  public PGVectorSpatialDataPoint(long containerId, Long time, float[] point, String measurements, String metadata) {
    this.containerId = containerId;
    this.timestamp = time;
    this.point = point;
    this.metadata = metadata;
    this.measurements = measurements;
  }

  public PGVectorSpatialDataPoint() {}

  /**
   * This was created to perform Tests, to check the returned json strings.
   * It might be intensive if was called on the full list
   * TODO: Should it be kept or moved to tests?
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    PGVectorSpatialDataPoint other = (PGVectorSpatialDataPoint) obj;
    if (containerId != other.containerId) return false;
    if (timestamp == null) {
      if (other.timestamp != null) return false;
    } else if (!timestamp.equals(other.timestamp)) return false;
    if (!Arrays.equals(point, other.point)) return false;
    if (metadata == null) {
      if (other.metadata != null) return false;
    } else if (
      !JsonConverter.convertToObject(metadata).equals(JsonConverter.convertToObject(other.metadata))
    ) return false;
    if (measurements == null) {
      if (other.measurements != null) return false;
    } else if (
      !JsonConverter.convertToObject(measurements).equals(JsonConverter.convertToObject(other.measurements))
    ) return false;
    return true;
  }
}
