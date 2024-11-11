package de.dlr.shepard.timeseries.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class ExperimentalTimeseries {

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

  public ExperimentalTimeseries(String measurement, String device, String location, String symbolicName, String field) {
    this.measurement = measurement;
    this.device = device;
    this.location = location;
    this.symbolicName = symbolicName;
    this.field = field;
  }

  public ExperimentalTimeseries(ExperimentalTimeseriesEntity timeseriesEntity) {
    this.measurement = timeseriesEntity.getMeasurement();
    this.device = timeseriesEntity.getDevice();
    this.location = timeseriesEntity.getLocation();
    this.symbolicName = timeseriesEntity.getSymbolicName();
    this.field = timeseriesEntity.getField();
  }
}
