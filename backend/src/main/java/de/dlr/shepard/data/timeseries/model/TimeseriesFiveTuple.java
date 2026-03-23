package de.dlr.shepard.data.timeseries.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class TimeseriesFiveTuple {

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

  public TimeseriesFiveTuple(TimeseriesEntity timeseriesEntity) {
    this.measurement = timeseriesEntity.getMeasurement();
    this.device = timeseriesEntity.getDevice();
    this.location = timeseriesEntity.getLocation();
    this.symbolicName = timeseriesEntity.getSymbolicName();
    this.field = timeseriesEntity.getField();
  }
}
