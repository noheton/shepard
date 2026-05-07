package de.dlr.shepard.context.references.timeseriesreference.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.dlr.shepard.common.identifier.HasAppId;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesUniqueIdBuilder;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;

@NodeEntity(label = "Timeseries")
@Data
@NoArgsConstructor
public class ReferencedTimeseriesNodeEntity implements HasId, HasAppId {

  @Id
  @GeneratedValue
  @JsonIgnore
  private Long id;

  /**
   * Application-level identifier (UUID v7) — additive in L2a.
   */
  @Property("appId")
  @JsonIgnore
  private String appId;

  @NotBlank
  private String measurement;

  @NotBlank
  private String device;

  @NotBlank
  private String location;

  @NotBlank
  private String symbolicName;

  @NotBlank
  private String field;

  public ReferencedTimeseriesNodeEntity(
    String measurement,
    String device,
    String location,
    String symbolicName,
    String field
  ) {
    this.measurement = measurement;
    this.device = device;
    this.location = location;
    this.symbolicName = symbolicName;
    this.field = field;
  }

  public ReferencedTimeseriesNodeEntity(Timeseries timeseries) {
    this.measurement = timeseries.getMeasurement();
    this.device = timeseries.getDevice();
    this.location = timeseries.getLocation();
    this.symbolicName = timeseries.getSymbolicName();
    this.field = timeseries.getField();
  }

  public Timeseries toTimeseries() {
    return new Timeseries(
      this.getMeasurement(),
      this.getDevice(),
      this.getLocation(),
      this.getSymbolicName(),
      this.getField()
    );
  }

  @Override
  public String getUniqueId() {
    return TimeseriesUniqueIdBuilder.buildUniqueId(measurement, device, location, symbolicName, field);
  }
}
