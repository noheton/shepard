package de.dlr.shepard.timeseriesreference.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.dlr.shepard.timeseries.model.Timeseries;
import de.dlr.shepard.util.HasId;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity(label = "Timeseries")
@Data
@NoArgsConstructor
public class ReferencedTimeseriesNodeEntity implements HasId {

  @Id
  @GeneratedValue
  @JsonIgnore
  private Long id;

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
    return String.join("-", measurement, device, location, symbolicName, field);
  }
}
