package de.dlr.shepard.data.timeseries.model;

import de.dlr.shepard.common.util.HasId;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity
@Data
@EqualsAndHashCode
@NoArgsConstructor(force = true)
@RequiredArgsConstructor
public class TimeseriesTuple implements HasId {

  @Id
  @GeneratedValue
  @EqualsAndHashCode.Exclude
  private Long id;

  @NotBlank
  private final String measurement;

  @NotBlank
  private final String device;

  @NotBlank
  private final String location;

  @NotBlank
  private final String symbolicName;

  @NotBlank
  private final String field;

  public TimeseriesTuple(TimeseriesEntity timeseriesEntity) {
    this.measurement = timeseriesEntity.getMeasurement();
    this.device = timeseriesEntity.getDevice();
    this.location = timeseriesEntity.getLocation();
    this.symbolicName = timeseriesEntity.getSymbolicName();
    this.field = timeseriesEntity.getField();
  }

  @Override
  public String getUniqueId() {
    return TimeseriesUniqueIdBuilder.buildUniqueId(measurement, device, location, symbolicName, field);
  }
}
