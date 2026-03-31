package de.dlr.shepard.data.timeseries.io;

import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "TimeseriesEntity")
@Data
public class TimeseriesIO {

  @NotBlank
  private final long id;

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

  public TimeseriesIO(Timeseries timeseries) {
    this.id = timeseries.getTimeseriesId();
    this.containerId = timeseries.getContainer().getId();
    this.valueType = timeseries.getValueType();
    this.measurement = timeseries.getTimeseriesTuple().getMeasurement();
    this.device = timeseries.getTimeseriesTuple().getDevice();
    this.location = timeseries.getTimeseriesTuple().getLocation();
    this.symbolicName = timeseries.getTimeseriesTuple().getSymbolicName();
    this.field = timeseries.getTimeseriesTuple().getField();
  }
}
