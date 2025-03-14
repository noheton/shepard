package de.dlr.shepard.data.timeseries.io;

import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "TimeseriesEntity")
@Data
public class TimeseriesIO {

  @NotBlank
  private final int id;

  @NotBlank
  private final long containerId;

  @NotBlank
  private final DataPointValueType valueType;

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

  public TimeseriesIO(TimeseriesEntity timeseriesEntity) {
    this.id = timeseriesEntity.getId();
    this.containerId = timeseriesEntity.getContainerId();
    this.valueType = timeseriesEntity.getValueType();
    this.measurement = timeseriesEntity.getMeasurement();
    this.device = timeseriesEntity.getDevice();
    this.location = timeseriesEntity.getLocation();
    this.symbolicName = timeseriesEntity.getSymbolicName();
    this.field = timeseriesEntity.getField();
  }
}
