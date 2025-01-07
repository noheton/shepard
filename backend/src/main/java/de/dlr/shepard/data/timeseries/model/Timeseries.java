package de.dlr.shepard.data.timeseries.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class Timeseries {

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

  public Timeseries(String measurement, String device, String location, String symbolicName, String field) {
    this.measurement = measurement;
    this.device = device;
    this.location = location;
    this.symbolicName = symbolicName;
    this.field = field;
  }

  public Timeseries(TimeseriesEntity timeseriesEntity) {
    this.measurement = timeseriesEntity.getMeasurement();
    this.device = timeseriesEntity.getDevice();
    this.location = timeseriesEntity.getLocation();
    this.symbolicName = timeseriesEntity.getSymbolicName();
    this.field = timeseriesEntity.getField();
  }
}
