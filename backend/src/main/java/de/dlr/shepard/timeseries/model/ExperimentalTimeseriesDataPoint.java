package de.dlr.shepard.timeseries.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.dlr.shepard.exceptions.InvalidRequestException;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
public class ExperimentalTimeseriesDataPoint {

  @JsonProperty("timestamp")
  @Schema(description = "Time in nanoseconds since unix epoch.")
  private long timestamp;

  @Schema(description = "A string, an int, a double or a boolean")
  private Object value;

  public ExperimentalTimeseriesDataPoint(long timestamp, Object value) {
    this.timestamp = timestamp;
    this.value = value;
  }

  public ExperimentalTimeseriesDataPointEntity toEntity(ExperimentalTimeseriesEntity timeseriesEntity) {
    switch (timeseriesEntity.getValueType()) {
      case Double:
        return new ExperimentalTimeseriesDataPointEntity(
          timeseriesEntity.getId(),
          this.getTimestamp(),
          Double.parseDouble(this.getValue().toString())
        );
      case Boolean:
        return new ExperimentalTimeseriesDataPointEntity(
          timeseriesEntity.getId(),
          this.getTimestamp(),
          Boolean.parseBoolean(this.getValue().toString())
        );
      case Integer:
        return new ExperimentalTimeseriesDataPointEntity(
          timeseriesEntity.getId(),
          this.getTimestamp(),
          Integer.parseInt(this.getValue().toString())
        );
      case String:
        return new ExperimentalTimeseriesDataPointEntity(
          timeseriesEntity.getId(),
          this.getTimestamp(),
          this.getValue().toString()
        );
      default: //this should not be possible.
        throw new InvalidRequestException("DataPoint has an unsupported data type.");
    }
  }
}
